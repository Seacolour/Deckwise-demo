import { describe, expect, it } from "vitest";
import {
  getClientMessageNamespaces,
  pickClientMessages,
} from "@/src/lib/i18n/clientMessages";

describe("client messages", () => {
  it("includes editor namespaces only for the product scope", () => {
    expect(getClientMessageNamespaces("product")).toEqual(
      expect.arrayContaining(["deckwise", "middle", "projects", "shareSheet"]),
    );
    expect(getClientMessageNamespaces("login")).toContain("login");
    expect(getClientMessageNamespaces("base")).toEqual([
      "app",
      "common",
      "errorBoundary",
      "notFound",
    ]);
  });

  it("serializes only namespaces selected for the request path", () => {
    const messages = {
      app: { name: "Deckwise" },
      login: { title: "Sign in" },
      common: { retry: "Retry" },
      errorBoundary: { title: "Error" },
      deckwise: { toolbar: { insert: "Insert" } },
      notFound: { title: "Not found" },
    };

    expect(pickClientMessages(messages, "login")).toEqual({
      app: messages.app,
      common: messages.common,
      errorBoundary: messages.errorBoundary,
      login: messages.login,
      notFound: messages.notFound,
    });
  });
});
