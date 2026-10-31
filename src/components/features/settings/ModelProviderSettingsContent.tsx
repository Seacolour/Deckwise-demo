"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  deleteModelCredential,
  listModelCredentialStatuses,
  notifyModelCredentialsChanged,
  saveModelCredential,
  type ModelCredentialStatus,
  type ModelProviderId,
} from "@/src/lib/api/modelProviders";

const PROVIDERS: { id: ModelProviderId; name: string; description: string }[] = [
  { id: "deepseek", name: "DeepSeek 官方", description: "使用 DeepSeek API Key 直连，支持快速问答和 Agent 任务" },
  { id: "openai", name: "OpenAI 官方", description: "使用 OpenAI API Key 直连，支持快速问答和 Agent 任务" },
  { id: "openrouter", name: "OpenRouter", description: "使用 OpenRouter API Key 调用其提供的 OpenAI 模型" },
];

export function ModelProviderSettingsContent() {
  const [statuses, setStatuses] = useState<ModelCredentialStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyProvider, setBusyProvider] = useState<ModelProviderId | null>(null);
  const [editingProvider, setEditingProvider] = useState<ModelProviderId | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<ModelProviderId | null>(null);
  const [apiKey, setApiKey] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void listModelCredentialStatuses()
      .then((next) => { if (active) setStatuses(next); })
      .catch(() => { if (active) setError("无法读取模型密钥状态，请稍后重试。"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const beginEditing = (providerId: ModelProviderId) => {
    setApiKey("");
    setError(null);
    setNotice(null);
    setConfirmDelete(null);
    setEditingProvider(providerId);
  };

  const cancelEditing = () => {
    setApiKey("");
    setEditingProvider(null);
  };

  const save = async (event: FormEvent, providerId: ModelProviderId) => {
    event.preventDefault();
    const key = apiKey.trim();
    if (!key || busyProvider) return;
    setBusyProvider(providerId);
    setError(null);
    setNotice(null);
    try {
      const updated = await saveModelCredential(providerId, key);
      setStatuses((previous) => [...previous.filter((item) => item.providerId !== providerId), updated]);
      cancelEditing();
      setNotice(`${PROVIDERS.find((provider) => provider.id === providerId)?.name} 密钥已保存。`);
      notifyModelCredentialsChanged();
    } catch {
      // Do not surface transport errors that might echo a submitted secret.
      setError("保存密钥失败，请检查密钥格式及服务状态后重试。");
    } finally {
      setBusyProvider(null);
    }
  };

  const remove = async (providerId: ModelProviderId) => {
    if (busyProvider) return;
    setBusyProvider(providerId);
    setError(null);
    setNotice(null);
    try {
      await deleteModelCredential(providerId);
      setStatuses((previous) => previous.map((item) => item.providerId === providerId
        ? { ...item, configured: false, maskedKey: null }
        : item));
      setConfirmDelete(null);
      setNotice(`${PROVIDERS.find((provider) => provider.id === providerId)?.name} 密钥已移除。`);
      notifyModelCredentialsChanged();
    } catch {
      setError("移除密钥失败，请稍后重试。");
    } finally {
      setBusyProvider(null);
    }
  };

  return (
    <section aria-labelledby="model-provider-settings-title" className="mt-1 space-y-3">
      <div>
        <h2 id="model-provider-settings-title" className="text-[14px] font-semibold leading-5 text-foreground">模型提供商</h2>
        <p className="mt-1 text-[13px] leading-5 text-muted-foreground">
          添加自己的 API Key 后才能在对话中选择对应模型。密钥由后端加密保存，页面只显示末尾几位。
        </p>
      </div>
      {PROVIDERS.map((provider) => {
        const status = statuses.find((item) => item.providerId === provider.id);
        const configured = status?.configured === true;
        return (
          <div key={provider.id} className="rounded-2xl border border-border bg-card p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-[180px] flex-1">
                <h3 className="text-[14px] font-semibold text-foreground">{provider.name}</h3>
                <p className="mt-1 text-[13px] leading-5 text-muted-foreground">{provider.description}</p>
                {!loading && <p className="mt-2 flex items-center gap-1.5 text-[12px] text-muted-foreground">
                  <span className={`size-1.5 rounded-full ${configured ? "bg-emerald-500" : "bg-muted-foreground/50"}`} aria-hidden="true" />
                  {configured ? `已配置 · ${status?.maskedKey ?? "密钥已保存"}` : "未配置"}
                </p>}
              </div>
              {loading ? <span className="text-[12px] text-muted-foreground">正在读取…</span> : editingProvider !== provider.id && confirmDelete !== provider.id ? (
                <div className="flex shrink-0 items-center gap-2">
                  <button type="button" className="rounded-lg border border-border px-3 py-1.5 text-[13px] hover:bg-muted" disabled={Boolean(busyProvider)} onClick={() => beginEditing(provider.id)}>
                    {configured ? "更换密钥" : "设置密钥"}
                  </button>
                  {configured && <button type="button" className="rounded-lg px-2 py-1.5 text-[13px] text-destructive hover:bg-muted" disabled={Boolean(busyProvider)} onClick={() => setConfirmDelete(provider.id)}>
                    移除
                  </button>}
                </div>
              ) : null}
            </div>
            {editingProvider === provider.id && <form className="mt-4 flex flex-wrap items-center gap-2" onSubmit={(event) => void save(event, provider.id)}>
              <input type="password" autoComplete="off" aria-label={`${provider.name} API Key`} value={apiKey} onChange={(event) => setApiKey(event.target.value)}
                className="h-9 min-w-[180px] flex-1 rounded-md border border-border bg-background px-3 text-[13px] outline-none focus:ring-2 focus:ring-ring/35" placeholder="输入新的 API Key" disabled={Boolean(busyProvider)} />
              <button type="submit" className="h-9 rounded-md bg-primary px-3 text-[12px] text-primary-foreground disabled:opacity-50" disabled={!apiKey.trim() || Boolean(busyProvider)}>保存</button>
              <button type="button" className="h-9 px-2 text-[12px] text-muted-foreground" onClick={cancelEditing} disabled={Boolean(busyProvider)}>取消</button>
            </form>}
            {confirmDelete === provider.id && <div className="mt-4 flex flex-wrap items-center gap-2 text-[12px]">
              <span>移除后，此提供商的模型将不可用。确定移除？</span>
              <button type="button" className="rounded-md bg-destructive px-3 py-1.5 text-destructive-foreground disabled:opacity-50" disabled={Boolean(busyProvider)} onClick={() => void remove(provider.id)}>确认移除</button>
              <button type="button" className="px-2 text-muted-foreground" disabled={Boolean(busyProvider)} onClick={() => setConfirmDelete(null)}>取消</button>
            </div>}
          </div>
        );
      })}
      {error && <p role="alert" className="text-[12px] text-destructive">{error}</p>}
      {notice && <p role="status" className="text-[12px] text-emerald-700 dark:text-emerald-300">{notice}</p>}
    </section>
  );
}
