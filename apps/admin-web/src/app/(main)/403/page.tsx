"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";

export default function Forbidden() {
  const router = useRouter();
  return (
    <div className="flex items-center justify-center gap-9">
      <Image src={`/403.png`} alt="" width={550} height={550} />
      <div className="flex flex-col items-center">
        <Image src={`/403_t.png`} alt="" width={204} height={142} />
        <h1>Access denied</h1>
        <p>You do not have permission to view this page.</p>
        <Button
          style={{ marginTop: "5rem" }}
          rounded
          label="Go Home"
          onClick={() => {
            router.push("/dashboard");
          }}
        />
      </div>
    </div>
  );
}
