import "@testing-library/jest-dom";
import { toHaveNoViolations } from "jest-axe";
import { TextDecoder, TextEncoder } from "util";

expect.extend(toHaveNoViolations);

// jsdom does not provide TextEncoder/TextDecoder, which the token-claim decoder uses.
if (typeof globalThis.TextDecoder === "undefined") {
  Object.assign(globalThis, { TextDecoder, TextEncoder });
}

// jsdom lacks matchMedia, which PrimeReact touches on mount.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}
