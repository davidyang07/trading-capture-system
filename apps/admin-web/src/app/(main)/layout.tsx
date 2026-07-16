import { Header } from "@/components/Layout/Header";
import { Nav } from "@/components/Layout/Nav";
import Breadcrumb from "@/components/Breadcrumb/Breadcrumb";

export default function MainLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div>
      <Header />
      <div className="main-container">
        <Nav />
        <div className="main-right">
          <Breadcrumb />
          {children}
        </div>
      </div>
    </div>
  );
}
