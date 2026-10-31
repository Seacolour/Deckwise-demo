import assert from "node:assert/strict";
import { afterEach, test, vi } from "vitest";
import {
  buildProxyForwardHeaders,
  resolveAllowedProxyHosts,
  validateProxyTargetUrl,
} from "@/src/lib/server/proxy/requestPolicy.ts";

afterEach(() => vi.unstubAllEnvs());

test("resolveAllowedProxyHosts uses only explicitly configured hosts", () => {
  vi.stubEnv("FILE_PROXY_ALLOWED_HOSTS", "files.example.com, assets.example.com");
  const allowedHosts = resolveAllowedProxyHosts();
  assert.deepEqual([...allowedHosts].sort(), ["assets.example.com", "files.example.com"]);
});

test("validateProxyTargetUrl rejects private hosts and non-allowlisted hosts", () => {
  const allowedHosts = new Set(["files.example.com"]);

  assert.deepEqual(
    validateProxyTargetUrl("http://127.0.0.1:3000/demo.pdf", allowedHosts),
    { valid: false, error: "Private IP addresses are not allowed" },
  );

  assert.deepEqual(
    validateProxyTargetUrl("https://unknown.example.com/demo.pdf", allowedHosts),
    {
      valid: false,
      error: "Host unknown.example.com is not in the allowed list",
    },
  );

  assert.deepEqual(
    validateProxyTargetUrl("https://files.example.com/demo.pdf", allowedHosts),
    { valid: true },
  );

  assert.deepEqual(
    validateProxyTargetUrl("https://files.example.com/dev/a.png?rt=token", new Set()),
    { valid: false, error: "Host files.example.com is not in the allowed list" },
  );
});

test("buildProxyForwardHeaders forwards the expected browser-like headers", () => {
  const requestHeaders = new Headers({
    accept: "application/pdf",
    "accept-language": "zh-CN",
    referer: "https://example.com/app",
    "user-agent": "UnitTest/1.0",
    range: "bytes=0-1024",
  });

  const internalHeaders = buildProxyForwardHeaders({
    requestHeaders,
  });
  assert.equal(internalHeaders.Range, "bytes=0-1024");
  assert.equal(internalHeaders.Accept, "application/pdf");
  assert.equal(internalHeaders["Accept-Language"], "zh-CN");
  assert.equal(internalHeaders.Referer, "https://example.com/app");
  assert.equal(internalHeaders["User-Agent"], "UnitTest/1.0");
});
