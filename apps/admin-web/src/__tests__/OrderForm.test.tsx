import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { OrderForm } from "@/app/(main)/orders/OrderForm";

describe("OrderForm", () => {
  it("renders all order fields", () => {
    render(
      <OrderForm submitLabel="Create order" pending={false} onSubmit={jest.fn()} />
    );
    // Accessible name excludes the aria-hidden required asterisk.
    expect(screen.getByRole("textbox", { name: "Account ID" })).toBeInTheDocument();
    expect(
      screen.getByRole("textbox", { name: "Instrument ID" })
    ).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Price" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Quantity" })).toBeInTheDocument();
  });

  it("blocks submission and announces errors when required fields are empty", async () => {
    const onSubmit = jest.fn();
    render(
      <OrderForm submitLabel="Create order" pending={false} onSubmit={onSubmit} />
    );
    await userEvent.click(
      screen.getByRole("button", { name: /create order/i })
    );
    expect(onSubmit).not.toHaveBeenCalled();
    expect(
      await screen.findByText(/Please correct the highlighted fields/)
    ).toBeInTheDocument();
  });

  it("rejects an over-precise quantity (no silent rounding)", async () => {
    const onSubmit = jest.fn();
    render(
      <OrderForm submitLabel="Create order" pending={false} onSubmit={onSubmit} />
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "Quantity" }),
      "1.123456"
    );
    await userEvent.click(
      screen.getByRole("button", { name: /create order/i })
    );
    expect(onSubmit).not.toHaveBeenCalled();
    expect(
      await screen.findByText(/At most 4 decimal places/)
    ).toBeInTheDocument();
  });

  it("submits decimal fields as strings (no numeric coercion — ADR-0014)", async () => {
    const onSubmit = jest.fn();
    render(
      <OrderForm
        submitLabel="Create order"
        pending={false}
        onSubmit={onSubmit}
        initialValues={{
          accountId: "ACC-1",
          instrumentId: "AAPL",
          side: "BUY",
          orderType: "LIMIT",
          price: "189.25000000",
          priceType: "LIMIT",
          currency: "USD",
          quantity: "100.1250",
          settleType: "T_PLUS_2",
          settleDate: "2026-01-05",
        }}
      />
    );
    await userEvent.click(
      screen.getByRole("button", { name: /create order/i })
    );
    expect(onSubmit).toHaveBeenCalledTimes(1);
    const submitted = onSubmit.mock.calls[0][0];
    expect(submitted.price).toBe("189.25000000");
    expect(submitted.quantity).toBe("100.1250");
    expect(typeof submitted.price).toBe("string");
    expect(typeof submitted.quantity).toBe("string");
  });

  it("disables the submit button while pending", () => {
    render(
      <OrderForm submitLabel="Create order" pending onSubmit={jest.fn()} />
    );
    expect(
      screen.getByRole("button", { name: /create order/i })
    ).toBeDisabled();
  });

  it("has no automated accessibility violations in its initial state", async () => {
    const { container } = render(
      <OrderForm submitLabel="Create order" pending={false} onSubmit={jest.fn()} />
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
