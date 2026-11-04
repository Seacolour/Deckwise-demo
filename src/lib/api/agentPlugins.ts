import { apiFetchWithAuth } from "@/src/lib/api/fetcherWithAuth";

export type AgentPlugin = {
  id: string;
  kind: "mcp" | "skill";
  name: string;
  description: string;
  enabled: boolean;
  sourceUrl: string | null;
  connectionStatus?: "disconnected" | "authorizing" | "connected" | "reconnect_required" | null;
  workspaceName?: string | null;
};

export async function listAgentPlugins(): Promise<AgentPlugin[]> {
  return apiFetchWithAuth<AgentPlugin[]>("/user/agent-plugins", {
    cache: "no-store",
    requestPolicy: "query",
  });
}

export async function updateAgentPlugin(id: string, enabled: boolean): Promise<AgentPlugin> {
  return apiFetchWithAuth<AgentPlugin>(`/user/agent-plugins/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: { enabled },
    requestPolicy: "mutation",
  });
}

export async function beginNotionConnection(): Promise<{ authorizationUrl: string }> {
  return apiFetchWithAuth<{ authorizationUrl: string }>("/user/agent-plugins/notion/connect", {
    method: "POST", requestPolicy: "mutation",
  });
}

export async function completeNotionConnection(code: string, state: string): Promise<AgentPlugin> {
  return apiFetchWithAuth<AgentPlugin>("/user/agent-plugins/notion/callback", {
    method: "POST", body: { code, state }, requestPolicy: "mutation",
  });
}

export async function disconnectNotionConnection(): Promise<boolean> {
  return apiFetchWithAuth<boolean>("/user/agent-plugins/notion/connection", {
    method: "DELETE", requestPolicy: "mutation",
  });
}
