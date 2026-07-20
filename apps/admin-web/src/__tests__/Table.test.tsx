import { render, screen } from "@testing-library/react";
import { Column } from "primereact/column";
import Table from "@/components/Table/Table";

interface Row {
  id: number;
}

function rows(n: number): Row[] {
  return Array.from({ length: n }, (_, i) => ({ id: i }));
}

describe("Table paginator sync", () => {
  it("highlights the page supplied by the controlled `page` prop", () => {
    render(
      <Table
        dataList={rows(10)}
        totalNum={30}
        page={2}
        onPageChange={jest.fn()}
        aria-label="Rows"
      >
        <Column field="id" header="ID" />
      </Table>
    );
    // PrimeReact marks the active page button with the p-highlight class.
    const active = document.querySelector('.p-paginator-page.p-highlight');
    expect(active).not.toBeNull();
    expect(active?.textContent).toBe("3"); // zero-based page 2 -> label "3"
  });

  it("moves the highlight when the parent resets `page` (e.g. filter change)", () => {
    const { rerender } = render(
      <Table
        dataList={rows(10)}
        totalNum={30}
        page={2}
        onPageChange={jest.fn()}
        aria-label="Rows"
      >
        <Column field="id" header="ID" />
      </Table>
    );
    expect(
      document.querySelector('.p-paginator-page.p-highlight')
        ?.textContent
    ).toBe("3");

    rerender(
      <Table
        dataList={rows(10)}
        totalNum={30}
        page={0}
        onPageChange={jest.fn()}
        aria-label="Rows"
      >
        <Column field="id" header="ID" />
      </Table>
    );
    expect(
      document.querySelector('.p-paginator-page.p-highlight')
        ?.textContent
    ).toBe("1");
  });
});
