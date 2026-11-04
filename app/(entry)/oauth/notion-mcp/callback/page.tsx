"use client";

import { useEffect, useRef, useState } from "react";
import { completeNotionConnection } from "@/src/lib/api/agentPlugins";

const SETTINGS_PATH = "/app/settings/plugins";

export default function NotionMcpCallbackPage() {
  const started = useRef(false);
  const [message, setMessage] = useState("正在完成 Notion 授权…");

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const state = params.get("state");
    const denied = params.has("error");
    window.history.replaceState(window.history.state, "", window.location.pathname);
    if (denied || !code || !state) {
      window.location.replace(`${SETTINGS_PATH}?notion=cancelled`);
      return;
    }
    void completeNotionConnection(code, state)
      .then(() => window.location.replace(`${SETTINGS_PATH}?notion=connected`))
      .catch(() => {
        setMessage("Notion 授权未完成，请返回插件页重试。");
        window.location.replace(`${SETTINGS_PATH}?notion=error`);
      });
  }, []);

  return (
    <main className="flex min-h-dvh items-center justify-center bg-background px-6 text-foreground">
      <div className="space-y-4 text-center">
        <p role="status">{message}</p>
        <a className="text-primary underline" href={SETTINGS_PATH}>返回插件页</a>
      </div>
    </main>
  );
}
