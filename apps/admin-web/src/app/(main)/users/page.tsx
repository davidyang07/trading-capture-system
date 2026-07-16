"use client";

import { useEffect, useReducer } from "react";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import Table from "@/components/Table/Table";
import { postUserSearch } from "@/api/user";
import { DEFAULT_PAGE_OPTIONS } from "@/utils/const";
import { isEqual } from "lodash-es";

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
      const last = state.searchParams;
      // avoid triggering duplicate requests due to repeated changes.
      if (!isEqual(last, payload)) {
        return { ...state, searchParams: { ...payload } };
      } else {
        return state;
      }
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

export default function UsersPage() {
  const [state, dispatch] = useReducer(
    reducer,
    initialState,
    (initialState: any) => initialState
  );

  const getTableList = () => {
    const params: any = { ...(state.searchParams ?? {}) };
    params.pageNumber = state.pageNumber;
    params.pageSize = state.pageSize;
    if (state.sortField) {
      params.sortCriteria = [
        {
          field: "firstName",
          isAsc: state.sortOrder === 1,
        },
        {
          field: "lastName",
          isAsc: state.sortOrder === 1,
        },
      ];
    }
    postUserSearch(params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        dispatch({
          type: "onDataChange",
          payload: {
            dataList: res?.data?.users ?? [],
            total: res?.data?.totalFound,
          },
        });
      }
    });
  };

  const handlePageChange = (params: any) => {
    dispatch({ type: "onPageChange", payload: params });
  };

  const formatName = (rowData: any) => {
    return `${rowData.firstName} ${rowData.lastName}`;
  };

  const formatRole = (rowData: any) => {
    return rowData?.roles ?? [].join("、");
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
          <p className="main-title">User LIST</p>
        </div>
        <div className="search-form-container mb-8">
          <InputText
            placeholder="name or E-mail"
            onChange={(e) => {
              dispatch({
                type: "onSearchChange",
                payload: { searchContent: e.target?.value },
              });
            }}
          />
        </div>

        <Table
          dataList={state.dataList}
          totalNum={state.total}
          onPageChange={handlePageChange}
          empty={<p>No User Right Now</p>}
          sortField={state.sortField}
          sortOrder={state.sortOrder}
          onSort={(event: any) => {
            dispatch({ type: "onSortChange", payload: event });
          }}
        >
          <Column
            field="firstName"
            header="Name"
            body={formatName}
            sortable
          ></Column>
          <Column field="email" header="E-mail"></Column>
          <Column header="Role" body={formatRole}></Column>
        </Table>
      </div>
    </>
  );
}
