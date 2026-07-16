"use client";

import { useEffect, useReducer, useState } from "react";
import Link from "next/link";
import { Column } from "primereact/column";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Calendar } from "primereact/calendar";
import { Dropdown } from "primereact/dropdown";
import dayjs from "dayjs";
import Table from "@/components/Table/Table";
import { DEFAULT_PAGE_OPTIONS, SIDE_OPTIONS } from "@/utils/const";
import ExecDetailSidebar from "./DetailSidebar";
import { postExecutionSearch } from "@/api/execution";

const initialState = {
  searchParams: {
    transactTime: [],
    tradeDate: [],
  },
  pageNumber: 0,
  pageSize: DEFAULT_PAGE_OPTIONS[0],
  sortField: null,
  sortOrder: null,
  dataList: [],
  total: 0,
};

const reducer = (state: any, { type, payload }: any) => {
  switch (type) {
    case "onSearchChange":
      return { ...state, searchParams: { ...state.searchParams, ...payload } };
    case "onPageChange":
      const { pageNumber, pageSize } = payload;
      return { ...state, pageNumber, pageSize };
    case "onSortChange":
      const { sortField, sortOrder } = payload;
      return { ...state, sortField, sortOrder };
    case "onDataChange":
      return {
        ...state,
        dataList: [...payload.dataList],
        total: payload.total,
      };
    default:
      throw new Error();
  }
};

export default function ExecutionPage({ search = true, orderId }: any) {
  const [sidebarVisible, setSidebarVisible] = useState<boolean>(false);
  const [detailId, setDetailId] = useState<string>("");
  const [detail, setDetail] = useState<any>({}); //the better approach is actually simply store execId since this is the only field the child needs

  const [state, dispatch] = useReducer(
    reducer,
    initialState,
    (initialState: any) => initialState
  );

  //fetchData API - first construct object then call API
  //params: {pageNumber, pageSize, sortCriteria, searchCriteria} is something we will pass to API
  //_search is the search object we are maintaining in state
  const getTableList = () => {
    const _search:any = state.searchParams ?? {};
    const params: any = { ..._search };
    orderId && (params.orderIds = [orderId]);
    params.pageNumber = state.pageNumber;
    params.pageSize = state.pageSize;

    // Date
    if (
      _search.transactTime &&
      _search.transactTime?.[0] &&
      _search.transactTime?.[1]
    ) {
      const start = dayjs(_search.transactTime?.[0]).format("YYYY-MM-DD");
      const end = dayjs(_search.transactTime?.[1]).format("YYYY-MM-DD");
      params.transactDateStartAt = start;
      params.transactDateEndAt = end;
      delete params.transactTime;
    } else {
      delete params.transactTime;
    }

    if (_search.tradeDate && _search.tradeDate?.[0] && _search.tradeDate?.[1]) {
      const start = dayjs(_search.tradeDate?.[0]).format("YYYY-MM-DD");
      const end = dayjs(_search.tradeDate?.[1]).format("YYYY-MM-DD");
      params.tradeDateStartAt = start;
      params.tradeDateEndAt = end;
      delete params.tradeDate;
    } else {
      delete params.tradeDate;
    }

    // sort
    if (state.sortField) {
      params.sortCriteria = {
        field: state.sortField,
        isAsc: state.sortOrder === 1,
      };
    }

    postExecutionSearch(params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        dispatch({
          type: "onDataChange",
          payload: {
            dataList: res?.data?.executions ?? [],
            total: res?.data?.totalCount,
          },
        });
      }
    });
  };

  const formatTime = (rowData: any) => {
    return rowData.transactTime
      ? dayjs(rowData?.transactTime).format("YYYY-MM-DD")
      : "";
  };

  const handlePageChange = (params: any) => {
    dispatch({ type: "onPageChange", payload: params });
  };

  const handleDetail = (row: any) => {
    setDetailId(row?.internalId);
    setDetail(row);
    setSidebarVisible(true);
  };

  useEffect(() => {
    getTableList();
  }, [
    state.pageNumber,
    state.pageSize,
    state.sortField,
    state.sortOrder,
    state.searchParams,
  ]);

  return (
    <>
      <div className="main-card-light">
        {search ? (
          <div className="main-title-container mb-8">
            <p className="main-title">EXECUTION LIST</p>
            <Link href={"/execution/create"}>
              <Button rounded label="Create Execution" severity="help" />
            </Link>
          </div>
        ) : null}

        {search ? (
          <div className="search-form-container mb-8">
            <InputText
              placeholder="Exec ID"
              onChange={(e: any) => {
                dispatch({
                  type: "onSearchChange",
                  payload: { searchContent: e.target?.value },
                });
              }}
            />
            <Calendar
              value={state.searchParams?.transactTime ?? []}
              placeholder="Transact Time"
              selectionMode="range"
              showButtonBar
              hideOnRangeSelection
              onChange={(e: any) => {
                dispatch({
                  type: "onSearchChange",
                  payload: { transactTime: e.value },
                });
              }}
            />
            <Calendar
              value={state.searchParams?.tradeDate ?? []}
              placeholder="Trade Time"
              selectionMode="range"
              showButtonBar
              hideOnRangeSelection
              onChange={(e: any) => {
                dispatch({
                  type: "onSearchChange",
                  payload: { tradeDate: e.value },
                });
              }}
            />
            <Dropdown
              showClear
              value={state.searchParams?.side}
              placeholder="Side"
              optionLabel="name"
              optionValue="code"
              options={SIDE_OPTIONS}
              onChange={(e: any) => {
                dispatch({
                  type: "onSearchChange",
                  payload: { side: e.value },
                });
              }}
            />
          </div>
        ) : null}

        <Table
          dataList={state.dataList}
          totalNum={state.total}
          onPageChange={handlePageChange}
          empty={
            <>
              <p>No Execution Right Now</p>
              <p>Please Create New Execution or Switch Account Nickname</p>
            </>
          }
          sortField={state.sortField}
          sortOrder={state.sortOrder}
          onSort={(event: any) => {
            dispatch({ type: "onSortChange", payload: event });
          }}
          dataKey="internalId"
          selectionMode="single"
          onRowClick={(event: any) => {
            const { data } = event;
            handleDetail(data);
          }}
        >
          <Column
            field="execId"
            header="Exec ID"
            sortable
            style={{ minWidth: "8rem" }}
          ></Column>
          <Column
            field="execType"
            header="Exec Type"
            style={{ minWidth: "8rem" }}
          ></Column>
          <Column
            field="orderId"
            header="Order ID"
            style={{ minWidth: "8rem" }}
          ></Column>
          <Column field="side" header="Side"></Column>
          <Column
            field="avgPrice"
            header="Avg Price"
            style={{ minWidth: "8rem" }}
          ></Column>
          <Column
            field="lastQty"
            header="Last Qty"
            style={{ minWidth: "8rem" }}
          ></Column>
          <Column
            field="transactTime"
            header="Transact Time"
            sortable
            body={formatTime}
            style={{ minWidth: "11rem" }}
          ></Column>
          <Column
            field="tradeDate"
            header="Trade Date"
            sortable
            style={{ minWidth: "9rem" }}
          ></Column>
          <Column
            field="executionStatus"
            header="Execution Status"
            style={{ minWidth: "11rem" }}
          ></Column>
        </Table>
      </div>

      <ExecDetailSidebar
        id={detailId}
        data={detail}
        visible={sidebarVisible}
        onHide={() => setSidebarVisible(false)}
      />
    </>
  );
}
