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
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>
      <Header />
      <div className="main-container">
        <Nav />
        <main id="main-content" className="main-right" tabIndex={-1}>
          <Breadcrumb />
          {children}
        </main>
      </div>
    </div>
  );
}
