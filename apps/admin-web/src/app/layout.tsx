import type { Metadata } from "next";
import { cookies } from "next/headers";
import { auth } from "@/auth";
import { SESSION_ENDED_COOKIE } from "@/auth/session-end";
import { Providers } from "@/app/providers";
import "primereact/resources/themes/lara-light-indigo/theme.css";
import "primeicons/primeicons.css";
import "@/style/global.scss";
import "@/style/primereact.scss";
import "@/style/app.scss";
import "@/style/landing.scss";

export const metadata: Metadata = {
  title: "Trading Capture System",
  description: "Distributed trading capture platform — orders, executions, accounts.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // A session cookie that outlived a logout is not handed to client components.
  const ended = (await cookies()).has(SESSION_ENDED_COOKIE);
  const session = ended ? null : await auth();
  return (
    <html lang="en">
      <body>
        <Providers session={session}>{children}</Providers>
      </body>
    </html>
  );
}
