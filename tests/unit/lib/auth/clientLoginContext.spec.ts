import { describe, expect, it } from "vitest";
import { buildClientLoginContextFromSource } from "@/src/lib/auth/clientLoginContext";

describe("client login context", () => {
  it("uses the browser locale and derives the country", () => {
    expect(buildClientLoginContextFromSource({
      appLocale: "en",
      browserLocales: ["ja-JP", "en-US"],
      timeZone: "Asia/Tokyo",
    })).toEqual({
      locale: "ja-JP",
      country: "JP",
      timeZone: "Asia/Tokyo",
    });
  });

  it("falls back to the app locale when browser locale is unavailable", () => {
    expect(buildClientLoginContextFromSource({
      appLocale: "ja",
      browserLocale: "",
      browserLocales: [],
      timeZone: "",
    })).toEqual({
      locale: "ja",
    });
  });

  it("normalizes locale separators before sending the context", () => {
    expect(buildClientLoginContextFromSource({
      browserLocale: "en_US",
      timeZone: "America/New_York",
    })).toEqual({
      locale: "en-US",
      country: "US",
      timeZone: "America/New_York",
    });
  });
});
