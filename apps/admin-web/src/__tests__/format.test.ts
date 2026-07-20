import { formatDecimal, formatMoney, humanizeEnum } from "@/lib/format";

describe("formatDecimal", () => {
  it("preserves exact decimal strings without float math", () => {
    // A value that would lose precision through parseFloat.
    expect(formatDecimal("100000000000.12345678", 2)).toBe(
      "100,000,000,000.12345678"
    );
  });

  it("pads to the minimum fraction digits", () => {
    expect(formatDecimal("5", 2)).toBe("5.00");
    expect(formatDecimal("5.1", 2)).toBe("5.10");
  });

  it("groups thousands", () => {
    expect(formatDecimal("1234567.89", 2)).toBe("1,234,567.89");
  });

  it("handles negatives and empty input", () => {
    expect(formatDecimal("-42.5", 2)).toBe("-42.50");
    expect(formatDecimal(null)).toBe("—");
    expect(formatDecimal(undefined)).toBe("—");
  });
});

describe("formatMoney", () => {
  it("appends the currency code", () => {
    expect(formatMoney("1000", "USD")).toBe("1,000.00 USD");
  });
  it("omits currency when not provided", () => {
    expect(formatMoney("1000")).toBe("1,000.00");
  });
});

describe("humanizeEnum", () => {
  it("title-cases underscored enums", () => {
    expect(humanizeEnum("PARTIALLY_FILLED")).toBe("Partially Filled");
    expect(humanizeEnum("TRADE_CANCEL")).toBe("Trade Cancel");
  });
});
