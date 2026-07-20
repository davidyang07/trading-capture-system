"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, isApiError } from "@/lib/api-error";

/**
 * Minimal typed server-state hook (§11 — smallest justified approach). Handles loading,
 * error, cancellation on unmount/dependency-change, and manual refetch. Reads are safe to
 * retry; this hook never auto-retries mutations.
 */
export interface QueryState<T> {
  data: T | undefined;
  error: ApiError | undefined;
  loading: boolean;
  refetch: () => void;
}

export function useQuery<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  deps: ReadonlyArray<unknown>
): QueryState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<ApiError | undefined>(undefined);
  const [loading, setLoading] = useState<boolean>(true);
  const [nonce, setNonce] = useState(0);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  // Current callers use primitive route/filter/page dependencies. A stable key lets
  // the hook avoid depending on a newly-created inline fetcher every render.
  const dependencyKey = JSON.stringify(deps);

  const refetch = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError(undefined);

    fetcherRef.current(controller.signal)
      .then((result) => {
        if (active) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!active) {
          return;
        }
        if (err instanceof DOMException && err.name === "AbortError") {
          return;
        }
        setError(isApiError(err) ? err : ApiError.network());
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [dependencyKey, nonce]);

  return { data, error, loading, refetch };
}
