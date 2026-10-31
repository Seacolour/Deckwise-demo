import { describe, expect, it, vi } from "vitest";

const localeState = vi.hoisted(() => ({
  locale: "vi",
}));

vi.mock("next-intl/server", () => ({
  getLocale: () => localeState.locale,
  getTranslations: ({ locale, namespace }: { locale: string; namespace: string }) => {
    const copy: Record<string, Record<string, Record<string, string>>> = {
      en: {
        metadata: {
          siteDescription:
            "A workspace for collaborating with an AI agent on documents, ideas, and research.",
        },
      },
      vi: {
        metadata: {
          siteDescription:
            "Không gian cộng tác với AI Agent để sắp xếp tư liệu, viết và chỉnh sửa tài liệu.",
        },
      },
    };

    return (key: string) => copy[locale]?.[namespace]?.[key] ?? `${namespace}.${key}`;
  },
}));

describe("manifest", () => {
  it.each([
    [
      "vi",
      "Không gian cộng tác với AI Agent để sắp xếp tư liệu, viết và chỉnh sửa tài liệu.",
    ],
  ])("localizes the install metadata for %s", async (locale, description) => {
    localeState.locale = locale;
    const { default: manifest } = await import("@/app/manifest");

    const result = await manifest();

    expect(result.description).toBe(description);
    expect(result.start_url).toBe(`/${locale}`);
  });

  it("keeps English on the canonical unprefixed start URL", async () => {
    localeState.locale = "en";
    const { default: manifest } = await import("@/app/manifest");

    const result = await manifest();

    expect(result.description).toBe(
      "A workspace for collaborating with an AI agent on documents, ideas, and research.",
    );
    expect(result.start_url).toBe("/");
  });
});
