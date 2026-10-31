import {
  isSlidesSourceFileName,
  isTextDocumentKind,
} from "@/src/lib/files/projectFileKinds";

export type SlidesUploadIdentity = {
  type: "image" | "pdf" | "office" | "note" | "slides" | "sheet";
  documentId?: string | null;
};

export type PowerPointImportPlan = {
  isPowerPointImport: boolean;
  shouldNormalize: boolean;
  shouldDelaySidebarPublish: boolean;
};

export type PowerPointImportUploadedFile = SlidesUploadIdentity & {
  cacheKey: string;
  name: string;
  projectId?: string | null;
  url?: string | null;
};

export type PowerPointImportStageLogger = (
  stage: string,
  payload: Record<string, unknown>,
) => void;

type PowerPointImportRuntime = {
  getDocument: (
    documentId: string,
    opts: { includeVersions: boolean },
  ) => Promise<{
    draftContent: string | null;
    version: number | null;
  }>;
  normalizeMarkdownToSlides: (input: {
    sourceMarkdown: string;
    deckTitle: string;
    outputLanguage: string;
    themeName: string;
    mode?: "sync" | "flex";
  }) => Promise<{
    text: string;
  }>;
  updateDraft: (
    documentId: string,
    input: {
      version: number;
      draftContent: string;
    },
  ) => Promise<{
    draftContent: string | null;
    version: number | null;
  }>;
  persistFetchedNoteContent: (input: {
    projectId: string | null;
    cacheKey: string | null;
    url?: string | null;
    content: string;
    documentId?: string | null;
    version?: number | null;
  }) => Promise<void>;
  dispatchNoteContentUpdate: (detail: {
    cacheKey?: string;
    content?: string;
    documentId?: string | null;
    version?: number | null;
  }) => void;
};

function buildDeckTitleFromFileName(fileName: string) {
  return fileName
    .replace(/\.[^.]+$/, "")
    .replace(/(?:\.slide|\.slides)$/i, "")
    .trim() || "Untitled Deck";
}

function isApiErrorLike(error: unknown): error is { code?: number; message?: string } {
  return typeof error === "object" && error !== null;
}

function isVersionConflict(error: unknown) {
  if (isApiErrorLike(error)) {
    const code = typeof error.code === "number" ? error.code : null;
    const message = typeof error.message === "string" ? error.message : "";
    return code === 409 || /version conflict|版本冲突/i.test(message);
  }

  if (error instanceof Error) {
    return /version conflict|版本冲突/i.test(error.message);
  }

  return false;
}

export function resolvePowerPointImportPlan(input: {
  originalFileName: string;
  uploadedFile: SlidesUploadIdentity;
}): PowerPointImportPlan {
  const isPowerPointImport = isSlidesSourceFileName(input.originalFileName);
  return {
    isPowerPointImport,
    shouldNormalize:
      isPowerPointImport &&
      isTextDocumentKind(input.uploadedFile.type) &&
      Boolean(input.uploadedFile.documentId),
    shouldDelaySidebarPublish: isPowerPointImport,
  };
}

export function shouldAutoNormalizeImportedPowerPoint(input: {
  originalFileName: string;
  uploadedFile: SlidesUploadIdentity;
}) {
  return resolvePowerPointImportPlan(input).shouldNormalize;
}

