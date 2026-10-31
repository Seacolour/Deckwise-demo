import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { appLocaleOptions, appLocales } from "@/src/lib/i18n";

function leaves(value: unknown, prefix = "", result: Record<string, string> = {}): Record<string, string> {
  if (typeof value === "string") result[prefix] = value;
  else if (value && typeof value === "object" && !Array.isArray(value)) {
    for (const [key, child] of Object.entries(value)) {
      leaves(child, prefix ? `${prefix}.${key}` : key, result);
    }
  }
  return result;
}

function messages(locale: string): Record<string, string> {
  return leaves(JSON.parse(readFileSync(join(process.cwd(), "messages", `${locale}.json`), "utf8")));
}

const placeholders = (value: string) => [...value.matchAll(/\{[^{}]+\}/g)].map((match) => match[0]).sort();

describe("app locale messages", () => {
  it("offers every supported locale", () => {
    expect(appLocaleOptions.map((option) => option.value)).toEqual([...appLocales]);
  });

  it("keeps message keys and interpolation parameters aligned", () => {
    const english = messages("en");
    const keys = Object.keys(english).sort();
    for (const locale of appLocales) {
      const translated = messages(locale);
      expect(Object.keys(translated).sort(), locale).toEqual(keys);
      for (const key of keys) {
        expect(placeholders(translated[key]), `${locale}.${key}`).toEqual(placeholders(english[key]));
      }
    }
  });

  it("provides the active email-code login copy", () => {
    for (const locale of appLocales) {
      const translated = messages(locale);
      for (const key of ["login.emailLabel", "login.codeLabel", "login.sendCode", "login.signInWithEmail"]) {
        expect(translated[key], `${locale}.${key}`).toBeTruthy();
      }
    }
  });
});
