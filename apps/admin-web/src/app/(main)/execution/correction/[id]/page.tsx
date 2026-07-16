"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Dropdown } from "primereact/dropdown";
import { InputNumber } from "primereact/inputnumber";
import { FormLabel, errMsg, itemClass } from "@/components/Form/Form";
import { ORDER_STATUS_CANCEL_CORR_OPTIONS } from "@/utils/const";
import { postExecutionCorrect } from "@/api/execution";
import { useToast } from "@/components/Toast/ToastProvider";
import { Field, Form } from "react-final-form";
import { getCurrencyOptions } from "@/api/common";

export default function CorrectionExecutionPage({ params }: any) {
  const router = useRouter();
  const { successToast } = useToast();

  // form state
  const [init, setInit] = useState<any>({ execType: "Trade Correct" });

  // Options Data List
  const [currencyOpts, setCurrencyOpts] = useState<Array<string>>([]);

  const onSubmit = (data: any, form: any) => {
    const _data = { ...data };
    postExecutionCorrect(_data).then((res: any) => {
      if (res?.serviceStatus?.success) {
        successToast();
        router.back();
      }
    });
  };

  // form validate
  const validate = (data: any) => {
    let errors: { [k: string]: string } = {};
    const required = [
      "execId",
      "execType",
      "execRefId",
      "ordStatus",
      "lastPrice",
      "avgPrice",
      "lastQty",
      "cumQty",
      "leavesQty",
      "currency",
    ];
    required.forEach((item) => !data[item] && (errors[item] = "required"));
    if (data.execId && data.execId.length > 255) {
      errors.execId = "no longer than 255 characters";
    }
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
    setInit({ ...init, execRefId: params?.id });
  }, []);

  return (
    <>
      <div className="main-card-light">
        <div className="main-title-container no-border mb-8">
          <p className="main-title">Execution Correction</p>
        </div>

        <Form
          onSubmit={onSubmit}
          validate={validate}
          initialValues={init}
          render={({ handleSubmit }: any) => {
            return (
              <form
                id="myForm"
                className="tcs-form mx-3.5"
                onSubmit={handleSubmit}
              >
                <Field
                  name="execId"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Exec ID" />
                      <InputText {...input} maxLength={255} />
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
                  name="execRefId"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Exec Ref ID" />
                      <InputText {...input} disabled={true} variant="filled" />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="ordStatus"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Order Status" />
                      <Dropdown
                        {...input}
                        options={ORDER_STATUS_CANCEL_CORR_OPTIONS}
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
                      <FormLabel label="Text" />
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
            onClick={() => router.back()}
          />
          <Button
            rounded
            type="submit"
            form="myForm"
            label="Save"
            severity="help"
          />
        </div>
      </div>
    </>
  );
}
