"use client";

import { useRef } from "react";
import { OverlayPanel } from "primereact/overlaypanel";
import "./TableAction.scss";

export default function TableAction({ children }: any) {
  const opRef = useRef<any>(null);

  return (
    <>
      <i
        className="pi pi-ellipsis-v cursor-pointer"
        onClick={(e: any) => {
          e.stopPropagation();
          e.preventDefault();
          opRef?.current?.toggle?.(e);
        }}
      />
      <OverlayPanel ref={opRef} className="action-laypanel cursor-pointer">
        {children}
      </OverlayPanel>
    </>
  );
}
