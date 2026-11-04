"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { BookOpen, ExternalLink, PlugZap } from "lucide-react";
import { beginNotionConnection, disconnectNotionConnection, listAgentPlugins, updateAgentPlugin, type AgentPlugin } from "@/src/lib/api/agentPlugins";

export function AgentPluginsSettingsContent() {
  const t = useTranslations("personalization");
  const loadError = t("pluginsError");
  const [plugins, setPlugins] = useState<AgentPlugin[]>([]);
  const [tab, setTab] = useState<"mcp" | "skill">("mcp");
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void listAgentPlugins()
      .then((items) => { if (active) setPlugins(items); })
      .catch(() => { if (active) setError(loadError); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [loadError]);

  useEffect(() => {
    const url = new URL(window.location.href);
    const outcome = url.searchParams.get("notion");
    if (!outcome) return;
    setNotice(outcome === "connected" ? t("notionConnected") :
      outcome === "cancelled" ? t("notionCancelled") : t("notionConnectFailed"));
    url.searchParams.delete("notion");
    window.history.replaceState(window.history.state, "", `${url.pathname}${url.search}${url.hash}`);
  }, [t]);

  const toggle = async (plugin: AgentPlugin) => {
    if (busyId) return;
    setBusyId(plugin.id);
    setError(null);
    try {
      const updated = await updateAgentPlugin(plugin.id, !plugin.enabled);
      setPlugins((previous) => previous.map((item) => item.id === updated.id ? updated : item));
    } catch {
      setError(t("pluginsChangeFailed"));
    } finally {
      setBusyId(null);
    }
  };

  const visible = plugins.filter((item) => item.kind === tab);

  const connectNotion = async () => {
    if (busyId) return;
    setBusyId("notion");
    setError(null);
    try {
      const { authorizationUrl } = await beginNotionConnection();
      if (new URL(authorizationUrl).origin !== "https://mcp.notion.com") throw new Error("Invalid Notion URL");
      window.location.assign(authorizationUrl);
    } catch {
      setError(t("notionConnectFailed"));
      setBusyId(null);
    }
  };

  const disconnectNotion = async () => {
    if (busyId) return;
    setBusyId("notion");
    setError(null);
    try {
      await disconnectNotionConnection();
      setPlugins((previous) => previous.map((item) => item.id === "notion"
        ? { ...item, enabled: false, connectionStatus: "disconnected", workspaceName: null } : item));
    } catch {
      setError(t("notionDisconnectFailed"));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="space-y-4" aria-label={t("plugins")}>
      <div role="tablist" aria-label={t("plugins")} className="inline-flex rounded-xl bg-muted p-1">
        {(["mcp", "skill"] as const).map((kind) => (
          <button key={kind} type="button" role="tab" aria-selected={tab === kind}
            className={`rounded-lg px-4 py-1.5 text-[13px] ${tab === kind ? "bg-card font-semibold shadow-sm" : "text-muted-foreground"}`}
            onClick={() => setTab(kind)}>
            {kind === "mcp" ? `${t("mcp")} ${t("plugins")}` : t("skills")}
          </button>
        ))}
      </div>

      <div role="tabpanel" className="space-y-3">
        {loading && <p role="status" className="text-[13px] text-muted-foreground">{t("pluginsLoading")}</p>}
        {!loading && visible.length === 0 && !error && (
          <p className="text-[13px] text-muted-foreground">{t("pluginsEmpty")}</p>
        )}
        {visible.map((plugin) => {
          const Icon = plugin.kind === "mcp" ? PlugZap : BookOpen;
          return (
            <article key={plugin.id} className="flex items-start gap-3 rounded-2xl border border-border bg-card p-4">
              <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-muted text-foreground">
                <Icon size={18} aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1 space-y-1">
                <h2 className="text-[14px] font-semibold">{plugin.name}</h2>
                <p className="text-[12px] leading-5 text-muted-foreground">{plugin.description}</p>
                {plugin.sourceUrl && (
                  plugin.sourceUrl.startsWith("https://docs.langchain.com/") ||
                  plugin.sourceUrl.startsWith("https://developers.notion.com/")
                ) && (
                  <a href={plugin.sourceUrl} target="_blank" rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-[12px] text-primary hover:underline">
                    {t("pluginsSource")} <ExternalLink size={12} aria-hidden="true" />
                  </a>
                )}
                {plugin.id === "notion" && (
                  <div className="space-y-2 pt-1 text-[12px] text-muted-foreground">
                    <p>{plugin.connectionStatus === "connected"
                      ? `${t("notionConnected")}${plugin.workspaceName ? ` · ${plugin.workspaceName}` : ""}`
                      : plugin.connectionStatus === "authorizing" ? t("notionAuthorizing")
                      : plugin.connectionStatus === "reconnect_required" ? t("notionReconnectRequired")
                      : t("notionDisconnected")}</p>
                    <div className="flex flex-wrap gap-2">
                      {plugin.connectionStatus !== "connected" && (
                        <button type="button" disabled={Boolean(busyId)} onClick={() => void connectNotion()}
                          className="rounded-lg border border-border px-3 py-1.5 text-foreground disabled:opacity-50">
                          {plugin.connectionStatus === "disconnected" ? t("notionConnect") : t("notionReconnect")}
                        </button>
                      )}
                      {plugin.connectionStatus !== "disconnected" && (
                        <button type="button" disabled={Boolean(busyId)} onClick={() => void disconnectNotion()}
                          className="rounded-lg border border-border px-3 py-1.5 text-foreground disabled:opacity-50">
                          {t("notionDisconnect")}
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
              <button type="button" aria-label={`${plugin.name} ${plugin.enabled ? t("enabled") : t("disabled")}`}
                aria-pressed={plugin.enabled} disabled={Boolean(busyId) || (plugin.id === "notion" && plugin.connectionStatus !== "connected")}
                className="shrink-0 rounded-lg border border-border px-3 py-1.5 text-[12px] disabled:opacity-50"
                onClick={() => void toggle(plugin)}>
                {plugin.enabled ? t("enabled") : t("disabled")}
              </button>
            </article>
          );
        })}
      </div>
      {notice && <p role="status" className="text-[12px] text-muted-foreground">{notice}</p>}
      {error && <p role="alert" className="text-[12px] text-destructive">{error}</p>}
    </section>
  );
}
