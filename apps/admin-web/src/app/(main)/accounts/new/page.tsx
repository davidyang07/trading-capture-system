"use client";

import { useRouter } from "next/navigation";
import { Form } from "react-final-form";
import { Button } from "primereact/button";
import { PageHeader } from "@/components/PageHeader";
import { SelectField, TextField } from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useMutation } from "@/hooks/useMutation";
import { createAccount } from "@/api/accounts";
import { ACCOUNT_TYPE_OPTIONS } from "@/lib/options";
import { currencyCode, required } from "@/lib/validation";
import type { CreateAccountRequest } from "@/types/api";

interface AccountValues {
  accountName: string;
  accountType: string;
  baseCurrency: string;
}

export default function NewAccountPage() {
  const router = useRouter();
  const toast = useToast();
  const mutation = useMutation(createAccount);

  const onSubmit = async (values: AccountValues) => {
    const body: CreateAccountRequest = {
      accountName: values.accountName,
      accountType: values.accountType as CreateAccountRequest["accountType"],
      baseCurrency: values.baseCurrency,
    };
    try {
      const account = await mutation.mutate(body);
      if (account) {
        toast.successToast({ detail: `Account ${account.accountId} created.` });
        router.push(`/accounts/${encodeURIComponent(account.accountId)}`);
      }
    } catch {
      /* surfaced below */
    }
  };

  return (
    <div>
      <PageHeader title="New account" description="Open a financial account." />
      <Form<AccountValues>
        onSubmit={onSubmit}
        validate={(values) => {
          const errors: Partial<Record<keyof AccountValues, string>> = {};
          if (required(values.accountName)) errors.accountName = "Required";
          if (required(values.accountType)) errors.accountType = "Required";
          const ccy = currencyCode(values.baseCurrency);
          if (ccy) errors.baseCurrency = ccy;
          return errors;
        }}
      >
        {({ handleSubmit, hasValidationErrors, submitFailed }) => (
          <form onSubmit={handleSubmit} noValidate className="entity-form">
            {mutation.error ? <ErrorState error={mutation.error} /> : null}
            <div className="form-grid">
              <TextField name="accountName" label="Account name" required />
              <SelectField
                name="accountType"
                label="Account type"
                required
                options={ACCOUNT_TYPE_OPTIONS}
              />
              <TextField
                name="baseCurrency"
                label="Base currency"
                required
                help="3-letter ISO code, e.g. USD"
              />
            </div>
            {submitFailed && hasValidationErrors ? (
              <p className="form-summary-error" role="alert">
                Please correct the highlighted fields.
              </p>
            ) : null}
            <div className="form-actions">
              <Button
                type="submit"
                label="Create account"
                icon="pi pi-check"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
              <Button
                type="button"
                label="Cancel"
                text
                onClick={() => router.push("/accounts")}
              />
            </div>
          </form>
        )}
      </Form>
    </div>
  );
}
