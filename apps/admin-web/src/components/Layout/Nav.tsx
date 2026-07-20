"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "next-auth/react";
import { routesForRole } from "@/routes";
import { isAdmin } from "@/lib/permissions";
import "./Nav.scss";

export const Nav = () => {
  const pathname = usePathname();
  const { data: session } = useSession();
  const routes = routesForRole(isAdmin(session ?? null));

  const isActive = (url: string) =>
    pathname === url || pathname.startsWith(`${url}/`);

  return (
    <nav className="layout-navbar" aria-label="Primary">
      <ul className="layout-nav-list">
        {routes.map((item) => {
          const active = isActive(item.url);
          return (
            <li key={item.url}>
              <Link
                href={item.url}
                className={`layout-nav-item ${active ? "active" : ""}`}
                aria-current={active ? "page" : undefined}
              >
                <i className={item.icon} aria-hidden="true" />
                <span>{item.name}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
};
