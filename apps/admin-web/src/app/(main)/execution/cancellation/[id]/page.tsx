"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";
import { Dropdown } from "primereact/dropdown";
import { InputText } from "primereact/inputtext";
import { ORDER_STATUS_OPTIONS } from "@/utils/const";
import { FormLabel, errMsg, itemClass } from "@/components/Form/Form";
import { postExecutionCancel } from "@/api/execution";
import { useToast } from "@/components/Toast/ToastProvider";
import { Field, Form } from "react-final-form";

export default function CancellationExecutionPage({ params }: any) {
  const router = useRouter();
  const { successToast } = useToast();

  // form state
  const [init, setInit] = useState<any>({
    execType: "Trade Cancel",
  });

  const onSubmit = (data: any, form: any) => {
    const _data = { ...data };
    postExecutionCancel(_data).then((res: any) => {
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
      "orderStatus",
      "avgPrice",
      "lastQty",
      "cumQty",
      "leavesQty",
    ];
    required.forEach((item) => !data[item] && (errors[item] = "required"));
    if (data.execId && data.execId.length > 255) {
      errors.execId = "no longer than 255 characters";
    }
    return errors;
  };

  useEffect(() => {
    setInit({ ...init, execRefId: params.id });
  }, []);

  return (
    <>
      <div className="main-card-light">
        <div className="main-title-container no-border mb-8">
          <p className="main-title">Execution Cancellation</p>
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
                  name="avgPrice"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Avg Price" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="lastQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Last Qty" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="cumQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Cum Qty" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <Field
                  name="leavesQty"
                  render={({ input, meta }: any) => (
                    <div className={itemClass(meta)}>
                      <FormLabel required label="Leaves Qty" />
                      <InputText {...input} />
                      {errMsg(meta)}
                    </div>
                  )}
                />
                <div className="operate-footer">
                  <Button
                    rounded
                    label="Cancel"
                    severity="secondary"
                    onClick={() => router.back()}
                  />
                  <Button rounded type="submit" label="Save" severity="help" />
                </div>
              </form>
            );
          }}
        />
      </div>
    </>
  );
}
