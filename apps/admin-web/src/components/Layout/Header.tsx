"use client";

import Image from "next/image";
import { Button } from "primereact/button";
import { useSession } from "next-auth/react";
import { isAdmin } from "@/lib/permissions";
import "./Header.scss";

export const Header = () => {
  const { data: session, status } = useSession();
  const username = session?.user?.username ?? session?.user?.name ?? "";
  const role = isAdmin(session ?? null) ? "Administrator" : "Investor";

  const handleLogout = () => {
    // Server-side federated logout clears the cookie and ends the Keycloak session.
    // A full-page navigation also drops all in-memory, user-scoped state.
    window.location.assign("/api/auth/logout");
  };

  return (
    <header className="layout-top-bar">
      <div className="layout-topbar-logo">
        <Image
          src="/logo.png"
          alt=""
          className="logo-img"
          width={32}
          height={32}
          priority
        />
        <div className="layout-topbar-desc">
          <p className="layout-topbar-title">Trading Capture System</p>
          <p className="layout-topbar-subtitle">
            <span>Distributed Trading Platform</span>
          </p>
        </div>
      </div>

      <div className="layout-topbar-menu">
        {status === "authenticated" && username ? (
          <>
            <Image
              src="/avatar.png"
              alt=""
              className="avatar-img"
              width={28}
              height={28}
              priority
            />
            <span className="layout-topbar-user">
              {username}
              <span className="layout-topbar-role"> · {role}</span>
            </span>
            <Button
              label="Log out"
              outlined
              size="small"
              onClick={handleLogout}
            />
          </>
        ) : null}
      </div>
    </header>
  );
};
