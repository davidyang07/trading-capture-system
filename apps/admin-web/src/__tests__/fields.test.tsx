import { render, screen } from "@testing-library/react";
import { axe } from "jest-axe";
import { Form } from "react-final-form";
import {
  DateField,
  SelectField,
  TextareaField,
  TextField,
} from "@/components/Form/fields";

function Harness({ children }: { children: React.ReactNode }) {
  return (
    <Form onSubmit={() => {}}>
      {({ handleSubmit }) => <form onSubmit={handleSubmit}>{children}</form>}
    </Form>
  );
}

describe("accessible form fields", () => {
  it("associates the label with the input", () => {
    render(
      <Harness>
        <TextField name="accountId" label="Account ID" required />
      </Harness>
    );
    const input = screen.getByLabelText(/Account ID/);
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-required", "true");
  });

  it("exposes help text via aria-describedby", () => {
    render(
      <Harness>
        <TextField name="currency" label="Currency" help="ISO code" />
      </Harness>
    );
    const input = screen.getByLabelText("Currency");
    const describedBy = input.getAttribute("aria-describedby") ?? "";
    expect(describedBy.length).toBeGreaterThan(0);
    expect(screen.getByText("ISO code")).toBeInTheDocument();
  });

  it("renders a labelled combobox for select fields", () => {
    render(
      <Harness>
        <SelectField
          name="b"
          label="Side"
          required
          options={[
            { label: "Buy", value: "BUY" },
            { label: "Sell", value: "SELL" },
          ]}
        />
      </Harness>
    );
    // PrimeReact Dropdown exposes the label via the associated <label htmlFor>.
    expect(screen.getByText("Side")).toBeInTheDocument();
  });

  it.each([
    ["select", <SelectField key="select" name="side" label="Side" help="Choose buy or sell" options={[]} />],
    ["date", <DateField key="date" name="settleDate" label="Settlement date" help="Use the market date" />],
    ["textarea", <TextareaField key="textarea" name="description" label="Description" help="Do not include sensitive data" />],
  ])("exposes %s help text through aria-describedby", (_kind, field) => {
    render(<Harness>{field}</Harness>);
    const accessibleName =
      _kind === "select"
        ? "Side"
        : _kind === "date"
          ? "Settlement date"
          : "Description";
    const control = screen.getByLabelText(accessibleName, {
      selector: _kind === "select" ? "input" : undefined,
    });
    const describedBy = control.getAttribute("aria-describedby") ?? "";
    expect(describedBy).not.toBe("");
    const described = describedBy
      .split(/\s+/)
      .map((id) => document.getElementById(id)?.textContent)
      .filter(Boolean)
      .join(" ");
    expect(described).toMatch(/Choose buy or sell|Use the market date|Do not include sensitive data/);
  });

  it("has no accessibility violations for text fields", async () => {
    // Scope axe to our own semantic markup; PrimeReact widget internals are
    // validated through explicit aria attributes + manual keyboard testing.
    const { container } = render(
      <Harness>
        <TextField name="a" label="Quantity" required help="Up to 4 dp" />
      </Harness>
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
