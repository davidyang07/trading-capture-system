import {
  currencyCode,
  positiveDecimal,
  required,
} from "@/lib/validation";

describe("required", () => {
  it("flags empty values", () => {
    expect(required("")).toBe("Required");
    expect(required(undefined)).toBe("Required");
    expect(required("x")).toBeUndefined();
  });
});

describe("positiveDecimal", () => {
  const qty = positiveDecimal(4);

  it("accepts values within the scale", () => {
    expect(qty("100")).toBeUndefined();
    expect(qty("100.1234")).toBeUndefined();
  });

  it("rejects too many fraction digits (no rounding)", () => {
    expect(qty("1.12345")).toBe("At most 4 decimal places");
  });

  it("rejects non-numeric and zero", () => {
    expect(qty("abc")).toBe("Enter a positive number");
    expect(qty("0")).toBe("Must be greater than zero");
    expect(qty("0.0000")).toBe("Must be greater than zero");
  });

  it("rejects values exceeding the integer precision", () => {
    // price scale 8, precision 19 -> max 11 integer digits
    const px = positiveDecimal(8);
    expect(px("123456789012.0")).toBe("Value is too large");
  });
});

describe("currencyCode", () => {
  it("requires a 3-letter ISO code", () => {
    expect(currencyCode("USD")).toBeUndefined();
    expect(currencyCode("usd")).toBe("Use a 3-letter ISO code (e.g. USD)");
    expect(currencyCode("US")).toBe("Use a 3-letter ISO code (e.g. USD)");
    expect(currencyCode("")).toBe("Required");
  });
});
