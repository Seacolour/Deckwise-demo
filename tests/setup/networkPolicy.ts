const LOOPBACK_HOSTS = new Set(["127.0.0.1", "::1", "localhost"]);
const allowedOriginCountsKey = "__deckwiseTestAllowedNetworkOriginCounts";

type TestNetworkPolicyGlobal = typeof globalThis & {
  [allowedOriginCountsKey]?: Map<string, number>;
};

function allowedOriginCounts(): Map<string, number> {
  const target = globalThis as TestNetworkPolicyGlobal;
  target[allowedOriginCountsKey] ??= new Map<string, number>();
  return target[allowedOriginCountsKey];
}

function normalizedHostname(url: URL): string {
  return url.hostname.startsWith("[") && url.hostname.endsWith("]")
    ? url.hostname.slice(1, -1)
    : url.hostname;
}

export function allowTestLoopbackNetworkOrigin(value: string): () => void {
  const url = new URL(value);
  if (
    (url.protocol !== "http:" && url.protocol !== "https:")
    || !LOOPBACK_HOSTS.has(normalizedHostname(url))
  ) {
    throw new Error(`Only loopback HTTP origins can be enabled in tests: ${value}`);
  }

  const origin = url.origin;
  const counts = allowedOriginCounts();
  counts.set(origin, (counts.get(origin) ?? 0) + 1);
  let active = true;

  return () => {
    if (!active) return;
    active = false;
    const currentCount = counts.get(origin) ?? 0;
    if (currentCount <= 1) {
      counts.delete(origin);
      return;
    }
    counts.set(origin, currentCount - 1);
  };
}

export function isTestNetworkUrlExplicitlyAllowed(url: URL): boolean {
  return (allowedOriginCounts().get(url.origin) ?? 0) > 0;
}
