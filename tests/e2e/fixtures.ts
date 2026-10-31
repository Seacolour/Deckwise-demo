import {
  expect,
  test as base,
  type ConsoleMessage,
  type Page,
  type Request,
  type Response,
} from "@playwright/test";

type BrowserFailure = {
  detail: string;
  kind: "console" | "network" | "page" | "request" | "response";
};

export type BrowserFailureAllowlist = {
  consoleLocationOrigins?: string[];
  consoleMessageIncludes?: string[];
  externalOrigins?: string[];
  sameOriginPathPrefixes?: string[];
};

const DEFAULT_BROWSER_WARNING_ALLOWLIST = [
  // Chromium emits this rendering hint from canvas libraries even though the
  // page remains correct. Keep the exemption exact so application warnings do
  // not disappear into an open-ended browser-noise bucket.
  "willReadFrequently attribute",
];

const MONITORED_NETWORK_RESOURCE_TYPES = new Set([
  "eventsource",
  "fetch",
  "websocket",
  "xhr",
]);

function isSameOrigin(page: Page, url: string): boolean {
  try {
    return new URL(url).origin === new URL(page.url()).origin;
  } catch {
    return false;
  }
}

function formatConsoleMessage(message: ConsoleMessage): string {
  const location = message.location();
  const suffix = location.url
    ? ` (${location.url}:${location.lineNumber}:${location.columnNumber})`
    : "";
  return `${message.text()}${suffix}`;
}

function requestFailureIsActionable(page: Page, request: Request): boolean {
  if (!isSameOrigin(page, request.url())) {
    return false;
  }

  // Browsers routinely abort media/range requests when a test finishes or a
  // route changes. Server, DNS and protocol failures remain actionable.
  return request.failure()?.errorText !== "net::ERR_ABORTED";
}

function responseFailureIsActionable(page: Page, response: Response): boolean {
  return isSameOrigin(page, response.url()) && response.status() >= 400;
}

function normalizedOrigin(value: string): string | null {
  try {
    return new URL(value).origin;
  } catch {
    return null;
  }
}

function requestIsAllowlisted(
  page: Page,
  request: Request,
  allowlist: BrowserFailureAllowlist,
): boolean {
  if (!MONITORED_NETWORK_RESOURCE_TYPES.has(request.resourceType())) {
    return true;
  }

  const requestUrl = new URL(request.url());
  const pageOrigin = new URL(page.url()).origin;
  if (requestUrl.origin !== pageOrigin) {
    return (allowlist.externalOrigins ?? [])
      .map(normalizedOrigin)
      .some((origin) => origin === requestUrl.origin);
  }

  // Next's client router transports server-component payloads as same-origin
  // fetches. They are framework navigation, not application API traffic.
  if (request.method() === "GET" && requestUrl.searchParams.has("_rsc")) {
    return true;
  }

  return (allowlist.sameOriginPathPrefixes ?? []).some((prefix) => (
    prefix.endsWith("/")
      ? requestUrl.pathname.startsWith(prefix)
      : requestUrl.pathname === prefix || requestUrl.pathname.startsWith(`${prefix}/`)
  ));
}

function consoleMessageIsAllowlisted(
  message: ConsoleMessage,
  allowlist: BrowserFailureAllowlist,
): boolean {
  const text = message.text();
  const locationOrigin = normalizedOrigin(message.location().url);
  if (
    locationOrigin &&
    (allowlist.consoleLocationOrigins ?? [])
      .map(normalizedOrigin)
      .some((origin) => origin === locationOrigin)
  ) {
    return true;
  }
  return [
    ...DEFAULT_BROWSER_WARNING_ALLOWLIST,
    ...(allowlist.consoleMessageIncludes ?? []),
  ].some((entry) => text.includes(entry));
}

export const test = base.extend<{
  browserFailureAllowlist: BrowserFailureAllowlist;
  browserFailureGate: void;
}>({
  browserFailureAllowlist: [{}, { option: true }],
  browserFailureGate: [async ({ browserFailureAllowlist, page }, use) => {
    const failures: BrowserFailure[] = [];

    const onConsole = (message: ConsoleMessage) => {
      if (
        (message.type() === "error" || message.type() === "warning") &&
        !consoleMessageIsAllowlisted(message, browserFailureAllowlist)
      ) {
        failures.push({
          detail: formatConsoleMessage(message),
          kind: "console",
        });
      }
    };
    const onPageError = (error: Error) => {
      failures.push({ detail: error.stack ?? error.message, kind: "page" });
    };
    const onRequest = (request: Request) => {
      if (!requestIsAllowlisted(page, request, browserFailureAllowlist)) {
        failures.push({
          detail: `${request.resourceType()} ${request.method()} ${request.url()}`,
          kind: "network",
        });
      }
    };
    const onRequestFailed = (request: Request) => {
      if (requestFailureIsActionable(page, request)) {
        failures.push({
          detail: `${request.method()} ${request.url()} — ${request.failure()?.errorText ?? "unknown error"}`,
          kind: "request",
        });
      }
    };
    const onResponse = (response: Response) => {
      if (responseFailureIsActionable(page, response)) {
        failures.push({
          detail: `${response.status()} ${response.request().method()} ${response.url()}`,
          kind: "response",
        });
      }
    };

    page.on("console", onConsole);
    page.on("pageerror", onPageError);
    page.on("request", onRequest);
    page.on("requestfailed", onRequestFailed);
    page.on("response", onResponse);

    await use();

    page.off("console", onConsole);
    page.off("pageerror", onPageError);
    page.off("request", onRequest);
    page.off("requestfailed", onRequestFailed);
    page.off("response", onResponse);

    expect(
      failures,
      failures.map((failure) => `[${failure.kind}] ${failure.detail}`).join("\n"),
    ).toEqual([]);
  }, { auto: true }],
});

export { expect } from "@playwright/test";
