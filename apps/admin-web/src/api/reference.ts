import { apiFetch, type RequestOptions } from "@/lib/http";
import type {
  Currency,
  Exchange,
  InstrumentSearchParams,
  InstrumentSummary,
  Page,
} from "@/types/api";

const SERVICE = "enrichment" as const;

export function searchInstruments(
  params: InstrumentSearchParams,
  signal?: AbortSignal
): Promise<Page<InstrumentSummary>> {
  return apiFetch<Page<InstrumentSummary>>(SERVICE, "api/v1/instruments", {
    query: params as RequestOptions["query"],
    signal,
  });
}

export function listCurrencies(signal?: AbortSignal): Promise<Currency[]> {
  return apiFetch<Currency[]>(SERVICE, "api/v1/currencies", { signal });
}

export function listExchanges(signal?: AbortSignal): Promise<Exchange[]> {
  return apiFetch<Exchange[]>(SERVICE, "api/v1/exchanges", { signal });
}
