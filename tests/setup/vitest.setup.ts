import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";
import enMessages from "../../messages/en.json";
import { isTestNetworkUrlExplicitlyAllowed } from "./networkPolicy";

const nativeFetch = globalThis.fetch?.bind(globalThis);
const nativeConsoleError = console.error.bind(console);
const nativeConsoleWarn = console.warn.bind(console);
const CONSOLE_WARN_ALLOWLIST = [
  // jsdom may report this platform capability when a rendering-only branch is
  // exercised. Application warnings still fail unless a test explicitly spies
  // on console.warn for the expected error path.
  "Not implemented: HTMLCanvasElement.prototype.getContext",
];
const CONSOLE_ERROR_ALLOWLIST = [
  // jsdom reports missing canvas support through its virtual console. Tests
  // that need actual pixels install a canvas mock; rendering-only tests may
  // safely exercise the fallback path.
  "Not implemented: HTMLCanvasElement.prototype.getContext",
];
let unexpectedNetworkRequests: string[] = [];

function formatConsoleValue(value: unknown): string {
  if (value instanceof Error) return value.stack ?? value.message;
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

console.warn = (...args: unknown[]) => {
  const detail = args.map(formatConsoleValue).join(" ");
  if (CONSOLE_WARN_ALLOWLIST.some((entry) => detail.includes(entry))) {
    nativeConsoleWarn(...args);
    return;
  }
  throw new Error(`Unexpected console.warn in test. Mock or narrowly allow the expected warning:\n${detail}`);
};

console.error = (...args: unknown[]) => {
  const detail = args.map(formatConsoleValue).join(" ");
  if (CONSOLE_ERROR_ALLOWLIST.some((entry) => detail.includes(entry))) {
    nativeConsoleError(...args);
    return;
  }
  throw new Error(`Unexpected console.error in test. Mock or narrowly allow the expected error:\n${detail}`);
};

if (nativeFetch) {
  globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
    const rawUrl = input instanceof Request ? input.url : String(input);
    let url: URL;
    try {
      url = new URL(rawUrl);
    } catch {
      const detail = `Unexpected relative network request in test: ${rawUrl}. Stub fetch at the test boundary.`;
      unexpectedNetworkRequests.push(detail);
      return Promise.reject(new Error(detail));
    }

    if (
      (url.protocol === "http:" || url.protocol === "https:")
      && !isTestNetworkUrlExplicitlyAllowed(url)
    ) {
      const detail = `Unexpected network request in test: ${url.href}. Stub fetch or explicitly allow the integration-test loopback origin.`;
      unexpectedNetworkRequests.push(detail);
      return Promise.reject(new Error(detail));
    }

    return nativeFetch(input, init);
  }) as typeof fetch;
}

beforeEach(() => {
  unexpectedNetworkRequests = [];
});

type MessageValues = Record<string, string | number>;

function readMessage(path: string): unknown {
  return path.split(".").reduce<unknown>((current, segment) => {
    if (!current || typeof current !== "object") return undefined;
    return (current as Record<string, unknown>)[segment];
  }, enMessages);
}

function formatMessage(message: string, values?: MessageValues) {
  if (!values) return message;

  return Object.entries(values).reduce(
    (formatted, [key, value]) => formatted.replaceAll(`{${key}}`, String(value)),
    message,
  );
}

vi.mock("next-intl", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next-intl")>();

  return {
    ...actual,
    useLocale: () => "en",
    useMessages: () => enMessages,
    useTranslations: (namespace?: string) => (key: string, values?: MessageValues) => {
      const messagePath = namespace ? `${namespace}.${key}` : key;
      const message = readMessage(messagePath);
      return typeof message === "string" ? formatMessage(message, values) : messagePath;
    },
  };
});

afterEach(() => {
  cleanup();
  if (unexpectedNetworkRequests.length > 0) {
    const requests = [...unexpectedNetworkRequests];
    unexpectedNetworkRequests = [];
    throw new Error(
      `Unexpected network request${requests.length === 1 ? "" : "s"} escaped the test boundary:\n${requests
        .map((request, index) => `${index + 1}. ${request}`)
        .join("\n")}`,
    );
  }
});
