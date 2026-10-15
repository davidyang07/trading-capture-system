import { renderHook } from "@testing-library/react";
import { useIdempotencyKey } from "@/hooks/useIdempotencyKey";

describe("useIdempotencyKey", () => {
  const posting = { direction: "CREDIT", amount: "100.00", currency: "USD" };

  it("reuses the key when the same posting is submitted again", () => {
    const { result } = renderHook(() => useIdempotencyKey());

    const first = result.current.keyFor(posting);
    const retry = result.current.keyFor({ ...posting });

    expect(first).toMatch(/^web-[0-9a-f-]{32,36}$/);
    expect(retry).toBe(first);
  });

  it("issues a new key for a changed posting", () => {
    const { result } = renderHook(() => useIdempotencyKey());

    const first = result.current.keyFor(posting);
    const changed = result.current.keyFor({ ...posting, amount: "200.00" });

    expect(changed).not.toBe(first);
  });

  it("issues a new key after reset, even for the same posting", () => {
    const { result } = renderHook(() => useIdempotencyKey());

    const first = result.current.keyFor(posting);
    result.current.reset();

    expect(result.current.keyFor(posting)).not.toBe(first);
  });
});
