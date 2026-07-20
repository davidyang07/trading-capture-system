import { LoadingState } from "@/components/feedback/States";

export default function AppLoading() {
  return (
    <main className="login-page" aria-label="Loading application">
      <LoadingState label="Loading application…" />
    </main>
  );
}
