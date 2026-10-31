import { APP_HEADERS } from "@/src/lib/config/env";

const PRIVATE_PATTERNS = [
  /^127\./,
  /^10\./,
  /^172\.(1[6-9]|2\d|3[01])\./,
  /^192\.168\./,
  /^localhost$/i,
  /^::1$/,
  /^fe80:/i,
  /^fc00:/i,
] as const;

export function resolveAllowedProxyHosts(): Set<string> {
  return new Set((process.env.FILE_PROXY_ALLOWED_HOSTS ?? "")
    .split(",")
    .map((host) => host.trim().toLowerCase())
    .filter(Boolean));
}

function isPrivateUrl(url: URL): boolean {
  const hostname = url.hostname.toLowerCase();
  const ip = url.hostname;
  return PRIVATE_PATTERNS.some(
    (pattern) => pattern.test(hostname) || pattern.test(ip),
  );
}

export function validateProxyTargetUrl(
  urlString: string,
  allowedHosts: ReadonlySet<string>,
): { valid: boolean; error?: string } {
  try {
    const url = new URL(urlString);
    if (!["http:", "https:"].includes(url.protocol)) {
      return { valid: false, error: "Only HTTP/HTTPS protocols are allowed" };
    }

    if (isPrivateUrl(url)) {
      return { valid: false, error: "Private IP addresses are not allowed" };
    }

    const hostname = url.hostname.toLowerCase();
    if (!allowedHosts.has(hostname)) {
      return {
        valid: false,
        error: `Host ${hostname} is not in the allowed list`,
      };
    }

    return { valid: true };
  } catch {
    return { valid: false, error: "Invalid URL format" };
  }
}

export function buildProxyForwardHeaders(input: {
  requestHeaders: Headers;
}): Record<string, string> {
  const { requestHeaders } = input;
  const forwardHeaders: Record<string, string> = {};

  const range = requestHeaders.get("range");
  if (range) {
    forwardHeaders.Range = range;
  }

  for (const [key, value] of Object.entries(APP_HEADERS)) {
    forwardHeaders[key] = value;
  }

  forwardHeaders.Accept = forwardHeaders.Accept || requestHeaders.get("accept") || "*/*";
  forwardHeaders["User-Agent"] =
    forwardHeaders["User-Agent"] ||
    requestHeaders.get("user-agent") ||
    "Mozilla/5.0 (compatible; JZProxy/1.0; +https://juzilab.cn)";

  const referer = forwardHeaders.Referer || requestHeaders.get("referer");
  if (referer) {
    forwardHeaders.Referer = referer;
  }

  const acceptLanguage =
    forwardHeaders["Accept-Language"] ||
    requestHeaders.get("accept-language");
  if (acceptLanguage) {
    forwardHeaders["Accept-Language"] = acceptLanguage;
  }

  return forwardHeaders;
}
