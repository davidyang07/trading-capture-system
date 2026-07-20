import { act, renderHook, waitFor } from "@testing-library/react";
import { useQuery } from "@/hooks/useQuery";
import { ApiError } from "@/lib/api-error";

describe("useQuery", () => {
  it("moves from loading to typed data", async () => {
    const fetcher = jest.fn().mockResolvedValue({ content: ["order-1"] });
    const { result } = renderHook(() => useQuery(fetcher, []));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({ content: ["order-1"] });
    expect(result.current.error).toBeUndefined();
  });

  it("preserves a typed API failure for the error state", async () => {
    const failure = new ApiError({
      kind: "unauthorized",
      status: 403,
      message: "forbidden",
      correlationId: "query-ref",
    });
    const { result } = renderHook(() =>
      useQuery(() => Promise.reject(failure), [])
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe(failure);
    expect(result.current.data).toBeUndefined();
  });

  it("aborts an in-flight request when the consumer unmounts", () => {
    let observedSignal: AbortSignal | undefined;
    const fetcher = jest.fn((signal: AbortSignal) => {
      observedSignal = signal;
      return new Promise<never>(() => {});
    });
    const { unmount } = renderHook(() => useQuery(fetcher, []));

    expect(observedSignal?.aborted).toBe(false);
    unmount();
    expect(observedSignal?.aborted).toBe(true);
  });

  it("refetches deterministically on demand", async () => {
    const fetcher = jest
      .fn<Promise<string>, [AbortSignal]>()
      .mockResolvedValueOnce("first")
      .mockResolvedValueOnce("second");
    const { result } = renderHook(() => useQuery(fetcher, []));

    await waitFor(() => expect(result.current.data).toBe("first"));
    act(() => result.current.refetch());
    await waitFor(() => expect(result.current.data).toBe("second"));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
