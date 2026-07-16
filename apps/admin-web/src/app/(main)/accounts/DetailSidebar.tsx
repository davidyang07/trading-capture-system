import { useEffect, useReducer } from "react";
import { Sidebar } from "primereact/sidebar";
import Table from "@/components/Table/Table";
import { Column } from "primereact/column";
import { DEFAULT_PAGE_OPTIONS } from "@/utils/const";
import { postCashSearch, postSecuritySearch } from "@/api/account";

interface IAccountDetailSidebarProps {
  id: string;
  visible: boolean;
  onHide: () => void;
}

const initialState = {
  cash: {
    dataList: [],
    total: 0,
    pageNumber: 0,
    pageSize: DEFAULT_PAGE_OPTIONS[0],
  },
  sec: {
    dataList: [],
    total: 0,
    pageNumber: 0,
    pageSize: DEFAULT_PAGE_OPTIONS[0],
  },
};

const reducer = (state: any, { type, payload }: any) => {
  const { pageNumber, pageSize, dataList, total } = payload;
  switch (type) {
    case "onCashPageChange":
      return { cash: { ...state.cash, pageNumber, pageSize }, sec: state.sec };
    case "onCashDataChange":
      return { cash: { ...state.cash, dataList, total }, sec: state.sec };
    case "onSecPageChange":
      return { cash: state.cash, sec: { ...state.sec, pageNumber, pageSize } };
    case "onSecDataChange":
      return { cash: state.cash, sec: { ...state.sec, dataList, total } };
    default:
      throw new Error();
  }
};

export default function AccountDetailSidebar(
  props: IAccountDetailSidebarProps
) {
  const [state, dispatch] = useReducer(
    reducer,
    initialState,
    (initialState: any) => initialState
  );

  const getCashTableList = async () => {
    const params: any = {};
    params.pageNumber = state.cash.pageNumber;
    params.pageSize = state.cash.pageSize;
    postCashSearch(props.id, params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        dispatch({
          type: "onCashDataChange",
          payload: {
            dataList: res?.data?.balances ?? [],
            total: res?.data?.totalFound,
          },
        });
      }
    });
  };

  const getSecTableList = async () => {
    const params: any = {};
    params.pageNumber = state.sec.pageNumber;
    params.pageSize = state.sec.pageSize;
    postSecuritySearch(props.id, params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        console.log(res?.data?.totalFound)
        dispatch({
          type: "onSecDataChange",
          payload: {
            dataList: res?.data?.balances ?? [],
            total: res?.data?.totalFound,
          },
        });
      }
    });
  };

  useEffect(() => {
    if (props.id) {
      getCashTableList();
      getSecTableList();
    }
  }, [props.id]);

  useEffect(() => {
    props.id && getCashTableList();
  }, [state.cash.pageNumber, state.cash.pageSize]);

  useEffect(() => {
    props.id && getSecTableList();
  }, [state.sec.pageNumber, state.sec.pageSize]);

  return (
    <>
      <Sidebar
        visible={props.visible}
        position="right"
        onHide={props.onHide}
        showCloseIcon={false}
        header={() => (
          <p className="main-title">
            <span>Account Detail</span>
            <span className="main-title-desc">{props.id}</span>
          </p>
        )}
      >
        <div className="main-card-dark mb-4">
          <p className="main-subtitle mb-3.5">Cash Balance</p>
          <Table
            dataList={state.cash.dataList}
            totalNum={state.cash.total}
            sizeOptions={undefined}
            onPageChange={(params: any) =>
              dispatch({ type: "onCashPageChange", payload: params })
            }
          >
            <Column field="currency" header="Currency"></Column>
            <Column field="amount" header="Amount"></Column>
          </Table>
        </div>

        <div className="main-card-dark mb-4">
          <p className="main-subtitle mb-3.5">Security Balance</p>
          <Table
            dataList={state.sec.dataList}
            totalNum={state.sec.total}
            sizeOptions={undefined}
            onPageChange={(params: any) =>
              dispatch({ type: "onSecPageChange", payload: params })
            }
          >
            <Column field="quantity" header="Quantity"></Column>
            <Column
              field="stock"
              header="Instrument name"
              style={{ minWidth: "12rem" }}
            ></Column>
          </Table>
        </div>
      </Sidebar>
    </>
  );
}
