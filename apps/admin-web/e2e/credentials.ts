import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * Dev login credentials for the local Keycloak realm. Read from the committed dev realm
 * export (development placeholders only) so no credential value is hardcoded in the specs.
 * Overridable via env for CI.
 */
interface DevUser {
  username: string;
  password: string;
}

function realmPassword(username: string): string {
  const realmPath = join(
    __dirname,
    "..",
    "..",
    "..",
    "infrastructure",
    "docker",
    "keycloak",
    "tcs-realm.json"
  );
  const realm = JSON.parse(readFileSync(realmPath, "utf-8")) as {
    users?: Array<{
      username?: string;
      credentials?: Array<{ type?: string; value?: string }>;
    }>;
  };
  const user = realm.users?.find((u) => u.username === username);
  const cred = user?.credentials?.find((c) => c.type === "password");
  if (!cred?.value) {
    throw new Error(`No dev password for ${username} in the realm export`);
  }
  return cred.value;
}

export function devUser(username: string, envVar: string): DevUser {
  return {
    username,
    password: process.env[envVar] ?? realmPassword(username),
  };
}

export const ADMIN = () => devUser("tcs-admin", "E2E_ADMIN_PASSWORD");
export const INVESTOR1 = () => devUser("investor1", "E2E_INVESTOR1_PASSWORD");
export const INVESTOR2 = () => devUser("investor2", "E2E_INVESTOR2_PASSWORD");
