"use client";

import { useEffect } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { postAuthToken } from "@/api/auth";
import { getRedirectUri, removeRedirectUri, setToken } from "@/utils/auth";

/** OIDC login callback page (reworked to Auth.js BFF in Phase 10 - ADR-0010) */
const CallbackPage = () => {
  const router = useRouter();

  const requestToken = (params: any) => {
    postAuthToken({
      code: params["code"],
      state: params["state"],
      orgId: params["org_id"],
      redirectUri: window.location?.origin + "/callback",
    }).then((res) => {
      if (res?.serviceStatus?.success) {
        const token = res?.data?.accessToken;
        setToken(token);
        const url = getRedirectUri();
        url && router.push(url);
        removeRedirectUri();
      }
    });
  };

  useEffect(() => {
    const paramsSearch = new URLSearchParams(window.location.search);
    const params = Object.fromEntries(paramsSearch.entries());
    requestToken(params);
  }, []);

  return (
    <div className="flex items-center justify-center h-screen bg-white">
      <Image src="/loading.gif" width={350} height={350} alt="loading" />
    </div>
  );
};

export default CallbackPage;
