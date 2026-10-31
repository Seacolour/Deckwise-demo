import { describe, expect, it } from "vitest";
import {
  resolveLocaleFromAcceptLanguageHeader,
  resolveLocaleFromLanguageTag,
} from "@/src/lib/i18n/acceptLanguage";

describe("accept-language locale matching", () => {
  it("matches browser language tags to supported app locales", () => {
    expect(resolveLocaleFromLanguageTag("ja-JP")).toBe("ja");
    expect(resolveLocaleFromLanguageTag("en-US")).toBe("en");
    expect(resolveLocaleFromLanguageTag("es-MX")).toBe("es");
    expect(resolveLocaleFromLanguageTag("ko-KR")).toBe("ko");
    expect(resolveLocaleFromLanguageTag("vi-VN")).toBe("vi");
    expect(resolveLocaleFromLanguageTag("pt-BR")).toBe("pt-BR");
    expect(resolveLocaleFromLanguageTag("pt-PT")).toBe("pt-BR");
  });

  it("keeps simplified and traditional Chinese distinct", () => {
    expect(resolveLocaleFromLanguageTag("zh-CN")).toBe("zh");
    expect(resolveLocaleFromLanguageTag("zh-Hans-CN")).toBe("zh");
    expect(resolveLocaleFromLanguageTag("zh-TW")).toBe("zh-Hant");
    expect(resolveLocaleFromLanguageTag("zh-HK")).toBe("zh-Hant");
    expect(resolveLocaleFromLanguageTag("zh-Hant-TW")).toBe("zh-Hant");
  });

  it("honors Accept-Language quality ordering", () => {
    expect(resolveLocaleFromAcceptLanguageHeader("en-US;q=0.8,ja-JP;q=1,zh-CN;q=0.9")).toBe("ja");
    expect(resolveLocaleFromAcceptLanguageHeader("fr-FR,zh-TW;q=0.9,en;q=0.8")).toBe("zh-Hant");
  });

  it("ignores unsupported languages, zero-quality entries, and wildcards", () => {
    expect(resolveLocaleFromAcceptLanguageHeader("ja-JP;q=0,en-US;q=0.8")).toBe("en");
    expect(resolveLocaleFromAcceptLanguageHeader("fr-FR,th-TH;q=0.9,*;q=0.8")).toBeNull();
    expect(resolveLocaleFromAcceptLanguageHeader(null)).toBeNull();
  });
});
