"use client";

import { Form } from "react-final-form";
import { Button } from "primereact/button";
import {
  DateField,
  SelectField,
  TextField,
} from "@/components/Form/fields";
import { ErrorState } from "@/components/feedback/States";
import type { ApiError } from "@/lib/api-error";
import {
  ORDER_TYPE_OPTIONS,
  PRICE_TYPE_OPTIONS,
  SETTLE_TYPE_OPTIONS,
  SIDE_OPTIONS,
} from "@/lib/options";
import {
  currencyCode,
  positiveDecimal,
  required,
} from "@/lib/validation";
import type { CreateOrderRequest } from "@/types/api";

export interface OrderFormValues {
  accountId: string;
  instrumentId: string;
  side: string;
  orderType: string;
  price: string;
  priceType: string;
  currency: string;
  quantity: string;
  settleType: string;
  settleDate: string;
}

export function OrderForm({
  initialValues,
  submitLabel,
  pending,
  error,
  onSubmit,
}: {
  initialValues?: Partial<OrderFormValues>;
  submitLabel: string;
  pending: boolean;
  error?: ApiError;
  onSubmit: (values: CreateOrderRequest) => void;
}) {
  return (
    <Form<OrderFormValues>
      initialValues={initialValues}
      onSubmit={(values) => onSubmit(values as CreateOrderRequest)}
      validate={(values) => {
        const errors: Partial<Record<keyof OrderFormValues, string>> = {};
        if (required(values.accountId)) errors.accountId = "Required";
        if (required(values.instrumentId)) errors.instrumentId = "Required";
        if (required(values.side)) errors.side = "Required";
        if (required(values.orderType)) errors.orderType = "Required";
        if (required(values.priceType)) errors.priceType = "Required";
        if (required(values.settleType)) errors.settleType = "Required";
        if (required(values.settleDate)) errors.settleDate = "Required";
        const priceErr = positiveDecimal(8)(values.price);
        if (priceErr) errors.price = priceErr;
        const qtyErr = positiveDecimal(4)(values.quantity);
        if (qtyErr) errors.quantity = qtyErr;
        const ccyErr = currencyCode(values.currency);
        if (ccyErr) errors.currency = ccyErr;
        return errors;
      }}
    >
      {({ handleSubmit, hasValidationErrors, submitFailed }) => (
        <form onSubmit={handleSubmit} noValidate className="entity-form">
          {error ? <ErrorState error={error} /> : null}
          <div className="form-grid">
            <TextField name="accountId" label="Account ID" required />
            <TextField name="instrumentId" label="Instrument ID" required />
            <SelectField
              name="side"
              label="Side"
              required
              options={SIDE_OPTIONS}
            />
            <SelectField
              name="orderType"
              label="Order type"
              required
              options={ORDER_TYPE_OPTIONS}
            />
            <TextField
              name="price"
              label="Price"
              required
              inputMode="decimal"
              help="Up to 8 decimal places"
            />
            <SelectField
              name="priceType"
              label="Price type"
              required
              options={PRICE_TYPE_OPTIONS}
            />
            <TextField
              name="currency"
              label="Currency"
              required
              help="3-letter ISO code, e.g. USD"
            />
            <TextField
              name="quantity"
              label="Quantity"
              required
              inputMode="decimal"
              help="Up to 4 decimal places"
            />
            <SelectField
              name="settleType"
              label="Settlement type"
              required
              options={SETTLE_TYPE_OPTIONS}
            />
            <DateField name="settleDate" label="Settlement date" required />
          </div>
          {submitFailed && hasValidationErrors ? (
            <p className="form-summary-error" role="alert">
              Please correct the highlighted fields.
            </p>
          ) : null}
          <div className="form-actions">
            <Button
              type="submit"
              label={submitLabel}
              icon="pi pi-check"
              loading={pending}
              disabled={pending}
            />
          </div>
        </form>
      )}
    </Form>
  );
}
