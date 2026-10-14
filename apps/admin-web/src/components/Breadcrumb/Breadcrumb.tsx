"use client";
import { BreadCrumb } from "primereact/breadcrumb";
import type { MenuItem } from "primereact/menuitem";
import { usePathname } from "next/navigation";
import { useMemo } from "react";

const routerNameMap: Record<string, string> = {
  "/dashboard/": "Dashboard",
  "/orders/": "Orders",
  "/orders/new/": "New Order",
  "/executions/": "Executions",
  "/executions/new/": "Capture Execution",
  "/accounts/": "Accounts",
  "/accounts/new/": "New Account",
  "/reference/": "Reference Data",
  "/403/": "",
};

export default function Breadcrumb() {
  const pathname = usePathname();
  const model = useMemo<MenuItem[]>(() => {
    const names = pathname.split("/").filter(Boolean);
    return names.map((segment, index) => {
      let path = "/";
      for (let i = 0; i <= index; i++) {
        path += `${names[i]}/`;
      }
      return {
        url: path,
        // Unknown segments are record ids (ACC-SEED-INV1, ORD-<uuid>): show them verbatim.
        label: routerNameMap[path] ?? decodeURIComponent(segment),
      };
    });
  }, [pathname]);

  return (
    <>
      <BreadCrumb model={model} />
    </>
  );
}
