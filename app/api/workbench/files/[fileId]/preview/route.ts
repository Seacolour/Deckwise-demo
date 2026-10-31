import { APP_HEADERS } from "@/src/lib/config/env";
import { SERVER_CONFIG } from "@/src/lib/config/env.server";
import { decodeTextBytes } from "@/src/lib/files/decodeTextBytes";

export const runtime = "nodejs";

const MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024;

const PREVIEW_TYPES: Record<string, string> = {
  pdf: "application/pdf",
  txt: "text/plain",
  json: "text/plain",
  md: "text/plain",
  png: "image/png",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  webp: "image/webp",
  gif: "image/gif",
  image: "image/*",
};

function errorResponse(message: string, status: number) {
  return Response.json({ message }, {
    status,
    headers: { "cache-control": "private, no-store" },
  });
}

function isSafeStorageUrl(raw: string): boolean {
  try {
    const url = new URL(raw);
    return url.protocol === "https:"
      || (url.protocol === "http:" && ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname));
  } catch {
    return false;
  }
}

async function readLimitedText(response: Response): Promise<Uint8Array | null> {
  const length = Number(response.headers.get("content-length"));
  if (Number.isFinite(length) && length > MAX_TEXT_PREVIEW_BYTES) return null;
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_TEXT_PREVIEW_BYTES) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

export async function GET(request: Request, context: { params: Promise<{ fileId: string }> }) {
  const { fileId } = await context.params;
  const query = new URL(request.url).searchParams;
  const projectId = query.get("projectId");
  const kind = query.get("kind")?.toLowerCase() ?? "";
  const contentType = PREVIEW_TYPES[kind];
  if (!/^[1-9]\d*$/.test(fileId) || !projectId || !/^[1-9]\d*$/.test(projectId) || !contentType) {
    return errorResponse("文件预览参数无效", 400);
  }

  const credential = request.headers.get("cookie");
  const authorization = request.headers.get("authorization");
  if (!credential && !authorization) return errorResponse("请先登录", 401);

  const backendHeaders = new Headers(APP_HEADERS);
  backendHeaders.set("accept", "application/json");
  if (credential) backendHeaders.set("cookie", credential);
  if (authorization) backendHeaders.set("authorization", authorization);

  try {
    const endpoint = `${SERVER_CONFIG.API_PROXY_TARGET.replace(/\/$/, "")}/api/files/${fileId}/access-url?projectId=${projectId}`;
    const accessResponse = await fetch(endpoint, {
      headers: backendHeaders,
      cache: "no-store",
      signal: request.signal,
    });
    if (!accessResponse.ok) return errorResponse("无权预览这个文件", accessResponse.status === 401 ? 401 : 403);
    const access: unknown = await accessResponse.json();
    const record = access && typeof access === "object" ? access as Record<string, unknown> : null;
    const data = record?.data && typeof record.data === "object" ? record.data as Record<string, unknown> : null;
    const rawUrl = typeof data?.accessUrl === "string" ? data.accessUrl : "";
    if (record?.code !== 0 || !isSafeStorageUrl(rawUrl)) return errorResponse("暂时无法预览这个文件", 502);

    const storageHeaders = new Headers();
    const range = request.headers.get("range");
    if (range && /^bytes=\d*-\d*$/.test(range)) storageHeaders.set("range", range);
    const objectResponse = await fetch(rawUrl, {
      headers: storageHeaders,
      cache: "no-store",
      redirect: "error",
      signal: request.signal,
    });
    if (!objectResponse.ok) return errorResponse("文件内容暂时无法读取", 502);
    const resolvedType = contentType === "image/*"
      ? objectResponse.headers.get("content-type")?.split(";", 1)[0].toLowerCase() ?? ""
      : contentType;
    if (contentType === "image/*" && !/^image\/(?:png|jpeg|webp|gif|avif|bmp)$/.test(resolvedType)) {
      return errorResponse("暂不支持预览此图片格式", 415);
    }

    const responseHeaders = new Headers({
      "cache-control": "private, no-store",
      "content-disposition": "inline",
      "content-type": resolvedType,
      "x-content-type-options": "nosniff",
    });
    if (contentType === "text/plain") {
      const bytes = await readLimitedText(objectResponse);
      if (!bytes) return errorResponse("文本超过 2 MB，请打开原文件查看", 413);
      responseHeaders.set("content-type", "text/plain; charset=utf-8");
      return new Response(decodeTextBytes(bytes), { headers: responseHeaders });
    }
    for (const header of ["content-length", "content-range", "accept-ranges"]) {
      const value = objectResponse.headers.get(header);
      if (value) responseHeaders.set(header, value);
    }
    return new Response(objectResponse.body, {
      status: objectResponse.status,
      headers: responseHeaders,
    });
  } catch {
    return errorResponse("文件预览失败，请稍后重试", 502);
  }
}
