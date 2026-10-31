import {
  resolvePowerPointImportPlan,
  runPowerPointImportPostProcess,
  type PowerPointImportStageLogger,
} from "./powerPointSlidesPostProcess";

export type UploadedProjectFileForPipeline = {
  type: "image" | "pdf" | "office" | "note" | "slides" | "sheet";
  cacheKey: string;
  url?: string;
  name: string;
  rawName?: string;
  projectId?: string | null;
  documentId?: string;
  sizeBytes?: number;
  local?: boolean;
};

type PowerPointImportPostProcessRuntime = Parameters<
  typeof runPowerPointImportPostProcess
>[0]["runtime"];

export type ProjectFileUploadPipelineRuntime = {
  publishUploadedProjectFileToMaterials: (
    projectId: string,
    file: UploadedProjectFileForPipeline,
  ) => Promise<void>;
  logPowerPointImportStage?: PowerPointImportStageLogger;
  powerPointImportPostProcessRuntime?: PowerPointImportPostProcessRuntime;
};

export async function completeUploadedProjectFileUpload(input: {
  originalFileName: string;
  projectId?: string | null;
  uploadedFile: UploadedProjectFileForPipeline;
}, runtime: ProjectFileUploadPipelineRuntime) {
  const plan = resolvePowerPointImportPlan({
    originalFileName: input.originalFileName,
    uploadedFile: input.uploadedFile,
  });

  // Product boundary for now: keep the AI post-process implementation on disk,
  // but do not attach uploads to it unless a runtime is explicitly wired in.
  if (!runtime.powerPointImportPostProcessRuntime || !plan.shouldNormalize) {
    if (input.projectId) {
      await runtime.publishUploadedProjectFileToMaterials(
        input.projectId,
        input.uploadedFile,
      );
    }
    return;
  }

  if (input.projectId && !plan.shouldDelaySidebarPublish) {
    await runtime.publishUploadedProjectFileToMaterials(
      input.projectId,
      input.uploadedFile,
    );
  }

  await runPowerPointImportPostProcess({
    originalFileName: input.originalFileName,
    uploadedFile: input.uploadedFile,
    runtime: runtime.powerPointImportPostProcessRuntime,
    logStage: runtime.logPowerPointImportStage,
  });

  if (input.projectId && plan.shouldDelaySidebarPublish) {
    runtime.logPowerPointImportStage?.("publish-to-materials", {
      cacheKey: input.uploadedFile.cacheKey,
      documentId: input.uploadedFile.documentId ?? null,
      uploadedType: input.uploadedFile.type,
    });
    await runtime.publishUploadedProjectFileToMaterials(
      input.projectId,
      input.uploadedFile,
    );
  }
}