export async function runPowerPointImportPostProcess(input: {
  originalFileName: string;
  uploadedFile: PowerPointImportUploadedFile;
  runtime: PowerPointImportRuntime;
  logStage?: PowerPointImportStageLogger;
}) {
  const plan = resolvePowerPointImportPlan(input);
  const logStage = input.logStage ?? (() => {});

  if (plan.isPowerPointImport) {
    logStage("post-process-check", {
      originalFileName: input.originalFileName,
      uploadedType: input.uploadedFile.type,
      cacheKey: input.uploadedFile.cacheKey,
      documentId: input.uploadedFile.documentId ?? null,
      shouldNormalize: plan.shouldNormalize,
    });
  }

  if (!plan.shouldNormalize) {
    return;
  }

  const { uploadedFile } = input;
  const documentId = uploadedFile.documentId;
  if (!documentId) {
    logStage("post-process-skip-missing-document-id", {
      originalFileName: input.originalFileName,
      cacheKey: uploadedFile.cacheKey,
      uploadedType: uploadedFile.type,
    });
    return;
  }

  try {
    logStage("fetch-draft-start", {
      originalFileName: input.originalFileName,
      cacheKey: uploadedFile.cacheKey,
      documentId,
      uploadedType: uploadedFile.type,
    });
    const dto = await input.runtime.getDocument(documentId, { includeVersions: false });
    const draftContent = dto.draftContent ?? "";
    const version = dto.version ?? null;
    logStage("fetch-draft-finished", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      version,
      draftLength: draftContent.length,
      hasDraftContent: draftContent.trim().length > 0,
    });
    if (!draftContent.trim() || version === null) {
      logStage("post-process-skip-empty-draft", {
        cacheKey: uploadedFile.cacheKey,
        documentId,
        version,
        draftLength: draftContent.length,
      });
      return;
    }

    logStage("normalize-to-slides-start", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      draftLength: draftContent.length,
      deckTitle: buildDeckTitleFromFileName(uploadedFile.name),
    });
    // Shares the same normalize endpoint as Doc -> Slide conversion, but PPT/PPTX
    // imports must stay on the sync prompt because this path is trying to
    // preserve imported structure with higher fidelity before we write back into
    // the uploaded document's draft.
    const normalized = await input.runtime.normalizeMarkdownToSlides({
      sourceMarkdown: draftContent,
      deckTitle: buildDeckTitleFromFileName(uploadedFile.name),
      outputLanguage: "same as source_markdown",
      themeName: "default",
      mode: "sync",
    });
    const nextContent = normalized.text.trim();
    logStage("normalize-to-slides-finished", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      nextLength: normalized.text.length,
      changed: nextContent !== draftContent.trim(),
      hasNextContent: nextContent.length > 0,
    });
    if (!nextContent || nextContent === draftContent.trim()) {
      logStage("post-process-skip-no-change", {
        cacheKey: uploadedFile.cacheKey,
        documentId,
      });
      return;
    }

    logStage("update-draft-start", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      version,
      nextLength: normalized.text.length,
    });
    const updated = await input.runtime.updateDraft(documentId, {
      version,
      draftContent: normalized.text,
    });
    logStage("update-draft-finished", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      previousVersion: version,
      nextVersion: updated.version ?? version,
      persistedLength: (updated.draftContent ?? normalized.text).length,
    });

    await input.runtime.persistFetchedNoteContent({
      projectId: uploadedFile.projectId ?? null,
      cacheKey: uploadedFile.cacheKey,
      url: uploadedFile.url ?? null,
      content: updated.draftContent ?? normalized.text,
      documentId,
      version: updated.version ?? version,
    });

    input.runtime.dispatchNoteContentUpdate({
      cacheKey: uploadedFile.cacheKey,
      content: updated.draftContent ?? normalized.text,
      documentId,
      version: updated.version ?? version,
    });
    logStage("post-process-complete", {
      cacheKey: uploadedFile.cacheKey,
      documentId,
      version: updated.version ?? version,
    });
  } catch (error) {
    if (isVersionConflict(error)) {
      logStage("post-process-version-conflict", {
        cacheKey: uploadedFile.cacheKey,
        documentId,
      });
      throw error;
    }

    console.warn(
      "[uploadProjectFile] autoNormalizeImportedSlides failed",
      {
        fileName: input.originalFileName,
        cacheKey: uploadedFile.cacheKey,
        documentId,
        error,
      },
    );
    throw error;
  }
}
