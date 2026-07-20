import {
  decodeAccessToken,
  extractRoles,
  extractScopes,
} from "@/auth/claims";

function makeToken(payload: object): string {
  const b64 = Buffer.from(JSON.stringify(payload))
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
  return `header.${b64}.signature`;
}

describe("decodeAccessToken", () => {
  it("reads realm roles, scopes, and username from the payload", () => {
    const token = makeToken({
      realm_access: { roles: ["ADMIN", "INVESTOR"] },
      scope: "order:read execution:write",
      preferred_username: "investor1",
    });
    const claims = decodeAccessToken(token);
    expect(extractRoles(claims)).toEqual(["ADMIN", "INVESTOR"]);
    expect(extractScopes(claims)).toEqual(["order:read", "execution:write"]);
    expect(claims.preferred_username).toBe("investor1");
  });

  it("returns empty claims for malformed tokens instead of throwing", () => {
    expect(decodeAccessToken(undefined)).toEqual({});
    expect(decodeAccessToken("not-a-jwt")).toEqual({});
    expect(extractRoles({})).toEqual([]);
    expect(extractScopes({})).toEqual([]);
  });
});
