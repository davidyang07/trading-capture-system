"use client";

import { useEffect, useReducer, useState } from "react";
import { Column } from "primereact/column";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Dropdown } from "primereact/dropdown";
import Table from "@/components/Table/Table";
import TableAction from "@/components/Table/TableAction";
import CreateAccDialog from "./CreateAccDialog";
import AccountDetailSidebar from "./DetailSidebar";
import { ACCOUNT_STATUS_OPTIONS, DEFAULT_PAGE_OPTIONS } from "@/utils/const";
import { patchAccountStatus, postAccountSearch } from "@/api/account";
import { useToast } from "@/components/Toast/ToastProvider";
import dayjs from "dayjs";

const initialState = {
  searchParams: {},
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
      return { ...state, dataList: payload.dataList, total: payload.total };
    default:
      throw new Error();
  }
};

export default function AccountsPage() {
  const { successToast } = useToast();
  const [accountId, setAccountId] = useState("");
  const [sidebarVisible, setSidebarVisible] = useState(false);
  const [accDialogVisible, setAccDialogVisible] = useState(false);
  const [secDialogVisible, setSecDialogVisible] = useState(false);
  const [cashDialogVisible, setCashDialogVisible] = useState(false);

  const [state, dispatch] = useReducer(
    reducer,
    initialState,
    (initialState: any) => initialState
  );

  const getTableList = () => {
    const params: any = state.searchParams ?? {};
    params.pageNumber = state.pageNumber;
    params.pageSize = state.pageSize;
    // sort
    if (state.sortField) {
      params.sortCriteria = {
        field: state.sortField,
        isAsc: state.sortOrder === 1,
      };
    }
    postAccountSearch(params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        dispatch({
          type: "onDataChange",
          payload: {
            dataList: res?.data?.accounts ?? [],
            total: res?.data?.totalFound,
          },
        });
      }
    });
  };

  const formatTime = (rowData: any) => {
    return rowData.createdDate
      ? dayjs(rowData?.createdDate).format("YYYY-MM-DD")
      : "";
  };

  const handlePageChange = (params: any) => {
    dispatch({ type: "onPageChange", payload: params });
  };

  const handleDetail = (row: any) => {
    setAccountId(row?.accountId);
    setSidebarVisible(true);
  };

  const handleSuspended = (rowData: any) => {
    patchAccountStatus(rowData.accountId, {
      status: rowData.accountStatus === "Active" ? "Suspended" : "Active",
    }).then((res: any) => {
      if (res?.serviceStatus?.success) {
        successToast();
        getTableList();
      }
    });
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
        <div className="main-title-container mb-8">
          <p className="main-title">ACCOUNT LIST</p>
          <Button
            label="Create Account"
            severity="help"
            rounded
            onClick={() => setAccDialogVisible(true)}
          />
        </div>

        <div className="search-form-container mb-8">
          <InputText
            placeholder="Account Nickname"
            onChange={(e: any) => {
              dispatch({
                type: "onSearchChange",
                payload: { searchContent: e.target?.value },
              });
            }}
          />
          <Dropdown
            showClear
            value={state.searchParams?.accountStatus}
            optionLabel="name"
            optionValue="code"
            placeholder="Account Status"
            options={ACCOUNT_STATUS_OPTIONS}
            onChange={(e: any) => {
              dispatch({
                type: "onSearchChange",
                payload: { accountStatus: e.target?.value },
              });
            }}
          />
        </div>

        <Table
          dataList={state.dataList}
          totalNum={state.total}
          onPageChange={handlePageChange}
          empty={<p>No Account Right Now</p>}
          sortField={state.sortField}
          sortOrder={state.sortOrder}
          onSort={(event: any) => {
            dispatch({ type: "onSortChange", payload: event });
          }}
          dataKey="accountId"
          selectionMode="single"
        >
          <Column
            field="accountId"
            header="Account ID"
            sortable
            style={{ minWidth: "9rem" }}
          ></Column>
          <Column
            field="accountNumber"
            header="Account Number"
            sortable
            style={{ minWidth: "12rem" }}
          ></Column>
          <Column
            field="nickname"
            header="Account Nickname"
            sortable
            style={{ minWidth: "13rem" }}
          ></Column>
          <Column
            field="userFullName"
            header="User's Full Name"
            style={{ minWidth: "11rem" }}
          ></Column>
          <Column
            field="userEmail"
            header="User's Email"
            style={{ minWidth: "9rem" }}
          ></Column>
          <Column
            field="accountStatus"
            header="Account Status"
            style={{ minWidth: "10rem" }}
          ></Column>
          <Column
            field="createdDate"
            header="Created Date"
            sortable
            body={formatTime}
            style={{ minWidth: "11rem" }}
          ></Column>
          <Column
            header="Action"
            bodyStyle={{ textAlign: "center" }}
            body={(rowData: any) => {
              return (
                <TableAction>
                  <div onClick={() => handleSuspended(rowData)}>
                    {rowData.accountStatus === "Active"
                      ? "Suspended"
                      : "Active"}
                  </div>
                  <div onClick={() => handleDetail(rowData)}>View Detail</div>
                </TableAction>
              );
            }}
          />
        </Table>
      </div>

      <AccountDetailSidebar
        id={accountId}
        visible={sidebarVisible}
        onHide={() => setSidebarVisible(false)}
      />

      <CreateAccDialog
        visible={accDialogVisible}
        onClose={() => {
          setAccDialogVisible(false);
          getTableList();
        }}
      />
    </>
  );
}
