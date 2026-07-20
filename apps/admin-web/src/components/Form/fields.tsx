"use client";

import { useId } from "react";
import { Field } from "react-final-form";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { Dropdown } from "primereact/dropdown";

/**
 * Accessible react-final-form field wrappers. Each renders a real <label> bound via
 * htmlFor/id, sets aria-invalid and aria-describedby, and exposes errors with role="alert"
 * so screen readers announce them (§12, §14).
 */

export interface Option {
  label: string;
  value: string;
}

interface BaseProps {
  name: string;
  label: string;
  required?: boolean;
  help?: string;
}

function showError(meta: {
  touched?: boolean;
  error?: string;
  submitError?: string;
}): string | undefined {
  if (meta.touched && (meta.error || meta.submitError)) {
    return meta.error ?? meta.submitError;
  }
  return undefined;
}

function LabelRow({
  htmlFor,
  label,
  required,
}: {
  htmlFor: string;
  label: string;
  required?: boolean;
}) {
  return (
    <label htmlFor={htmlFor} className="form-label">
      {label}
      {required ? (
        <span className="form-required" aria-hidden="true">
          {" *"}
        </span>
      ) : null}
    </label>
  );
}

export function TextField({
  name,
  label,
  required,
  help,
  placeholder,
  inputMode,
}: BaseProps & { placeholder?: string; inputMode?: "text" | "decimal" }) {
  const id = useId();
  const errId = `${id}-err`;
  const helpId = `${id}-help`;
  return (
    <Field name={name}>
      {({ input, meta }) => {
        const error = showError(meta);
        return (
          <div className={`form-item ${error ? "invalid" : ""}`}>
            <LabelRow htmlFor={id} label={label} required={required} />
            <InputText
              id={id}
              {...input}
              placeholder={placeholder}
              inputMode={inputMode}
              aria-required={required}
              aria-invalid={!!error}
              aria-describedby={
                `${help ? helpId : ""} ${error ? errId : ""}`.trim() ||
                undefined
              }
            />
            {help ? (
              <small id={helpId} className="form-help">
                {help}
              </small>
            ) : null}
            {error ? (
              <small id={errId} className="err" role="alert">
                {error}
              </small>
            ) : null}
          </div>
        );
      }}
    </Field>
  );
}

export function TextareaField({ name, label, required, help }: BaseProps) {
  const id = useId();
  const errId = `${id}-err`;
  const helpId = `${id}-help`;
  return (
    <Field name={name}>
      {({ input, meta }) => {
        const error = showError(meta);
        return (
          <div className={`form-item ${error ? "invalid" : ""}`}>
            <LabelRow htmlFor={id} label={label} required={required} />
            <InputTextarea
              id={id}
              rows={3}
              {...input}
              aria-required={required}
              aria-invalid={!!error}
              aria-describedby={`${help ? helpId : ""} ${
                error ? errId : ""
              }`.trim() || undefined}
            />
            {help ? (
              <small id={helpId} className="form-help">
                {help}
              </small>
            ) : null}
            {error ? (
              <small id={errId} className="err" role="alert">
                {error}
              </small>
            ) : null}
          </div>
        );
      }}
    </Field>
  );
}

export function SelectField({
  name,
  label,
  required,
  options,
  placeholder,
  help,
}: BaseProps & { options: Option[]; placeholder?: string }) {
  const id = useId();
  const errId = `${id}-err`;
  const helpId = `${id}-help`;
  return (
    <Field name={name}>
      {({ input, meta }) => {
        const error = showError(meta);
        return (
          <div className={`form-item ${error ? "invalid" : ""}`}>
            <LabelRow htmlFor={id} label={label} required={required} />
            <Dropdown
              inputId={id}
              ariaLabel={label}
              value={input.value}
              options={options}
              optionLabel="label"
              optionValue="value"
              onChange={(e) => input.onChange(e.value)}
              onBlur={() => input.onBlur()}
              placeholder={placeholder ?? "Select…"}
              aria-required={required}
              aria-invalid={!!error}
              aria-describedby={`${help ? helpId : ""} ${
                error ? errId : ""
              }`.trim() || undefined}
              pt={{ select: { "aria-label": label } }}
            />
            {help ? (
              <small id={helpId} className="form-help">
                {help}
              </small>
            ) : null}
            {error ? (
              <small id={errId} className="err" role="alert">
                {error}
              </small>
            ) : null}
          </div>
        );
      }}
    </Field>
  );
}

export function DateField({ name, label, required, help }: BaseProps) {
  const id = useId();
  const errId = `${id}-err`;
  const helpId = `${id}-help`;
  return (
    <Field name={name}>
      {({ input, meta }) => {
        const error = showError(meta);
        return (
          <div className={`form-item ${error ? "invalid" : ""}`}>
            <LabelRow htmlFor={id} label={label} required={required} />
            <InputText
              id={id}
              type="date"
              {...input}
              aria-required={required}
              aria-invalid={!!error}
              aria-describedby={`${help ? helpId : ""} ${
                error ? errId : ""
              }`.trim() || undefined}
            />
            {help ? (
              <small id={helpId} className="form-help">
                {help}
              </small>
            ) : null}
            {error ? (
              <small id={errId} className="err" role="alert">
                {error}
              </small>
            ) : null}
          </div>
        );
      }}
    </Field>
  );
}
