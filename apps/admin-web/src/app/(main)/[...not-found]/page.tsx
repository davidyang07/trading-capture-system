"use client";

import Image from "next/image";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";

export default function NotFound() {
  const router = useRouter();
  return (
    <div className="flex items-center justify-center gap-9">
      <Image src={`/404.png`} alt="" width={550} height={550} />
      <div className="flex flex-col items-center">
        <Image src={`/404_t.png`} alt="" width={204} height={142} />
        <h1>Page not found</h1>
        <p>The page you are looking for does not exist or has moved.</p>
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
