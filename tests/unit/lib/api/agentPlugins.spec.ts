import { beforeEach, describe, expect, it, vi } from "vitest";

const fetchMock = vi.hoisted(() => vi.fn());
vi.mock("@/src/lib/api/fetcherWithAuth", () => ({ apiFetchWithAuth: fetchMock }));

import { beginNotionConnection, completeNotionConnection, disconnectNotionConnection,
  listAgentPlugins, updateAgentPlugin } from "@/src/lib/api/agentPlugins";

describe("Agent plugin API", () => {
  beforeEach(() => fetchMock.mockReset());

  it("lists the current user's plugins and persists a switch", async () => {
    fetchMock.mockResolvedValueOnce([]).mockResolvedValueOnce({ id: "skill:document-review", enabled: false });
    await listAgentPlugins();
    await updateAgentPlugin("skill:document-review", false);
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/user/agent-plugins", {
      cache: "no-store", requestPolicy: "query",
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/user/agent-plugins/skill%3Adocument-review", {
      method: "PUT", body: { enabled: false }, requestPolicy: "mutation",
    });
  });

  it("uses authenticated mutation endpoints for the Notion connection", async () => {
    fetchMock.mockResolvedValue({});
    await beginNotionConnection();
    await completeNotionConnection("code", "state");
    await disconnectNotionConnection();
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/user/agent-plugins/notion/connect", {
      method: "POST", requestPolicy: "mutation",
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/user/agent-plugins/notion/callback", {
      method: "POST", body: { code: "code", state: "state" }, requestPolicy: "mutation",
    });
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/user/agent-plugins/notion/connection", {
      method: "DELETE", requestPolicy: "mutation",
    });
  });
});
