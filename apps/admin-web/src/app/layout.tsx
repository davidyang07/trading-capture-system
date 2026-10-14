import type { Metadata } from "next";
import { auth } from "@/auth";
import { Providers } from "@/app/providers";
import "primereact/resources/themes/lara-light-indigo/theme.css";
import "primeicons/primeicons.css";
import "@/style/global.scss";
import "@/style/primereact.scss";
import "@/style/app.scss";

export const metadata: Metadata = {
  title: "Trading Capture System",
  description: "Distributed trading capture platform — orders, executions, accounts.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await auth();
  return (
    <html lang="en">
      <body>
        <Providers session={session}>{children}</Providers>
      </body>
    </html>
  );
}
