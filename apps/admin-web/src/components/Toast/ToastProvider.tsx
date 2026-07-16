"use client";

import { createContext, useContext, useEffect, useRef } from "react";
import { Toast, ToastMessage } from "primereact/toast";

export interface IToastProvider {
  showToast: (props?: ToastMessage | ToastMessage[]) => void;
  successToast: (props?: ToastMessage | ToastMessage[]) => void;
  errorToast: (props?: ToastMessage | ToastMessage[]) => void;
  warningToast: (props?: ToastMessage | ToastMessage[]) => void;
}

export const ToastContext = createContext<IToastProvider>({
  showToast: () => {},
  successToast: () => {},
  errorToast: () => {},
  warningToast: () => {},
});

export const ToastProvider = (props?: any) => {
  const toastRef = useRef<Toast>(null);
  const showToast = (props?: ToastMessage | ToastMessage[]) => {
    toastRef.current?.show(props ?? {});
  };
  const successToast = (props?: ToastMessage | ToastMessage[]) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "success",
      summary: "Success",
      life: 2000,
    });
  };
  const errorToast = (props?: ToastMessage | ToastMessage[]) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "error",
      summary: "Error",
      life: 2000,
    });
  };
  const warningToast = (props?: ToastMessage | ToastMessage[]) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "warn",
      summary: "Warn",
      life: 2000,
    });
  };

  useEffect(() => {
    if (!(window as any).$message) {
      (window as any).$message = {
        error: errorToast,
        success: successToast,
        warning: warningToast,
      };
    }
  }, []);

  return (
    <ToastContext.Provider
      value={{
        showToast,
        successToast,
        errorToast,
        warningToast,
      }}
    >
      <Toast ref={toastRef} />
      {props.children}
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  return useContext(ToastContext);
};
