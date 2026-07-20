import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Reference Data · Trading Capture System",
};

export default function ReferenceLayout({ children }: { children: React.ReactNode }) {
  return children;
}
