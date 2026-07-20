"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Form } from "react-final-form";
import { Button } from "primereact/button";
import { PageHeader } from "@/components/PageHeader";
import { TextField, TextareaField } from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useMutation } from "@/hooks/useMutation";
import { captureExecution } from "@/api/executions";
import { positiveDecimal, required } from "@/lib/validation";
import type { CreateExecutionRequest } from "@/types/api";

interface CaptureValues {
  orderId: string;
  execId?: string;
  lastQty: string;
  lastPx: string;
  text?: string;
}

export default function CaptureExecutionPage() {
  const router = useRouter();
  const toast = useToast();
  const searchParams = useSearchParams();
  const mutation = useMutation(captureExecution);
  const presetOrderId = searchParams.get("orderId") ?? "";

  const onSubmit = async (values: CaptureValues) => {
    const body: CreateExecutionRequest = {
      orderId: values.orderId,
      execId: values.execId || undefined,
      lastQty: values.lastQty,
      lastPx: values.lastPx,
      text: values.text || undefined,
    };
    try {
      const execution = await mutation.mutate(body);
      if (execution) {
        toast.successToast({ detail: `Execution ${execution.execId} captured.` });
        router.push(`/executions/${encodeURIComponent(execution.internalId)}`);
      }
    } catch {
      /* surfaced below */
    }
  };

  return (
    <div>
      <PageHeader
        title="Capture execution"
        description="Record a trade fill against an eligible order."
      />
      <Form<CaptureValues>
        initialValues={{ orderId: presetOrderId }}
        onSubmit={onSubmit}
        validate={(values) => {
          const errors: Partial<Record<keyof CaptureValues, string>> = {};
          if (required(values.orderId)) errors.orderId = "Required";
          const qty = positiveDecimal(4)(values.lastQty);
          if (qty) errors.lastQty = qty;
          const px = positiveDecimal(8)(values.lastPx);
          if (px) errors.lastPx = px;
          return errors;
        }}
      >
        {({ handleSubmit, hasValidationErrors, submitFailed }) => (
          <form onSubmit={handleSubmit} noValidate className="entity-form">
            {mutation.error ? <ErrorState error={mutation.error} /> : null}
            <div className="form-grid">
              <TextField name="orderId" label="Order ID" required />
              <TextField
                name="execId"
                label="Exec ID"
                help="Optional — generated when omitted"
              />
              <TextField
                name="lastQty"
                label="Fill quantity"
                required
                inputMode="decimal"
                help="Up to 4 decimal places"
              />
              <TextField
                name="lastPx"
                label="Fill price"
                required
                inputMode="decimal"
                help="Up to 8 decimal places"
              />
            </div>
            <TextareaField name="text" label="Note" />
            {submitFailed && hasValidationErrors ? (
              <p className="form-summary-error" role="alert">
                Please correct the highlighted fields.
              </p>
            ) : null}
            <div className="form-actions">
              <Button
                type="submit"
                label="Capture execution"
                icon="pi pi-check"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
              <Button
                type="button"
                label="Cancel"
                text
                onClick={() => router.push("/executions")}
              />
            </div>
          </form>
        )}
      </Form>
    </div>
  );
}
