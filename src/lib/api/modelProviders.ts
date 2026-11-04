import { apiFetchWithAuth } from "@/src/lib/api/fetcherWithAuth";

export type ModelProviderId = "openai" | "deepseek" | "openrouter";

export type ModelCredentialStatus = {
  providerId: ModelProviderId;
  configured: boolean;
  maskedKey: string | null;
};

export type ModelCredentialTestResult = {
  providerId: ModelProviderId;
  modelId: string;
  status: "valid" | "invalid" | "unavailable";
  message: string;
};

export type AvailableWorkbenchModel = {
  id: string;
  providerId: string;
  displayName: string;
  description: string | null;
  askSupported: boolean;
  agentSupported: boolean;
  imageSupported?: boolean;
};

type AvailableWorkbenchModelResponse = {
  id?: string | null;
  providerId?: string | null;
  displayName?: string | null;
  description?: string | null;
  askSupported?: boolean | null;
  agentSupported?: boolean | null;
  imageSupported?: boolean | null;
};

export const MODEL_CREDENTIALS_CHANGED_EVENT = "agentworkbench:model-credentials-changed";

export function notifyModelCredentialsChanged(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(MODEL_CREDENTIALS_CHANGED_EVENT));
  }
}

export async function listModelCredentialStatuses(): Promise<ModelCredentialStatus[]> {
  return apiFetchWithAuth<ModelCredentialStatus[]>("/user/model-credentials", {
    cache: "no-store",
    requestPolicy: "query",
  });
}

export async function saveModelCredential(providerId: ModelProviderId, apiKey: string): Promise<ModelCredentialStatus> {
  return apiFetchWithAuth<ModelCredentialStatus>(`/user/model-credentials/${providerId}`, {
    method: "PUT",
    body: { apiKey },
    requestPolicy: "mutation",
  });
}

export async function testModelCredential(providerId: ModelProviderId, apiKey?: string): Promise<ModelCredentialTestResult> {
  return apiFetchWithAuth<ModelCredentialTestResult>(`/user/model-credentials/${providerId}/test`, {
    method: "POST",
    body: apiKey === undefined ? {} : { apiKey },
    requestPolicy: "mutation",
  });
}

export async function deleteModelCredential(providerId: ModelProviderId): Promise<boolean> {
  return apiFetchWithAuth<boolean>(`/user/model-credentials/${providerId}`, {
    method: "DELETE",
    requestPolicy: "mutation",
  });
}

export async function listWorkbenchModels(): Promise<AvailableWorkbenchModel[]> {
  const rows = await apiFetchWithAuth<AvailableWorkbenchModelResponse[]>("/models/available", {
    cache: "no-store",
    requestPolicy: "query",
  });
  return (rows ?? []).flatMap((model): AvailableWorkbenchModel[] => {
    const id = model.id?.trim();
    if (!id) return [];
    const providerId = model.providerId?.trim() || (id.startsWith("deepseek") ? "deepseek" : id.split("/")[0] || "");
    return [{
      id,
      providerId,
      displayName: model.displayName?.trim() || id,
      description: model.description?.trim() || null,
      askSupported: model.askSupported !== false,
      // Older catalog responses have no capability flags. Keep a conservative
      // fallback for DeepSeek when connected to a backend predating tool support.
      agentSupported: model.agentSupported ?? providerId !== "deepseek",
      imageSupported: model.imageSupported ?? (providerId === "openrouter"
        && (id.startsWith("openai/") || id.startsWith("openrouter/openai/"))),
    }];
  });
}
