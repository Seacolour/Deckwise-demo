import { NextRequest } from "next/server";
import { proxyDownload } from "@/src/lib/server/proxyDownload";
import {
  buildProxyForwardHeaders,
  resolveAllowedProxyHosts,
  validateProxyTargetUrl,
} from "@/src/lib/server/proxy/requestPolicy";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const fileUrl = searchParams.get("url");
  const cacheKey = searchParams.get("ck");
  const allowedHosts = resolveAllowedProxyHosts();
  const validation = fileUrl
    ? validateProxyTargetUrl(fileUrl, allowedHosts)
    : { valid: false, error: "Missing url" };

  if (!validation.valid || !fileUrl) {
    return new Response(validation.error ?? "Invalid url", {
      status: fileUrl ? 403 : 400,
    });
  }

  const response = await proxyDownload(
    fileUrl,
    {
      method: "GET",
      headers: buildProxyForwardHeaders({
        requestHeaders: request.headers,
      }),
      redirect: "follow",
    },
    {
      debugLabel: "pdf",
      noStore: false,
      extraResponseHeaders: {
        "content-type": "application/pdf",
      },
    },
  );

  const responseHeaders = new Headers(response.headers);
  responseHeaders.set("content-disposition", 'inline; filename="file.pdf"');

  if (cacheKey) {
    responseHeaders.set("cache-control", "public, max-age=31536000, immutable");
  }

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  });
}
