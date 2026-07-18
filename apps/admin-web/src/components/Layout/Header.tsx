"use client";

import { useEffect } from "react";
import Image from "next/image";
import { Button } from "primereact/button";
import { useAppDispatch, useAppSelector } from "@/store/hooks";
import {
  selectUserInfo,
  updateUserInfo,
  clearUserInfo,
} from "@/store/user/userSlice";
import { getCurrent } from "@/api/user";
import { postLogout } from "@/api/auth";
import { removeToken, setRedirectUri } from "@/utils/auth";
import "./Header.scss";

export const Header = () => {
  const dispatch = useAppDispatch();
  const currentUser = useAppSelector(selectUserInfo);

  const handleLogout = () => {
    postLogout().then((res) => {
      if (res?.serviceStatus?.success) {
        removeToken();
        dispatch(clearUserInfo());
        if (res.data) {
          setRedirectUri(window.location.href);
          window.location.href = res.data;
        }
      }
    });
  };

  const getCurrentUser = () => {
    getCurrent().then((res) => {
      if (res?.serviceStatus?.success) {
        dispatch(updateUserInfo(res.data ?? {}));
      }
    });
  };

  useEffect(() => {
    getCurrentUser();
  }, []);

  return (
    <div className="layout-top-bar">
      <div className="layout-topbar-logo">
        <Image
          src={`/logo.png`}
          alt="Trading Capturing System Logo"
          className="logo-img"
          width={50}
          height={50}
          priority
        />
        <div className="layout-topbar-desc">
          <p className="layout-topbar-title">Trading Capturing System</p>
          <p className="layout-topbar-subtitle">
            <span>Distributed Trading Platform</span>
          </p>
        </div>
      </div>

      <div className="layout-topbar-menu">
        {currentUser?.userId && (
          <>
            <Image
              src={`/avatar.png`}
              alt="Trading Capturing System User Avatar"
              className="logo-img"
              width={31}
              height={31}
              priority
            />
            <span>Welcome, {`${currentUser?.firstName} ${currentUser.lastName}`}</span>
            <Button
              label="Log out"
              severity="help"
              rounded
              onClick={handleLogout}
            />
          </>
        )}
      </div>
    </div>
  );
};
