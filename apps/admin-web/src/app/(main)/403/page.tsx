"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";

export default function NotFound() {
  const router = useRouter();
  return (
    <div className="flex items-center justify-center gap-9">
      <Image src={`/403.png`} alt="403" width={550} height={550} />
      <div className="flex flex-col items-center">
        <Image src={`/403_t.png`} alt="403 text" width={204} height={142} />
        <Button
          style={{ marginTop: "5rem" }}
          rounded
          label="Go Home"
          severity="help"
          onClick={() => {
            router.push("/dashboard");
          }}
        />
      </div>
    </div>
  );
}
