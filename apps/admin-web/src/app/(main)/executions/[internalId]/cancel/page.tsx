"use client";

import { useParams, useRouter } from "next/navigation";
import { Form } from "react-final-form";
import { Button } from "primereact/button";
import { PageHeader } from "@/components/PageHeader";
import { TextField, TextareaField } from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useMutation } from "@/hooks/useMutation";
import { cancelExecution } from "@/api/executions";
import type { CancelExecutionRequest } from "@/types/api";

interface CancelValues {
  execId?: string;
  text?: string;
}

export default function CancelExecutionPage() {
  const params = useParams<{ internalId: string }>();
  const internalId = decodeURIComponent(params.internalId);
  const router = useRouter();
  const toast = useToast();
  const mutation = useMutation((body: CancelExecutionRequest) =>
    cancelExecution(internalId, body)
  );

  const backTo = `/executions/${encodeURIComponent(internalId)}`;

  const onSubmit = async (values: CancelValues) => {
    const body: CancelExecutionRequest = {
      execId: values.execId || undefined,
      text: values.text || undefined,
    };
    try {
      const execution = await mutation.mutate(body);
      if (execution) {
        toast.successToast({ detail: "Execution cancelled (chain voided)." });
        router.push(`/executions/${encodeURIComponent(execution.internalId)}`);
      }
    } catch {
      /* surfaced below */
    }
  };

  return (
    <div>
      <PageHeader
        title="Cancel execution"
        description="Append a cancellation record that voids the entire chain."
      />
      <p className="banner banner-warning" role="status">
        Cancelling appends an immutable cancellation and voids this execution
        chain. This cannot be undone.
      </p>
      <Form<CancelValues> onSubmit={onSubmit}>
        {({ handleSubmit }) => (
          <form onSubmit={handleSubmit} noValidate className="entity-form">
            {mutation.error ? <ErrorState error={mutation.error} /> : null}
            <div className="form-grid">
              <TextField
                name="execId"
                label="Cancellation Exec ID"
                help="Optional — generated when omitted"
              />
            </div>
            <TextareaField name="text" label="Reason" />
            <div className="form-actions">
              <Button
                type="submit"
                label="Confirm cancellation"
                icon="pi pi-times"
                severity="danger"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
              <Button
                type="button"
                label="Go back"
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
