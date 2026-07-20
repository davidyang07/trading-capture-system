"use client";

import { useCallback, useRef, useState } from "react";
import { ApiError, isApiError } from "@/lib/api-error";

/**
 * Typed mutation hook with built-in duplicate-submission protection: while a call is
 * pending, further `mutate` invocations are ignored (§12). Idempotency remains the
 * backend's authority; this only guards the UI.
 */
export interface MutationState<TArgs extends unknown[], TResult> {
  mutate: (...args: TArgs) => Promise<TResult | undefined>;
  pending: boolean;
  error: ApiError | undefined;
  reset: () => void;
}

export function useMutation<TArgs extends unknown[], TResult>(
  fn: (...args: TArgs) => Promise<TResult>
): MutationState<TArgs, TResult> {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | undefined>(undefined);
  const inFlight = useRef(false);

  const mutate = useCallback(
    async (...args: TArgs): Promise<TResult | undefined> => {
      if (inFlight.current) {
        return undefined;
      }
      inFlight.current = true;
      setPending(true);
      setError(undefined);
      try {
        return await fn(...args);
      } catch (err: unknown) {
        const apiError = isApiError(err) ? err : ApiError.network();
        setError(apiError);
        throw apiError;
      } finally {
        inFlight.current = false;
        setPending(false);
      }
    },
    [fn]
  );

  const reset = useCallback(() => setError(undefined), []);

  return { mutate, pending, error, reset };
}
