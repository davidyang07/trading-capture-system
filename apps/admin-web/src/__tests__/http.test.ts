import { apiFetch } from "@/lib/http";
import { ApiError } from "@/lib/api-error";

// A minimal Response stand-in — jsdom does not expose the fetch Response global.
function jsonResponse(status: number, body: unknown, correlationId?: string) {
  const headers = new Map<string, string>([
    ["content-type", "application/json"],
  ]);
  if (correlationId) {
    headers.set("x-correlation-id", correlationId);
  }
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => headers.get(k.toLowerCase()) ?? null },
    text: async () => (body === null ? "" : JSON.stringify(body)),
  } as unknown as Response;
}

function emptyResponse(status: number) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    text: async () => "",
  } as unknown as Response;
}

function malformedJsonResponse(status: number, body: string) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (key: string) =>
        key.toLowerCase() === "content-type" ? "application/json" : null,
    },
    text: async () => body,
  } as unknown as Response;
}

describe("apiFetch", () => {
  const fetchMock = jest.fn();

  beforeEach(() => {
    global.fetch = fetchMock as unknown as typeof fetch;
    fetchMock.mockReset();
  });

  it("targets the same-origin BFF proxy with the service prefix", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { content: [] }));
    await apiFetch("trading", "api/v1/orders", { query: { page: 0, size: 10 } });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/proxy/trading/api/v1/orders?page=0&size=10",
      expect.objectContaining({ method: "GET", cache: "no-store" })
    );
  });

  it("omits undefined and empty query params", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, {}));
    await apiFetch("account", "api/v1/accounts", {
      query: { status: undefined, page: 0, currency: "" },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/proxy/account/api/v1/accounts?page=0",
      expect.anything()
    );
  });

  it("returns undefined for 204 responses", async () => {
    fetchMock.mockResolvedValue(emptyResponse(204));
    const result = await apiFetch("trading", "api/v1/orders/x", {
      method: "DELETE",
    });
    expect(result).toBeUndefined();
  });

  it("maps a 401 to an unauthenticated ApiError", async () => {
    fetchMock.mockResolvedValue(jsonResponse(401, { detail: "no token" }));
    await expect(apiFetch("trading", "api/v1/orders")).rejects.toMatchObject({
      kind: "unauthenticated",
      status: 401,
    });
  });

  it("maps a 409 problem, keeping errorCode and correlationId", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        409,
        { errorCode: "DUPLICATE_ORDER_ID", correlationId: "corr-9" },
        "hdr"
      )
    );
    try {
      await apiFetch("trading", "api/v1/orders", { method: "POST", body: {} });
      throw new Error("expected rejection");
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.kind).toBe("conflict");
      expect(apiErr.errorCode).toBe("DUPLICATE_ORDER_ID");
      expect(apiErr.correlationId).toBe("corr-9");
    }
  });

  it("does not treat a network failure as empty success", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
    await expect(apiFetch("trading", "api/v1/orders")).rejects.toMatchObject({
      kind: "network",
    });
  });

  it("maps its deterministic request deadline to a timeout error", async () => {
    jest.useFakeTimers();
    fetchMock.mockImplementation(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener("abort", () =>
            reject(new DOMException("deadline", "AbortError"))
          );
        })
    );

    try {
      const request = apiFetch("trading", "api/v1/orders", { timeoutMs: 25 });
      const rejection = expect(request).rejects.toMatchObject({ kind: "timeout" });
      await jest.advanceTimersByTimeAsync(25);
      await rejection;
    } finally {
      jest.useRealTimers();
    }
  });

  it("rejects a malformed JSON success response instead of returning typed data", async () => {
    fetchMock.mockResolvedValue(malformedJsonResponse(200, "not-json"));

    await expect(apiFetch("trading", "api/v1/orders")).rejects.toMatchObject({
      kind: "malformed",
      status: 200,
    });
  });
});
