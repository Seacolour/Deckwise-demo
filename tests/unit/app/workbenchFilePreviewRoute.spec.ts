import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/src/lib/config/env", () => ({ APP_HEADERS: { "X-App-Platform": "web" } }));
vi.mock("@/src/lib/config/env.server", () => ({
  SERVER_CONFIG: { API_PROXY_TARGET: "http://localhost:8104" },
}));

import { GET } from "@/app/api/workbench/files/[fileId]/preview/route";

const signedUrl = "https://example.r2.cloudflarestorage.com/file?signature=test";
const context = { params: Promise.resolve({ fileId: "21" }) };

function request(kind: string, extraHeaders: Record<string, string> = {}) {
  return new Request(`http://localhost:3000/api/workbench/files/21/preview?projectId=7&kind=${kind}`, {
    headers: { cookie: "deckwise_session=test", ...extraHeaders },
  });
}

describe("workbench file preview route", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it("decodes UTF-8 text and returns it inline even when storage marks it as a download", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json({ code: 0, data: { accessUrl: signedUrl } }))
      .mockResolvedValueOnce(new Response(new TextEncoder().encode("测试"), {
        headers: { "content-type": "text/plain", "content-disposition": "attachment" },
      }));

    const response = await GET(request("txt"), context);
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("text/plain; charset=utf-8");
    expect(response.headers.get("content-disposition")).toBe("inline");
    await expect(response.text()).resolves.toBe("测试");
    expect(fetch).toHaveBeenNthCalledWith(1,
      "http://localhost:8104/api/files/21/access-url?projectId=7",
      expect.objectContaining({ headers: expect.any(Headers) }));
  });

  it("falls back to GB18030 for legacy Chinese text", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json({ code: 0, data: { accessUrl: signedUrl } }))
      .mockResolvedValueOnce(new Response(new Uint8Array([0xb2, 0xe2, 0xca, 0xd4])));

    const response = await GET(request("txt"), context);
    await expect(response.text()).resolves.toBe("测试");
  });

  it("streams PDF bytes inline and forwards a byte range", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json({ code: 0, data: { accessUrl: signedUrl } }))
      .mockResolvedValueOnce(new Response(new TextEncoder().encode("%PDF-1.7"), {
        status: 206,
        headers: { "content-type": "application/pdf", "content-disposition": "attachment",
          "content-range": "bytes 0-7/100", "accept-ranges": "bytes" },
      }));

    const response = await GET(request("pdf", { range: "bytes=0-7" }), context);
    expect(response.status).toBe(206);
    expect(response.headers.get("content-type")).toBe("application/pdf");
    expect(response.headers.get("content-disposition")).toBe("inline");
    expect(response.headers.get("content-range")).toBe("bytes 0-7/100");
    const [, storageInit] = vi.mocked(fetch).mock.calls[1];
    expect(new Headers(storageInit?.headers).get("range")).toBe("bytes=0-7");
  });

  it("requires a session and never fetches arbitrary URLs from a rejected access response", async () => {
    const anonymous = await GET(new Request("http://localhost:3000/api/workbench/files/21/preview?projectId=7&kind=pdf"), context);
    expect(anonymous.status).toBe(401);
    expect(fetch).not.toHaveBeenCalled();

    vi.mocked(fetch).mockResolvedValueOnce(Response.json({ code: 0, data: { accessUrl: "file:///etc/passwd" } }));
    const rejected = await GET(request("pdf"), context);
    expect(rejected.status).toBe(502);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
