import { ApiError } from "@/lib/api-error";

describe("ApiError.fromResponse", () => {
  it("maps status codes to kinds", () => {
    expect(ApiError.fromResponse(400, null, {}).kind).toBe("validation");
    expect(ApiError.fromResponse(401, null, {}).kind).toBe("unauthenticated");
    expect(ApiError.fromResponse(403, null, {}).kind).toBe("unauthorized");
    expect(ApiError.fromResponse(404, null, {}).kind).toBe("not-found");
    expect(ApiError.fromResponse(409, null, {}).kind).toBe("conflict");
    expect(ApiError.fromResponse(500, null, {}).kind).toBe("server");
  });

  it("prefers the problem's correlationId, else the header", () => {
    const withBody = ApiError.fromResponse(409, "hdr-1", {
      correlationId: "body-1",
    });
    expect(withBody.correlationId).toBe("body-1");
    const withHeader = ApiError.fromResponse(500, "hdr-2", {});
    expect(withHeader.correlationId).toBe("hdr-2");
  });

  it("surfaces a friendly conflict message for known error codes", () => {
    const err = ApiError.fromResponse(409, null, {
      errorCode: "ORDER_ALREADY_FILLED",
    });
    expect(err.userMessage).toBe("This order is already fully filled.");
  });

  it("never leaks server detail through userMessage for 500", () => {
    const err = ApiError.fromResponse(500, null, {
      detail: "NullPointerException at com.internal.Foo",
    });
    // message keeps the raw detail internally, but userMessage is safe.
    expect(err.userMessage).toBe(
      "Something went wrong on our side. Please try again."
    );
  });

  it("carries field errors for validation problems", () => {
    const err = ApiError.fromResponse(400, null, {
      fieldErrors: [{ field: "quantity", message: "must be > 0" }],
    });
    expect(err.fieldErrors).toHaveLength(1);
    expect(err.fieldErrors[0].field).toBe("quantity");
  });
});

describe("ApiError.network", () => {
  it("is a safe network error", () => {
    const err = ApiError.network();
    expect(err.kind).toBe("network");
    expect(err.userMessage).toMatch(/Unable to reach the server/);
  });

  it("distinguishes timeout and malformed protocol failures", () => {
    expect(ApiError.timeout()).toMatchObject({ kind: "timeout", status: 0 });
    expect(ApiError.malformed("protocol-ref")).toMatchObject({
      kind: "malformed",
      correlationId: "protocol-ref",
    });
  });
});
