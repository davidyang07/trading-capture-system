import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { PrimeReactProvider } from "primereact/api";
import { ToastProvider } from "@/components/Toast/ToastProvider";
import { StoreProvider } from "@/store/StoreProvider";
import "primereact/resources/themes/lara-light-cyan/theme.css";
import "primeicons/primeicons.css";
import "@/style/global.scss";
import "@/style/primereact.scss";
import "@/style/form.scss";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Trading Capturing System",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <StoreProvider>
          <PrimeReactProvider>
            <ToastProvider>
              {children}
            </ToastProvider>
          </PrimeReactProvider>
        </StoreProvider>
      </body>
    </html>
  );
}
