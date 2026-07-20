import { apiFetch, type RequestOptions } from "@/lib/http";
import type {
  CancelExecutionRequest,
  CorrectExecutionRequest,
  CreateExecutionRequest,
  Execution,
  ExecutionDetail,
  ExecutionSearchParams,
  Page,
} from "@/types/api";

const SERVICE = "trading" as const;

export function searchExecutions(
  params: ExecutionSearchParams,
  signal?: AbortSignal
): Promise<Page<Execution>> {
  return apiFetch<Page<Execution>>(SERVICE, "api/v1/executions", {
    query: params as RequestOptions["query"],
    signal,
  });
}

export function getExecution(
  internalId: string,
  signal?: AbortSignal
): Promise<ExecutionDetail> {
  return apiFetch<ExecutionDetail>(
    SERVICE,
    `api/v1/executions/${encodeURIComponent(internalId)}`,
    { signal }
  );
}

export function captureExecution(
  body: CreateExecutionRequest
): Promise<Execution> {
  return apiFetch<Execution>(SERVICE, "api/v1/executions", {
    method: "POST",
    body,
  });
}

export function correctExecution(
  internalId: string,
  body: CorrectExecutionRequest
): Promise<Execution> {
  return apiFetch<Execution>(
    SERVICE,
    `api/v1/executions/${encodeURIComponent(internalId)}/correct`,
    { method: "POST", body }
  );
}

export function cancelExecution(
  internalId: string,
  body: CancelExecutionRequest
): Promise<Execution> {
  return apiFetch<Execution>(
    SERVICE,
    `api/v1/executions/${encodeURIComponent(internalId)}/cancel`,
    { method: "POST", body }
  );
}
