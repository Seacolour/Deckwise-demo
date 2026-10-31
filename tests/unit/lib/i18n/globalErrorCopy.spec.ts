import { describe, expect, it } from "vitest";
import {
  getGlobalErrorCopy,
  resolveGlobalErrorLocale,
} from "@/src/lib/i18n/globalErrorCopy";
import { appLocales, type AppLocale } from "@/src/lib/i18n";
import enMessages from "@/messages/en.json";
import esMessages from "@/messages/es.json";
import jaMessages from "@/messages/ja.json";
import koMessages from "@/messages/ko.json";
import ptBrMessages from "@/messages/pt-BR.json";
import viMessages from "@/messages/vi.json";
import zhHantMessages from "@/messages/zh-Hant.json";
import zhMessages from "@/messages/zh.json";

const catalogsByLocale = {
  en: enMessages,
  es: esMessages,
  ja: jaMessages,
  ko: koMessages,
  "pt-BR": ptBrMessages,
  vi: viMessages,
  "zh-Hant": zhHantMessages,
  zh: zhMessages,
} satisfies Record<AppLocale, typeof enMessages>;

describe("global error copy", () => {
  it("prefers the persisted locale cookie over browser languages", () => {
    expect(resolveGlobalErrorLocale({
      cookie: "NEXT_LOCALE=pt-BR",
      languages: ["vi-VN"],
    })).toBe("pt-BR");
  });

  it("resolves newly added browser language tags", () => {
    expect(resolveGlobalErrorLocale({ languages: ["vi-VN"] })).toBe("vi");
    expect(resolveGlobalErrorLocale({ languages: ["pt-BR"] })).toBe("pt-BR");
    expect(resolveGlobalErrorLocale({ languages: ["zh-TW"] })).toBe("zh-Hant");
    expect(resolveGlobalErrorLocale({ languages: ["th-TH"] })).toBe("en");
  });

  it("returns localized fallback text for global errors", () => {
    expect(getGlobalErrorCopy("vi")).toEqual(expect.objectContaining({
      htmlLang: "vi",
      homeLabel: "Trở về nhà",
      retryLabel: "Thử lại",
      title: "Đã xảy ra lỗi",
    }));
  });

  it("keeps the minimal client copy synchronized with the locale catalogs", () => {
    for (const locale of appLocales) {
      const copy = getGlobalErrorCopy(locale);
      const catalog = catalogsByLocale[locale];

      expect(copy).toEqual(expect.objectContaining({
        homeLabel: catalog.app.backToHome,
        message: catalog.errorBoundary.message,
        retryLabel: catalog.common.retry,
        title: catalog.errorBoundary.title,
      }));
    }
  });
});
