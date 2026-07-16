"use client";
import { BreadCrumb } from "primereact/breadcrumb";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { get } from "lodash-es";
import { includes } from "lodash-es";

const routerNameMap = {
  "/dashboard/": "Dashboard",
  "/execution/": "Execution Management",
  "/execution/create/": "Create Execution",
  "/accounts/": "Accounts Management",
  "/users/": "Users Management",
  "/403/": "",
};

export default function Breadcrumb() {
  const pathname = usePathname();
  const [metaModal, setMetaModal] = useState<any>([]);

  useEffect(() => {
    const names = pathname.split("/").filter((p: any) => p);

    const isFromSidebar =
      names.length > 0 &&
      (includes(names, "cancellation") || includes(names, "correction"));
    isFromSidebar && names.splice(names.length - 1, 1);

    const result = names.map((str: string, index: number) => {
      let path = "/";
      for (let i = 0; i <= index; i++) {
        path += `${names[i]}/`;
      }
      return {
        url: path,
        label: get(routerNameMap, path, str),
        disabled: isFromSidebar && index === names.length - 1,
      };
    });
    setMetaModal(result);
  }, [pathname]);

  return (
    <>
      <BreadCrumb
        model={metaModal}
        style={{
          color: "#4B5563",
          border: "none",
          fontSize: "14px",
          backgroundColor: "transparent",
          height: "50px",
        }}
      />
    </>
  );
}
