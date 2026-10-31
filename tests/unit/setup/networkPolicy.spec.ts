import { describe, expect, it } from "vitest";
import {
  allowTestLoopbackNetworkOrigin,
  isTestNetworkUrlExplicitlyAllowed,
} from "@/tests/setup/networkPolicy";

describe("test network policy", () => {
  it("requires explicit origin-scoped access even for loopback requests", () => {
    const first = new URL("http://127.0.0.1:41001/health");
    const otherPort = new URL("http://127.0.0.1:41002/health");
    expect(isTestNetworkUrlExplicitlyAllowed(first)).toBe(false);

    const revoke = allowTestLoopbackNetworkOrigin(first.origin);
    expect(isTestNetworkUrlExplicitlyAllowed(first)).toBe(true);
    expect(isTestNetworkUrlExplicitlyAllowed(otherPort)).toBe(false);

    revoke();
    expect(isTestNetworkUrlExplicitlyAllowed(first)).toBe(false);
  });

  it("keeps duplicate grants independent and makes each revoke idempotent", () => {
    const url = new URL("http://localhost:41003/health");
    const revokeFirst = allowTestLoopbackNetworkOrigin(url.origin);
    const revokeSecond = allowTestLoopbackNetworkOrigin(url.origin);

    revokeFirst();
    revokeFirst();
    expect(isTestNetworkUrlExplicitlyAllowed(url)).toBe(true);

    revokeSecond();
    expect(isTestNetworkUrlExplicitlyAllowed(url)).toBe(false);
  });

  it("accepts the IPv6 loopback origin", () => {
    const url = new URL("http://[::1]:41004/health");
    const revoke = allowTestLoopbackNetworkOrigin(url.origin);

    expect(isTestNetworkUrlExplicitlyAllowed(url)).toBe(true);

    revoke();
    expect(isTestNetworkUrlExplicitlyAllowed(url)).toBe(false);
  });

  it("never permits external origins through the integration escape hatch", () => {
    expect(() => allowTestLoopbackNetworkOrigin("https://example.com"))
      .toThrow("Only loopback HTTP origins can be enabled");
  });
});
