import path from "node:path";
import { fileURLToPath } from "node:url";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");
const projectRoot = path.dirname(fileURLToPath(import.meta.url));

const nextConfig = {
  output: "standalone",
  poweredByHeader: false,
  images: {
    qualities: [68, 75, 86],
  },
  experimental: {
    // Multiple root layouts need a standalone 404 document; otherwise Next
    // cannot compose app/not-found.tsx under a single locale/provider tree.
    globalNotFound: true,
    // The Next 16 Turbopack dev cache can occasionally leave a stale route
    // state on this Windows checkout, making first cold requests resolve as 404.
    turbopackFileSystemCacheForDev: false,
    proxyClientMaxBodySize: "64mb",
    externalDir: true,
  },
  turbopack: {
    root: projectRoot,
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          {
            key: "Content-Security-Policy",
            value: "base-uri 'self'; object-src 'none'",
          },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          {
            key: "Permissions-Policy",
            value: "camera=(), microphone=(), geolocation=()",
          },
          { key: "Cross-Origin-Opener-Policy", value: "same-origin-allow-popups" },
          { key: "Cross-Origin-Embedder-Policy", value: "unsafe-none" },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
