/**
 * Documents API
 *
 * Standard REST transport now comes from the generated Orval client.
 * This module remains as a thin app-facing compatibility layer so existing
 * call sites can keep their stable imports and helper semantics.
 */

import {
  createDocument as createDocumentRequest,
  deleteDocument as deleteDocumentRequest,
  getDocument as getDocumentRequest,
  listDocument as listDocumentRequest,
  updateDraft as updateDraftRequest,
} from "@/src/lib/api/generated/documents";
import { unwrapGeneratedPayload } from "./generatedCompat";
import { runSharedRead } from "./sharedReads";
import type { DocumentCreateRequest as GeneratedDocumentCreateRequest } from "@/src/lib/types/all/documentCreateRequest";
import type { DocumentDTO as GeneratedDocumentDTO } from "@/src/lib/types/all/documentDTO";
import type { DocumentDTODocumentType as GeneratedDocumentType } from "@/src/lib/types/all/documentDTODocumentType";
import type { DocumentDraftUpdateRequest as GeneratedDocumentDraftUpdateRequest } from "@/src/lib/types/all/documentDraftUpdateRequest";
import type { DocumentVersionDTO as GeneratedDocumentVersionDTO } from "@/src/lib/types/all/documentVersionDTO";

export type DocumentApiId = string | number;
export type DocumentType = GeneratedDocumentType | (string & {});

export type DocumentDraftUpdateRequest = GeneratedDocumentDraftUpdateRequest;
export type DocumentCreateRequest = GeneratedDocumentCreateRequest;

export type DocumentVersionDTO = GeneratedDocumentVersionDTO;
export type DocumentDTO = GeneratedDocumentDTO;

/**
 * GET /documents/{documentId}
 * 获取文档详情（含可选版本历史）
 */
export async function getDocument(
  documentId: DocumentApiId,
  opts?: { includeVersions?: boolean; signal?: AbortSignal }
): Promise<DocumentDTO> {
  try {
    const includeVersionsKey =
      opts?.includeVersions === undefined ? "unset" : String(opts.includeVersions);

    return await runSharedRead(
      `document:${String(documentId)}:includeVersions:${includeVersionsKey}`,
      (sharedSignal) =>
        getDocumentRequest(String(documentId), {
          ...(opts?.includeVersions !== undefined
            ? { includeVersions: opts.includeVersions }
            : {}),
        }, {
          signal: sharedSignal,
        }).then((response) => unwrapGeneratedPayload<DocumentDTO>(response)),
      { signal: opts?.signal },
    );
  } catch (err) {
    console.error("[documents] getDocument FAILED", documentId, err);
    throw err;
  }
}

/**
 * PUT /documents/{documentId}/draft
 * 更新草稿内容（高频操作，带 version 乐观锁）
 */
export async function updateDraft(
  documentId: DocumentApiId,
  body: DocumentDraftUpdateRequest
): Promise<DocumentDTO> {
  const response = await updateDraftRequest(String(documentId), body);
  return unwrapGeneratedPayload<DocumentDTO>(response);
}

/**
 * DELETE /documents/{documentId}
 * 删除文档
 */
export async function deleteDocument(documentId: DocumentApiId): Promise<boolean> {
  const response = await deleteDocumentRequest(String(documentId));
  return Boolean(unwrapGeneratedPayload<boolean>(response));
}

/**
 * GET /documents/list
 * 列出项目下的文档
 */
export async function listDocuments(
  projectId: DocumentApiId,
  opts?: { offset?: number; limit?: number }
): Promise<DocumentDTO[]> {
  const response = await listDocumentRequest({
    projectId: String(projectId),
    ...(opts?.offset !== undefined ? { offset: opts.offset } : {}),
    ...(opts?.limit !== undefined ? { limit: opts.limit } : {}),
  });
  return unwrapGeneratedPayload<DocumentDTO[]>(response);
}

/**
 * POST /documents/create
 * 创建新文档
 */
export async function createDocument(
  body: DocumentCreateRequest
): Promise<DocumentDTO> {
  const response = await createDocumentRequest(body);
  return unwrapGeneratedPayload<DocumentDTO>(response);
}
