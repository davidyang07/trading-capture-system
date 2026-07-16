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

interface ITableProps {
  dataList: any;
  totalNum: number;
  empty?: string | React.ReactNode;
  sizeOptions?: number[] | undefined;
  onPageChange: (params: IPaginator) => void;
  [T: string | number]: any;
}

const Table: React.FC<ITableProps> = ({
  dataList = [],
  totalNum = 0,
  children,
  sizeOptions = DEFAULT_PAGE_OPTIONS,
  onPageChange,
  ...props
}) => {
  const [firstIndex, setFirstIndex] = useState(0);
  const [pageSize, setPageSize] = useState(sizeOptions[0]);
  const handlePageChange = ({ first, rows, page, totalPages }: any) => {
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
        {...props}
        value={dataList}
        selectionMode="single"
        emptyMessage={() => (
          <div className="table-empty-box">
            <i className="pi pi-exclamation-circle"></i>
            {props.empty ? props.empty : <p>No available options</p>}
          </div>
        )}
      >
        {children}
      </DataTable>
      {totalNum > 0 && dataList?.length >= 0 ? (
        <div className="table-paginator-container">
          <Paginator
            first={firstIndex}
            rows={pageSize}
            totalRecords={totalNum }
            rowsPerPageOptions={sizeOptions}
            onPageChange={handlePageChange}
          />
        </div>
      ) : null}
    </div>
  );
};

export default Table;
