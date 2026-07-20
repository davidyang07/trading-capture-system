"use client";

import type { ReactNode } from "react";
import type { Session } from "next-auth";
import { SessionProvider } from "next-auth/react";
import { PrimeReactProvider } from "primereact/api";
import { ToastProvider } from "@/components/Toast/ToastProvider";

export function Providers({
  children,
  session,
}: {
  children: ReactNode;
  session: Session | null;
}) {
  return (
    <SessionProvider session={session}>
      <PrimeReactProvider>
        <ToastProvider>{children}</ToastProvider>
      </PrimeReactProvider>
    </SessionProvider>
  );
}
