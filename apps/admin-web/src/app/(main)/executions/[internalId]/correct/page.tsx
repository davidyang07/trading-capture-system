"use client";

import { useRouter } from "next/navigation";
import { Form } from "react-final-form";
import { Button } from "primereact/button";
import { PageHeader } from "@/components/PageHeader";
import { TextField, TextareaField } from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useMutation } from "@/hooks/useMutation";
import { correctExecution } from "@/api/executions";
import { positiveDecimal } from "@/lib/validation";
import type { CorrectExecutionRequest } from "@/types/api";

interface CorrectValues {
  execId?: string;
  lastQty: string;
  lastPx: string;
  text?: string;
}

export default function CorrectExecutionPage({
  params,
}: {
  params: { internalId: string };
}) {
  const internalId = decodeURIComponent(params.internalId);
  const router = useRouter();
  const toast = useToast();
  const mutation = useMutation((body: CorrectExecutionRequest) =>
    correctExecution(internalId, body)
  );

  const backTo = `/executions/${encodeURIComponent(internalId)}`;

  const onSubmit = async (values: CorrectValues) => {
    const body: CorrectExecutionRequest = {
      execId: values.execId || undefined,
      lastQty: values.lastQty,
      lastPx: values.lastPx,
      text: values.text || undefined,
    };
    try {
      const execution = await mutation.mutate(body);
      if (execution) {
        toast.successToast({ detail: "Correction appended." });
        router.push(`/executions/${encodeURIComponent(execution.internalId)}`);
      }
    } catch {
      /* surfaced below */
    }
  };

  return (
    <div>
      <PageHeader
        title="Correct execution"
        description="Append a linked correction; the original record is never mutated."
      />
      <Form<CorrectValues>
        onSubmit={onSubmit}
        validate={(values) => {
          const errors: Partial<Record<keyof CorrectValues, string>> = {};
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
              <TextField
                name="execId"
                label="New Exec ID"
                help="Optional — generated when omitted"
              />
              <TextField
                name="lastQty"
                label="Corrected quantity"
                required
                inputMode="decimal"
                help="Up to 4 decimal places"
              />
              <TextField
                name="lastPx"
                label="Corrected price"
                required
                inputMode="decimal"
                help="Up to 8 decimal places"
              />
            </div>
            <TextareaField name="text" label="Reason" />
            {submitFailed && hasValidationErrors ? (
              <p className="form-summary-error" role="alert">
                Please correct the highlighted fields.
              </p>
            ) : null}
            <div className="form-actions">
              <Button
                type="submit"
                label="Append correction"
                icon="pi pi-check"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
              <Button
                type="button"
                label="Cancel"
                text
                onClick={() => router.push(backTo)}
              />
            </div>
          </form>
        )}
      </Form>
    </div>
  );
}
