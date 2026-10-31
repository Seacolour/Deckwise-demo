import { beforeEach, describe, expect, it, vi } from "vitest";

const fetchMock = vi.hoisted(() => vi.fn());
vi.mock("@/src/lib/api/fetcherWithAuth", () => ({ apiFetchWithAuth: fetchMock }));

import {
  deleteModelCredential,
  listModelCredentialStatuses,
  listWorkbenchModels,
  saveModelCredential,
} from "@/src/lib/api/modelProviders";

describe("model provider API", () => {
  beforeEach(() => fetchMock.mockReset());

  it("reads per-user credential status without requesting the secret", async () => {
    fetchMock.mockResolvedValue([{ providerId: "deepseek", configured: true, maskedKey: "****1234" }]);
    await expect(listModelCredentialStatuses()).resolves.toEqual([
      { providerId: "deepseek", configured: true, maskedKey: "****1234" },
    ]);
    expect(fetchMock).toHaveBeenCalledWith("/user/model-credentials", expect.objectContaining({
      cache: "no-store",
      requestPolicy: "query",
    }));
  });

  it("sends a key only in the authenticated PUT body and supports removal", async () => {
    fetchMock.mockResolvedValueOnce({ providerId: "deepseek", configured: true, maskedKey: "****1234" })
      .mockResolvedValueOnce(true);
    await saveModelCredential("deepseek", "test-secret-1234");
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/user/model-credentials/deepseek", {
      method: "PUT",
      body: { apiKey: "test-secret-1234" },
      requestPolicy: "mutation",
    });
    await deleteModelCredential("deepseek");
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/user/model-credentials/deepseek", {
      method: "DELETE",
      requestPolicy: "mutation",
    });
  });

  it("uses a distinct credential path for OpenRouter", async () => {
    fetchMock.mockResolvedValue({ providerId: "openrouter", configured: true, maskedKey: "****5678" });
    await saveModelCredential("openrouter", "or-test-private-5678");
    expect(fetchMock).toHaveBeenCalledWith("/user/model-credentials/openrouter", {
      method: "PUT",
      body: { apiKey: "or-test-private-5678" },
      requestPolicy: "mutation",
    });
  });

  it("normalizes available models and treats older DeepSeek responses as ASK-only", async () => {
    fetchMock.mockResolvedValue([
      { id: "deepseek/deepseek-chat", displayName: "DeepSeek Chat" },
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
      { id: "" },
    ]);
    await expect(listWorkbenchModels()).resolves.toEqual([
      {
        id: "deepseek/deepseek-chat", providerId: "deepseek", displayName: "DeepSeek Chat",
        description: null, askSupported: true, agentSupported: false,
      },
      {
        id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1",
        description: null, askSupported: true, agentSupported: true,
      },
    ]);
    expect(fetchMock).toHaveBeenCalledWith("/models/available", expect.objectContaining({ cache: "no-store" }));
  });
});
