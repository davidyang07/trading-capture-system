/**
 * Client-side form validation (UX only — the backend remains authoritative, §12).
 *
 * Decimal fields are validated as strings against the DECIMAL(precision,scale) rules the
 * services enforce, WITHOUT parsing to a JS number (avoids float rounding, ADR-0014).
 */

export function required(value: unknown): string | undefined {
  if (value === undefined || value === null || value === "") {
    return "Required";
  }
  return undefined;
}

/**
 * Validate a positive decimal string with at most `scale` fraction digits and `precision`
 * total significant digits. Returns an error message or undefined.
 */
export function positiveDecimal(
  scale: number,
  precision = 19
): (value: string | undefined) => string | undefined {
  return (value) => {
    if (value === undefined || value === "") {
      return "Required";
    }
    if (!/^\d+(\.\d+)?$/.test(value)) {
      return "Enter a positive number";
    }
    const [intPart, fracPart = ""] = value.split(".");
    if (fracPart.length > scale) {
      return `At most ${scale} decimal place${scale === 1 ? "" : "s"}`;
    }
    const significant = (intPart.replace(/^0+/, "") + fracPart).length;
    if (intPart.replace(/^0+/, "").length > precision - scale) {
      return "Value is too large";
    }
    if (significant === 0 || /^0+(\.0+)?$/.test(value)) {
      return "Must be greater than zero";
    }
    return undefined;
  };
}

export function currencyCode(value: string | undefined): string | undefined {
  if (!value) {
    return "Required";
  }
  if (!/^[A-Z]{3}$/.test(value)) {
    return "Use a 3-letter ISO code (e.g. USD)";
  }
  return undefined;
}

/** Compose multiple validators, returning the first error. */
export function compose(
  ...validators: Array<(value: string | undefined) => string | undefined>
): (value: string | undefined) => string | undefined {
  return (value) => {
    for (const validate of validators) {
      const error = validate(value);
      if (error) {
        return error;
      }
    }
    return undefined;
  };
}
