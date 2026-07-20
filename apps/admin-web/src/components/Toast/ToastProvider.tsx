"use client";

import { createContext, useContext, useRef, type PropsWithChildren } from "react";
import { Toast, ToastMessage } from "primereact/toast";

export interface IToastProvider {
  showToast: (props?: ToastMessage | ToastMessage[]) => void;
  successToast: (props?: ToastMessage) => void;
  errorToast: (props?: ToastMessage) => void;
  warningToast: (props?: ToastMessage) => void;
}

export const ToastContext = createContext<IToastProvider>({
  showToast: () => {},
  successToast: () => {},
  errorToast: () => {},
  warningToast: () => {},
});

export const ToastProvider = ({ children }: PropsWithChildren) => {
  const toastRef = useRef<Toast>(null);
  const showToast = (props?: ToastMessage | ToastMessage[]) => {
    toastRef.current?.show(props ?? {});
  };
  const successToast = (props?: ToastMessage) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "success",
      summary: "Success",
      life: 2000,
    });
  };
  const errorToast = (props?: ToastMessage) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "error",
      summary: "Error",
      life: 2000,
    });
  };
  const warningToast = (props?: ToastMessage) => {
    toastRef.current?.show({
      ...(props ?? {}),
      severity: "warn",
      summary: "Warn",
      life: 2000,
    });
  };

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
      {children}
    </ToastContext.Provider>
  );
};

export const useToast = () => {
  return useContext(ToastContext);
};
