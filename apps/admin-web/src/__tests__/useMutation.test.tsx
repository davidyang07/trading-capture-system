import { act, renderHook, waitFor } from "@testing-library/react";
import { useMutation } from "@/hooks/useMutation";
import { ApiError } from "@/lib/api-error";

describe("useMutation", () => {
  it("prevents duplicate submissions while a call is in flight", async () => {
    let resolve!: (value: string) => void;
    const fn = jest.fn(
      () =>
        new Promise<string>((r) => {
          resolve = r;
        })
    );
    const { result } = renderHook(() => useMutation(fn));

    let first: Promise<string | undefined>;
    let second: Promise<string | undefined>;
    act(() => {
      first = result.current.mutate();
      // Second call while the first is pending must be ignored.
      second = result.current.mutate();
    });

    expect(fn).toHaveBeenCalledTimes(1);
    expect(result.current.pending).toBe(true);

    await act(async () => {
      resolve("ok");
      await first;
      await second!;
    });
    expect(result.current.pending).toBe(false);
  });

  it("captures a typed error on failure", async () => {
    const fn = jest.fn(() =>
      Promise.reject(
        new ApiError({ kind: "conflict", status: 409, message: "nope" })
      )
    );
    const { result } = renderHook(() => useMutation(fn));

    await act(async () => {
      await expect(result.current.mutate()).rejects.toBeInstanceOf(ApiError);
    });
    await waitFor(() => expect(result.current.error?.kind).toBe("conflict"));
  });
});
