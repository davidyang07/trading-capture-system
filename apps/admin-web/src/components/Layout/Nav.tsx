"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { get, map } from "lodash-es";
import { getRoutes } from "@/routes";
import "./Nav.scss";

export const Nav = () => {
  const router = useRouter();
  const pathname = usePathname();
  const routes = getRoutes();
  const [currP, setCurrP] = useState<string>(get(routes?.[0], "url", "/"));

  const jump = (e: any, url: string) => {
    setCurrP(url);
    router.push(url);
  };

  const activeClass = (url: string) => {
    return currP.indexOf(url) !== -1 ? "active" : "";
  };

  useEffect(() => {
    if (pathname === "/") {
      const url = get(routes?.[0], "url", "");
      url && setCurrP(url);
      url && router.replace(url);
    } else {
      setCurrP(pathname);
    }
  }, [pathname]);

  return (
    <div className="layout-navbar">
      <div className="layout-nav-list">
        {map(routes, (item: any) => {
          return (
            <div
              key={item.url}
              className={`layout-nav-item ${activeClass(item.url)}`}
              onClick={(e) => jump(e, item.url)}
            >
              <i className={item.icon} />
              <span>{item.name}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
};
