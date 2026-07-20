import { apiFetch, type RequestOptions } from "@/lib/http";
import type {
  CreateOrderRequest,
  Order,
  OrderDetail,
  OrderSearchParams,
  Page,
  UpdateOrderRequest,
} from "@/types/api";

const SERVICE = "trading" as const;

export function searchOrders(
  params: OrderSearchParams,
  signal?: AbortSignal
): Promise<Page<Order>> {
  return apiFetch<Page<Order>>(SERVICE, "api/v1/orders", {
    query: params as RequestOptions["query"],
    signal,
  });
}

export function getOrder(
  orderId: string,
  signal?: AbortSignal
): Promise<OrderDetail> {
  return apiFetch<OrderDetail>(
    SERVICE,
    `api/v1/orders/${encodeURIComponent(orderId)}`,
    { signal }
  );
}

export function createOrder(body: CreateOrderRequest): Promise<Order> {
  return apiFetch<Order>(SERVICE, "api/v1/orders", { method: "POST", body });
}

export function updateOrder(
  orderId: string,
  body: UpdateOrderRequest
): Promise<Order> {
  return apiFetch<Order>(
    SERVICE,
    `api/v1/orders/${encodeURIComponent(orderId)}`,
    { method: "PUT", body }
  );
}

export function deleteOrder(orderId: string): Promise<void> {
  return apiFetch<void>(
    SERVICE,
    `api/v1/orders/${encodeURIComponent(orderId)}`,
    { method: "DELETE" }
  );
}
