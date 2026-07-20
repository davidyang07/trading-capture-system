"use client";

import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/PageHeader";
import { useToast } from "@/components/Toast/ToastProvider";
import { useMutation } from "@/hooks/useMutation";
import { createOrder } from "@/api/orders";
import { OrderForm } from "@/app/(main)/orders/OrderForm";
import type { CreateOrderRequest } from "@/types/api";

export default function NewOrderPage() {
  const router = useRouter();
  const toast = useToast();
  const mutation = useMutation(createOrder);

  const handleSubmit = async (values: CreateOrderRequest) => {
    try {
      const order = await mutation.mutate(values);
      if (order) {
        toast.successToast({ detail: `Order ${order.orderId} created.` });
        router.push(`/orders/${encodeURIComponent(order.orderId)}`);
      }
    } catch {
      // Field/validation and safe messages are surfaced by the form's ErrorState.
    }
  };

  return (
    <div>
      <PageHeader title="New order" description="Create a trade order." />
      <OrderForm
        submitLabel="Create order"
        pending={mutation.pending}
        error={mutation.error}
        onSubmit={handleSubmit}
      />
    </div>
  );
}
