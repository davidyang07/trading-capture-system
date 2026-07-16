"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { InputNumber } from "primereact/inputnumber";
import { FormLabel, errMsg, itemClass } from "@/components/Form/Form";
import { ORDER_STATUS_OPTIONS } from "@/utils/const";
import { getCurrencyOptions } from "@/api/common";
import { postExecutionCreate } from "@/api/execution";
import SearchDropdown from "@/components/Form/SearchDropdown";
import { postOrderSearch } from "@/api/order";
import { useToast } from "@/components/Toast/ToastProvider";
import { Field, Form } from "react-final-form";

export default function CreateExecutionPage() {
  const router = useRouter();
  const { successToast } = useToast();

  // form state
  const initVal = {
    execType: "Trade",
    lastPrice: 0,
    avgPrice: 0,
    lastQty: 0,
    cumQty: 0,
    leavesQty: 0,
  };

  // Options Data List
  const [currencyOpts, setCurrencyOpts] = useState<Array<string>>([]);

  // Order Search
  const [orderId, setOrderId] = useState<any>(null);
  const handleSearch = (value: string) => {
    return new Promise((resolve, reject) => {
      postOrderSearch({
        searchContent: value?.trim?.(),
      })
        .then((res: any) => {
          if (res?.serviceStatus?.success) {
            const match = res?.data?.orders ?? [];
            resolve(match);
          } else {
            reject([]);
          }
        })
        .catch(() => {
          resolve([]);
        });
    });
  };

  const onSubmit = (data: any, form: any) => {
    const _data = { ...data };
    orderId && (_data.orderId = orderId);
    postExecutionCreate(_data).then((res: any) => {
      if (res?.serviceStatus?.success) {
        successToast();
        router.push("/execution");
      }
    });
  };

  // form validate
  const validate = (data: any) => {
    let errors: { [k: string]: string } = {};
    const required = ["execId", "execType", "orderStatus", "currency"];
    required.forEach((item) => !data[item] && (errors[item] = "required"));
    ["lastPrice", "avgPrice", "lastQty", "cumQty", "leavesQty"].forEach(
      (item) => {
        (isNaN(data[item]) ||
          data[item] === null ||
          data[item] === undefined) &&
          (errors[item] = "required");
      }
    );
    return errors;
  };

  const getCurrencyOpts = () => {
    getCurrencyOptions().then((res: any) => {
      if (res?.serviceStatus?.success) {
        const opts = res?.data?.map((item: string) => ({
          name: item,
          code: item,
        }));
        setCurrencyOpts(opts);
      }
    });
  };

  useEffect(() => {
    getCurrencyOpts();
  }, []);

  return (
    <>
      <div className="main-card-light">
        <div className="main-title-container no-border mb-8">
          <p className="main-title">Create Execution</p>
        </div>

        <Form
          onSubmit={onSubmit}
          validate={validate}
          initialValues={initVal}
          render={({ handleSubmit }: any) => {
            return (
              <form
                id="myForm"
                className="tcs-form mx-3.5"
                onSubmit={handleSubmit}
              >
                <SearchDropdown
                  showClear
                  value={orderId}
                  optionLabel="orderId"
                  optionValue="orderId"
                  label="Order ID"
                  searchFn={(val: string) => handleSearch(val)}
                  onChange={(e: any) => setOrderId(e.value)}
                />
                <Field
                  name="execId"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Exec ID" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="execType"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Exec Type" />
                      <InputText {...input} disabled={true} variant="filled" />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="orderStatus"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Order Status" />
                      <Dropdown
                        {...input}
                        options={ORDER_STATUS_OPTIONS}
                        optionLabel="name"
                        optionValue="code"
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="lastPrice"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Last Price" />
                      <InputNumber
                        {...input}
                        minFractionDigits={2}
                        maxFractionDigits={2}
                        onChange={(e: any) => input.onChange(e?.value)}
                        onBlur={(e: any) => input.onBlur(e?.value)}
                        onFocus={(e: any) => input.onFocus(e?.value)}
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="avgPrice"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Avg Price" />
                      <InputNumber
                        {...input}
                        minFractionDigits={2}
                        maxFractionDigits={2}
                        onChange={(e: any) => input.onChange(e?.value)}
                        onBlur={(e: any) => input.onBlur(e?.value)}
                        onFocus={(e: any) => input.onFocus(e?.value)}
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="lastQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Last Qty" />
                      <InputNumber
                        {...input}
                        onChange={(e: any) => input.onChange(e?.value)}
                        onBlur={(e: any) => input.onBlur(e?.value)}
                        onFocus={(e: any) => input.onFocus(e?.value)}
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="cumQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Cum Qty" />
                      <InputNumber
                        {...input}
                        onChange={(e: any) => input.onChange(e?.value)}
                        onBlur={(e: any) => input.onBlur(e?.value)}
                        onFocus={(e: any) => input.onFocus(e?.value)}
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="leavesQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Leaves Qty" />
                      <InputNumber
                        {...input}
                        onChange={(e: any) => input.onChange(e?.value)}
                        onBlur={(e: any) => input.onBlur(e?.value)}
                        onFocus={(e: any) => input.onFocus(e?.value)}
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="currency"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Currency" />
                      <Dropdown
                        {...input}
                        options={currencyOpts}
                        optionLabel="name"
                        optionValue="code"
                      />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="text"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Text" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
              </form>
            );
          }}
        />

        <div className="operate-footer">
          <Button
            rounded
            label="Cancel"
            severity="secondary"
            onClick={() => {
              router.push("/execution");
            }}
          />
          <Button
            rounded
            type="submit"
            form="myForm"
            label="Create"
            severity="help"
          />
        </div>
      </div>
    </>
  );
}
