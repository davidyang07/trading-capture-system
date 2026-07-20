"use client";

import React, { useEffect, useState } from "react";
import { DataTable } from "primereact/datatable";
import { Paginator } from "primereact/paginator";
import { DEFAULT_PAGE_OPTIONS } from "@/utils/const";
import "./Table.scss";

interface IPaginator {
  pageSize: number;
  pageNumber: number;
}

interface ITableProps<T extends object> {
  dataList: T[];
  totalNum: number;
  empty?: string | React.ReactNode;
  sizeOptions?: number[] | undefined;
  onPageChange: (params: IPaginator) => void;
  /**
   * Current zero-based page owned by the parent. When provided, the paginator
   * stays in sync with parent-driven page resets (e.g. a filter change that
   * resets `page` to 0), preventing the highlighted page from drifting away
   * from the data actually shown.
   */
  page?: number;
  children?: React.ReactNode;
  "aria-label"?: string;
}

function Table<T extends object>({
  dataList = [],
  totalNum = 0,
  children,
  empty,
  "aria-label": ariaLabel,
  sizeOptions = DEFAULT_PAGE_OPTIONS,
  onPageChange,
  page,
}: ITableProps<T>) {
  const [firstIndex, setFirstIndex] = useState(0);
  const [pageSize, setPageSize] = useState(sizeOptions[0]);

  // Keep the paginator aligned with a parent-controlled page (e.g. reset to 0
  // on a filter change). Only runs when the caller drives `page` explicitly.
  useEffect(() => {
    if (page !== undefined) {
      setFirstIndex(page * pageSize);
    }
  }, [page, pageSize]);
  const handlePageChange = ({ first, rows }: { first: number; rows: number }) => {
    const pageNumber = Math.ceil((first + 1) / rows);
    setFirstIndex(first);
    setPageSize(rows);

    if (onPageChange && typeof onPageChange === "function") {
      onPageChange({
        pageSize: rows,
        pageNumber: pageNumber - 1,
      });
    }
  };

  return (
    <div className="table-container">
      <DataTable
        value={dataList}
        aria-label={ariaLabel}
        pt={{ table: { "aria-label": ariaLabel } }}
        emptyMessage={() => (
          <div className="table-empty-box">
            <i className="pi pi-exclamation-circle"></i>
            {empty ?? <p>No available options</p>}
          </div>
        )}
      >
        {children}
      </DataTable>
      {totalNum > pageSize ? (
        <div className="table-paginator-container">
          <Paginator
            first={firstIndex}
            rows={pageSize}
            totalRecords={totalNum}
            rowsPerPageOptions={sizeOptions}
            onPageChange={({ first, rows }) =>
              handlePageChange({ first, rows })
            }
          />
        </div>
      ) : null}
    </div>
  );
}

export default Table;
