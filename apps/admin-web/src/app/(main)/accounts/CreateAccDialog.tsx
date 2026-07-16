import { Button } from "primereact/button";
import { Dialog } from "primereact/dialog";
import { InputText } from "primereact/inputtext";
import { FormLabel, errMsg, itemClass, valid } from "@/components/Form/Form";
import { postAccountAdd } from "@/api/account";
import { isFunction } from "lodash-es";
import SearchDropdown from "@/components/Form/SearchDropdown";
import { useState } from "react";
import { postUserSearch } from "@/api/user";
import { useToast } from "@/components/Toast/ToastProvider";
import { Field, Form } from "react-final-form";

interface ICreateAccDialogProps {
  visible: boolean;
  onClose: () => void;
}

export default function CreateAccDialog(props: ICreateAccDialogProps) {
  const { successToast } = useToast();
  // User Search
  const [userId, setUserId] = useState<any>(null);

  const handleSearch = (value: string) => {
    return new Promise((resolve, reject) => {
      postUserSearch({
        searchContent: value?.trim?.(),
      })
        .then((res: any) => {
          if (res?.serviceStatus?.success) {
            const match = res?.data?.users ?? [];
            match.forEach((item: any) => {
              item.label = `${item.firstName} ${item.lastName} (${item.email})`;
            });
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
    const params: any = { ...data };
    params.userId = userId;
    postAccountAdd(params).then((res: any) => {
      if (res?.serviceStatus?.success) {
        successToast();
        isFunction(props.onClose) && props.onClose();
      }
    });
  };

  const validate = (data: any) => {
    let errors: { [k: string]: string } = {};
    const required = ["nickname"];
    required.forEach((item) => !data[item] && (errors[item] = "required"));
    return errors;
  };

  return (
    <Dialog
      visible={props.visible}
      style={{ width: "60vw" }}
      onHide={props.onClose}
      header={
        <div className="main-title-container no-border p-0">
          <p className="main-title">Create Account</p>
        </div>
      }
    >
      <Form
        onSubmit={onSubmit}
        validate={validate}
        render={({ handleSubmit }: any) => {
          return (
            <form id="myForm" className="tcs-form" onSubmit={handleSubmit}>
              <SearchDropdown
                showClear
                required
                value={userId}
                optionLabel="label"
                optionValue="userId"
                label="Select User"
                searchFn={(val: string) => handleSearch(val)}
                onChange={(e: any) => setUserId(e.value)}
              />
              <Field
                name="nickname"
                render={({ input, meta }: any) => (
                  <div className={itemClass(meta)}>
                    <FormLabel required label="Nickname" />
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
          onClick={props.onClose}
        />
        <Button
          rounded
          label="Create"
          severity="help"
          type="submit"
          form="myForm"
        />
      </div>
    </Dialog>
  );
}
