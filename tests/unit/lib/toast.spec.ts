import { beforeEach, describe, expect, it, vi } from "vitest";
import jaMessages from "@/messages/ja.json";
import koMessages from "@/messages/ko.json";
import ptBrMessages from "@/messages/pt-BR.json";
import viMessages from "@/messages/vi.json";
import { notify } from "@/src/lib/toast";
import { resolveToastLocaleCopy } from "@/src/lib/toastCopy";

const toastMock = vi.hoisted(() => vi.fn());

vi.mock("sonner", () => ({
  toast: toastMock,
}));

describe("notify toast copy", () => {
  beforeEach(() => {
    toastMock.mockReset();
  });

  it("keeps Deckwise in English toast messages", () => {
    notify.error("Failed to reload Deckwise slides after save failure.");

    expect(toastMock).toHaveBeenCalledWith(
      "Failed to reload Deckwise slides after save failure.",
      expect.objectContaining({ className: "deckwise-app-toast--error deckwise-app-toast--icon-alert" }),
    );
  });

  it("keeps Deckwise in CJK toast messages", () => {
    notify.error("保存失敗後に Deckwise スライドを再読み込みできませんでした。");

    expect(toastMock).toHaveBeenCalledWith(
      "保存失敗後に Deckwise スライドを再読み込みできませんでした。",
      expect.objectContaining({ className: "deckwise-app-toast--error deckwise-app-toast--icon-alert" }),
    );
  });

  it("shows Deckwise when it is the whole toast message", () => {
    notify.error("Deckwise");

    expect(toastMock).toHaveBeenCalledWith(
      "Deckwise",
      expect.objectContaining({ className: "deckwise-app-toast--error deckwise-app-toast--icon-alert" }),
    );
  });

  it("resolves toast copy from newly added target locale messages", () => {
    expect(resolveToastLocaleCopy(viMessages, "vi").messages.exportStarted).toBe("Xuất khẩu đã bắt đầu.");
    expect(resolveToastLocaleCopy(jaMessages, "ja").messages.fileUrlMissing).toBe("ファイルの URL がありません。");
    expect(resolveToastLocaleCopy(koMessages, "ko").messages.documentNoLongerExists).toBe(
      "문서가 더 이상 존재하지 않습니다.",
    );
    expect(resolveToastLocaleCopy(ptBrMessages, "pt-BR").messages.copiedDescription).toBe(
      "Copiado para a área de transferência",
    );
  });
});
