import { isSessionCookie, SESSION_ENDED_COOKIE } from "@/auth/session-end";

describe("isSessionCookie", () => {
  it("matches the Auth.js session cookie and its chunks, secure or not", () => {
    for (const name of [
      "authjs.session-token",
      "authjs.session-token.0",
      "authjs.session-token.1",
      "__Secure-authjs.session-token",
      "__Secure-authjs.session-token.0",
    ]) {
      expect(isSessionCookie(name)).toBe(true);
    }
  });

  it("leaves every other cookie alone, including the session-ended marker", () => {
    for (const name of [
      SESSION_ENDED_COOKIE,
      "authjs.csrf-token",
      "authjs.callback-url",
      "authjs.session-tokenX",
      "KEYCLOAK_SESSION",
    ]) {
      expect(isSessionCookie(name)).toBe(false);
    }
  });
});
