"use client";

import { Dialog } from "primereact/dialog";
import { Form } from "react-final-form";
import { Button } from "primereact/button";
import {
  SelectField,
  TextField,
  TextareaField,
} from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useIdempotencyKey } from "@/hooks/useIdempotencyKey";
import { useMutation } from "@/hooks/useMutation";
import { postCashTransaction, postSecurityTransaction } from "@/api/accounts";
import {
  CASH_DIRECTION_OPTIONS,
  CASH_TX_TYPE_OPTIONS,
  SECURITY_DIRECTION_OPTIONS,
  SECURITY_TX_TYPE_OPTIONS,
} from "@/lib/options";
import { compose, currencyCode, positiveDecimal, required } from "@/lib/validation";
import type {
  PostCashTransactionRequest,
  PostSecurityTransactionRequest,
} from "@/types/api";

interface CashValues {
  direction: string;
  transactionType: string;
  amount: string;
  currency: string;
  description?: string;
}

export function PostCashDialog({
  accountId,
  visible,
  onHide,
  onPosted,
}: {
  accountId: string;
  visible: boolean;
  onHide: () => void;
  onPosted: () => void;
}) {
  const toast = useToast();
  const mutation = useMutation((body: PostCashTransactionRequest) =>
    postCashTransaction(accountId, body)
  );
  const idempotency = useIdempotencyKey();
  const hide = () => {
    idempotency.reset();
    onHide();
  };

  const onSubmit = async (values: CashValues) => {
    const body: PostCashTransactionRequest = {
      direction: values.direction as PostCashTransactionRequest["direction"],
      transactionType:
        values.transactionType as PostCashTransactionRequest["transactionType"],
      amount: values.amount,
      currency: values.currency,
      description: values.description || undefined,
    };
    try {
      await mutation.mutate({ ...body, idempotencyKey: idempotency.keyFor(body) });
      toast.successToast({ detail: "Cash transaction posted." });
      mutation.reset();
      onPosted();
      hide();
    } catch {
      /* surfaced in-dialog */
    }
  };

  return (
    <Dialog
      header="Post cash transaction"
      visible={visible}
      onHide={hide}
      style={{ width: "32rem", maxWidth: "95vw" }}
      dismissableMask
    >
      <Form<CashValues>
        onSubmit={onSubmit}
        validate={(values) => {
          const errors: Partial<Record<keyof CashValues, string>> = {};
          if (required(values.direction)) errors.direction = "Required";
          if (required(values.transactionType))
            errors.transactionType = "Required";
          const amt = positiveDecimal(4)(values.amount);
          if (amt) errors.amount = amt;
          const ccy = currencyCode(values.currency);
          if (ccy) errors.currency = ccy;
          return errors;
        }}
      >
        {({ handleSubmit }) => (
          <form onSubmit={handleSubmit} noValidate className="entity-form">
            {mutation.error ? <ErrorState error={mutation.error} /> : null}
            <div className="form-grid">
              <SelectField
                name="direction"
                label="Direction"
                required
                options={CASH_DIRECTION_OPTIONS}
              />
              <SelectField
                name="transactionType"
                label="Type"
                required
                options={CASH_TX_TYPE_OPTIONS}
              />
              <TextField
                name="amount"
                label="Amount"
                required
                inputMode="decimal"
              />
              <TextField name="currency" label="Currency" required />
            </div>
            <TextareaField name="description" label="Description" />
            <div className="form-actions">
              <Button
                type="submit"
                label="Post"
                icon="pi pi-check"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
            </div>
          </form>
        )}
      </Form>
    </Dialog>
  );
}

interface SecurityValues {
  direction: string;
  transactionType: string;
  instrumentId: string;
  quantity: string;
  description?: string;
}

export function PostSecurityDialog({
  accountId,
  visible,
  onHide,
  onPosted,
}: {
  accountId: string;
  visible: boolean;
  onHide: () => void;
  onPosted: () => void;
}) {
  const toast = useToast();
  const mutation = useMutation((body: PostSecurityTransactionRequest) =>
    postSecurityTransaction(accountId, body)
  );
  const idempotency = useIdempotencyKey();
  const hide = () => {
    idempotency.reset();
    onHide();
  };

  const onSubmit = async (values: SecurityValues) => {
    const body: PostSecurityTransactionRequest = {
      direction:
        values.direction as PostSecurityTransactionRequest["direction"],
      transactionType:
        values.transactionType as PostSecurityTransactionRequest["transactionType"],
      instrumentId: values.instrumentId,
      quantity: values.quantity,
      description: values.description || undefined,
    };
    try {
      await mutation.mutate({ ...body, idempotencyKey: idempotency.keyFor(body) });
      toast.successToast({ detail: "Security transaction posted." });
      mutation.reset();
      onPosted();
      hide();
    } catch {
      /* surfaced in-dialog */
    }
  };

  return (
    <Dialog
      header="Post security transaction"
      visible={visible}
      onHide={hide}
      style={{ width: "32rem", maxWidth: "95vw" }}
      dismissableMask
    >
      <Form<SecurityValues>
        onSubmit={onSubmit}
        validate={(values) => {
          const errors: Partial<Record<keyof SecurityValues, string>> = {};
          if (required(values.direction)) errors.direction = "Required";
          if (required(values.transactionType))
            errors.transactionType = "Required";
          if (required(values.instrumentId)) errors.instrumentId = "Required";
          const qty = compose(positiveDecimal(4))(values.quantity);
          if (qty) errors.quantity = qty;
          return errors;
        }}
      >
        {({ handleSubmit }) => (
          <form onSubmit={handleSubmit} noValidate className="entity-form">
            {mutation.error ? <ErrorState error={mutation.error} /> : null}
            <div className="form-grid">
              <SelectField
                name="direction"
                label="Direction"
                required
                options={SECURITY_DIRECTION_OPTIONS}
              />
              <SelectField
                name="transactionType"
                label="Type"
                required
                options={SECURITY_TX_TYPE_OPTIONS}
              />
              <TextField name="instrumentId" label="Instrument ID" required />
              <TextField
                name="quantity"
                label="Quantity"
                required
                inputMode="decimal"
              />
            </div>
            <TextareaField name="description" label="Description" />
            <div className="form-actions">
              <Button
                type="submit"
                label="Post"
                icon="pi pi-check"
                loading={mutation.pending}
                disabled={mutation.pending}
              />
            </div>
          </form>
        )}
      </Form>
    </Dialog>
  );
}
