// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  save: vi.fn(),
  test: vi.fn(),
  remove: vi.fn(),
  notify: vi.fn(),
}));

vi.mock("@/src/lib/api/modelProviders", () => ({
  listModelCredentialStatuses: mocks.list,
  saveModelCredential: mocks.save,
  testModelCredential: mocks.test,
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

  it("shows only the providers supported by the connected backend", async () => {
    mocks.list.mockResolvedValue([
      { providerId: "deepseek", configured: false, maskedKey: null },
      { providerId: "openrouter", configured: false, maskedKey: null },
    ]);
    render(<ModelProviderSettingsContent />);
    await waitFor(() => expect(screen.queryByText("OpenAI 官方")).not.toBeInTheDocument());
    expect(screen.getByText("DeepSeek 官方")).toBeInTheDocument();
    expect(screen.getByText("OpenRouter")).toBeInTheDocument();
  });

  it("tests a typed Key before saving and shows the result", async () => {
    mocks.list.mockResolvedValue([{ providerId: "deepseek", configured: false, maskedKey: null }]);
    mocks.test.mockResolvedValue({ providerId: "deepseek", modelId: "deepseek-chat",
      status: "valid", message: "连接成功，模型可以使用此 Key" });
    render(<ModelProviderSettingsContent />);
    fireEvent.click(await screen.findByRole("button", { name: "设置密钥" }));
    fireEvent.change(screen.getByLabelText("DeepSeek 官方 API Key"), { target: { value: "candidate-only-key" } });
    fireEvent.click(screen.getByRole("button", { name: "测试 Key" }));
    await waitFor(() => expect(mocks.test).toHaveBeenCalledWith("deepseek", "candidate-only-key"));
    expect(await screen.findByText("连接成功，模型可以使用此 Key")).toBeInTheDocument();
    expect(mocks.save).not.toHaveBeenCalled();
  });

  it("retests a saved Key and shows a sanitized failure", async () => {
    mocks.list.mockResolvedValue([{ providerId: "openrouter", configured: true, maskedKey: "****1234" }]);
    mocks.test.mockResolvedValue({ providerId: "openrouter", modelId: "openai/gpt-6-luna",
      status: "invalid", message: "API Key 无效、已过期或无权访问该模型" });
    render(<ModelProviderSettingsContent />);
    fireEvent.click(await screen.findByRole("button", { name: "测试连接" }));
    await waitFor(() => expect(mocks.test).toHaveBeenCalledWith("openrouter", undefined));
    expect(await screen.findByText("API Key 无效、已过期或无权访问该模型")).toBeInTheDocument();
  });
});
