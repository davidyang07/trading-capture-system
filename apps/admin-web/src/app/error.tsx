"use client";

import { Button } from "primereact/button";

export default function AppError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="login-page">
      <section className="login-card" role="alert" aria-labelledby="error-title">
        <i className="pi pi-exclamation-triangle" aria-hidden="true" />
        <h1 id="error-title">Something went wrong</h1>
        <p className="login-subtitle">
          The application could not display this page. No changes were submitted.
        </p>
        <Button label="Try again" icon="pi pi-refresh" onClick={reset} />
      </section>
    </main>
  );
}
