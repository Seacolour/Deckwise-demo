// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  save: vi.fn(),
  remove: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("@/src/lib/api/modelProviders", () => ({
  listModelCredentialStatuses: mocks.list,
  saveModelCredential: mocks.save,
  deleteModelCredential: mocks.remove,
  notifyModelCredentialsChanged: mocks.notify,
}));

import { ModelProviderSettingsContent } from "@/src/components/features/settings/ModelProviderSettingsContent";

describe("ModelProviderSettingsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.list.mockResolvedValue([
      { providerId: "deepseek", configured: false, maskedKey: null },
      { providerId: "openai", configured: false, maskedKey: null },
      { providerId: "openrouter", configured: false, maskedKey: null },
    ]);
  });

  it("saves a key in a password field and only displays the masked status afterward", async () => {
    mocks.save.mockResolvedValue({ providerId: "deepseek", configured: true, maskedKey: "****1234" });
    render(<ModelProviderSettingsContent />);

    fireEvent.click(await screen.findAllByRole("button", { name: "设置密钥" }).then((buttons) => buttons[0]));
    const input = screen.getByLabelText("DeepSeek 官方 API Key");
    expect(input).toHaveAttribute("type", "password");
    fireEvent.change(input, { target: { value: "test-secret-1234" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith("deepseek", "test-secret-1234"));
    expect(await screen.findByText(/已配置 · \*\*\*\*1234/)).toBeInTheDocument();
    expect(screen.queryByDisplayValue("test-secret-1234")).not.toBeInTheDocument();
    expect(mocks.notify).toHaveBeenCalledOnce();
  });

  it("requires confirmation before removing a stored key", async () => {
    mocks.list.mockResolvedValue([
      { providerId: "deepseek", configured: true, maskedKey: "****1234" },
      { providerId: "openai", configured: false, maskedKey: null },
      { providerId: "openrouter", configured: false, maskedKey: null },
    ]);
    mocks.remove.mockResolvedValue(true);
    render(<ModelProviderSettingsContent />);

    fireEvent.click(await screen.findByRole("button", { name: "移除" }));
    expect(mocks.remove).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认移除" }));
    await waitFor(() => expect(mocks.remove).toHaveBeenCalledWith("deepseek"));
    expect(mocks.notify).toHaveBeenCalledOnce();
  });

  it("saves OpenRouter separately from the official OpenAI credential", async () => {
    mocks.save.mockResolvedValue({ providerId: "openrouter", configured: true, maskedKey: "****5678" });
    render(<ModelProviderSettingsContent />);

    fireEvent.click((await screen.findAllByRole("button", { name: "设置密钥" }))[2]);
    fireEvent.change(screen.getByLabelText("OpenRouter API Key"), { target: { value: "or-test-private-5678" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith("openrouter", "or-test-private-5678"));
    expect(await screen.findByText(/已配置 · \*\*\*\*5678/)).toBeInTheDocument();
    expect(screen.queryByDisplayValue("or-test-private-5678")).not.toBeInTheDocument();
  });
});
