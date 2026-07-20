import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import {
  AsyncBoundary,
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/components/feedback/States";
import { ApiError } from "@/lib/api-error";

describe("feedback states", () => {
  it("announces loading with a polite live region", () => {
    render(<LoadingState label="Loading orders…" />);
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Loading orders…");
  });

  it("shows an empty message", () => {
    render(<EmptyState message="No orders yet." />);
    expect(screen.getByText("No orders yet.")).toBeInTheDocument();
  });

  it("renders a safe error with correlation id and a retry action", async () => {
    const onRetry = jest.fn();
    const error = new ApiError({
      kind: "server",
      status: 500,
      message: "raw stack detail",
      correlationId: "corr-42",
    });
    render(<ErrorState error={error} onRetry={onRetry} />);

    // Safe message, not the raw detail.
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong on our side. Please try again."
    );
    expect(screen.queryByText("raw stack detail")).not.toBeInTheDocument();
    expect(screen.getByText("corr-42")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /retry/i }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("has no accessibility violations in the error state", async () => {
    const error = ApiError.network();
    const { container } = render(<ErrorState error={error} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe("AsyncBoundary", () => {
  const render_ = (props: Partial<Parameters<typeof AsyncBoundary>[0]>) =>
    render(
      <AsyncBoundary
        loading={false}
        error={undefined}
        data={undefined}
        {...(props as object)}
      >
        {(d: unknown) => <div>loaded:{String(d)}</div>}
      </AsyncBoundary>
    );

  it("shows loading before data arrives", () => {
    render_({ loading: true });
    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("shows the error state when the first load fails", () => {
    render_({ error: ApiError.network() });
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows empty state when the predicate matches", () => {
    render_({
      data: [] as unknown[],
      isEmpty: (d) => (d as unknown[]).length === 0,
      emptyMessage: "Nothing here",
    });
    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });

  it("renders children with data", () => {
    render_({ data: "value" });
    expect(screen.getByText("loaded:value")).toBeInTheDocument();
  });
});
