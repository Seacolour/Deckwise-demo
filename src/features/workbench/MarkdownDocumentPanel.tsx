"use client";

import { useEffect, useRef, useState, type DragEvent, type FormEvent } from "react";
import { ArrowLeft, ExternalLink, Eye, File as FileIcon, FilePlus2, FileText, Image, Pencil, RefreshCw, Save, Upload, X } from "lucide-react";
import type { DocumentDTO } from "@/src/lib/api/documents";
import { getWorkbenchFilePreviewUrl, getWorkbenchInlinePreviewPath, type WorkbenchFile } from "./workbenchApi";
import { loadWorkbenchPreviewFile } from "./workbenchFileCache";
import { decodeTextBytes } from "@/src/lib/files/decodeTextBytes";
import { WorkbenchPdfPreview } from "./WorkbenchPdfPreview";
import { writeWorkspaceFileDrag } from "./workspaceFileDrag";
import { WorkbenchMarkdown } from "./WorkbenchMarkdown";
import styles from "./markdownDocumentPanel.module.css";

type Props = {
  open: boolean;
  projectId: string | null;
  projectName: string | null;
  userKey: string;
  view: "files" | "preview" | "editor";
  documents: DocumentDTO[];
  files: WorkbenchFile[];
  filesLoading: boolean;
  fileUploading: boolean;
  fileError: string | null;
  activeDocument: DocumentDTO | null;
  activeDocumentId: string | null;
  draft: string;
  loading: boolean;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onViewChange: (view: "files" | "preview" | "editor") => void;
  onCreate: (title: string) => Promise<void>;
  onUploadFile: (file: File) => Promise<void>;
  onRefreshFiles: () => void;
  onSelect: (id: string) => boolean;
  onChange: (value: string) => void;
  onSave: () => Promise<unknown>;
  onRefresh: () => void;
};

export function MarkdownDocumentPanel({
  open, projectId, projectName, userKey, view, documents, files, filesLoading, fileUploading, fileError,
  activeDocument, activeDocumentId, draft, loading, busy, error,
  onClose, onViewChange, onCreate, onUploadFile, onRefreshFiles, onSelect, onChange, onSave, onRefresh,
}: Props) {
  const [newTitle, setNewTitle] = useState("");
  const [creating, setCreating] = useState(false);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [previewFile, setPreviewFile] = useState<WorkbenchFile | null>(null);
  const [filePreviewResult, setFilePreviewResult] = useState<{
    key: string; blob?: Blob; text?: string; objectUrl?: string; originalUrl?: string; error?: string;
  } | null>(null);
  const uploadRef = useRef<HTMLInputElement | null>(null);
  const dirty = Boolean(activeDocument && draft !== (activeDocument.draftContent ?? ""));
  const documentIds = new Set(documents.map((document) => document.id == null ? "" : String(document.id)));
  const physicalFiles = files.filter((file) => !file.documentId || !documentIds.has(file.documentId));

  useEffect(() => {
    if (view !== "preview" || !previewFile || !projectId) return;
    let cancelled = false;
    const controller = new AbortController();
    let objectUrl: string | null = null;
    const key = `${userKey}:${projectId}:${previewFile.id}:${previewFile.cacheKey}`;
    const inlinePath = getWorkbenchInlinePreviewPath(projectId, previewFile);
    void (async () => {
      try {
        if (!inlinePath) {
          const originalUrl = await getWorkbenchFilePreviewUrl(projectId, previewFile.id);
          if (!cancelled) setFilePreviewResult({ key, originalUrl });
          return;
        }
        const blob = await loadWorkbenchPreviewFile(userKey, projectId, previewFile, controller.signal);
        if (cancelled) return;
        const isText = /\.(?:txt|json|md)$/i.test(previewFile.name);
        const text = isText ? decodeTextBytes(new Uint8Array(await blob.arrayBuffer())) : undefined;
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setFilePreviewResult({ key, blob, text, objectUrl });
      } catch (cause) {
        if (!cancelled) setFilePreviewResult({
          key, error: cause instanceof Error ? cause.message : "文件预览失败",
        });
      }
    })();
    return () => {
      cancelled = true;
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [projectId, previewFile, userKey, view]);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    const title = newTitle.trim();
    if (!title || busy) return;
    try {
      await onCreate(title);
      setNewTitle("");
      setCreating(false);
      onViewChange("editor");
    } catch {
      // The parent displays the request error and keeps the title for retry.
    }
  };

  const upload = async (file: File | undefined) => {
    if (!file) return;
    try { await onUploadFile(file); }
    catch { /* Parent shows the upload error. */ }
    if (uploadRef.current) uploadRef.current.value = "";
  };

  const dragDocument = (event: DragEvent<HTMLDivElement>, document: DocumentDTO) => {
    if (!projectId || document.id == null) return;
    const name = document.title?.trim() || `文档 ${document.id}`;
    writeWorkspaceFileDrag(event.dataTransfer, {
      kind: "document", projectId, documentId: String(document.id), name,
    });
  };

  const dragFile = (event: DragEvent<HTMLDivElement>, file: WorkbenchFile) => {
    if (!projectId) return;
    writeWorkspaceFileDrag(event.dataTransfer, {
      kind: "file", projectId, fileId: file.id, cacheKey: file.cacheKey,
      name: file.name, image: file.image,
    });
  };

  const openDocument = (id: string, nextView: "preview" | "editor") => {
    if (!onSelect(id)) return;
    setPreviewFile(null);
    onViewChange(nextView);
  };

  const openPhysicalFile = (file: WorkbenchFile) => {
    setFilePreviewResult(null);
    setPreviewFile(file);
    onViewChange("preview");
  };

  const displayDocumentName = (document: DocumentDTO) => {
    const name = document.title?.trim() || `文档 ${document.id}`;
    return name.toLowerCase().endsWith(".md") ? name : `${name}.md`;
  };

  const selectedDocument = documents.find((document) => String(document.id) === activeDocumentId);
  const detailName = previewFile?.name ?? (selectedDocument ? displayDocumentName(selectedDocument) : "文档");
  const documentReady = !previewFile && activeDocument && activeDocument.id != null
    && String(activeDocument.id) === activeDocumentId;
  const previewKey = projectId && previewFile ? `${userKey}:${projectId}:${previewFile.id}:${previewFile.cacheKey}` : null;
  const previewResult = filePreviewResult?.key === previewKey ? filePreviewResult : null;
  const previewLoading = Boolean(previewFile && !previewResult);
  const previewBlob = previewResult?.blob;
  const previewText = previewResult?.text;
  const previewObjectUrl = previewResult?.objectUrl;
  const originalUrl = previewResult?.originalUrl;
  const previewError = previewResult?.error;

  return (
    <aside className={`${styles.panel} ${open ? "" : styles.closed}`} aria-label="工作区文件">
      <header className={styles.header}>
        <div><strong>文件</strong><span>{projectName || "当前工作区"}</span></div>
        <button type="button" className={styles.iconButton} onClick={onClose} aria-label="关闭文件面板"><X size={18} /></button>
      </header>
      {view === "files" ? (
        <>
          <div className={styles.fileToolbar}>
            <span>工作区文件</span>
            <div>
              <button type="button" className={styles.iconButton} onClick={onRefreshFiles} disabled={!projectId || filesLoading || fileUploading}
                aria-label="刷新文件列表" title="刷新文件列表"><RefreshCw size={16} /></button>
              <button type="button" className={styles.uploadButton} onClick={() => uploadRef.current?.click()}
                disabled={busy || fileUploading} aria-label="上传文件" title="上传文件到工作区"><Upload size={15} />上传文件</button>
              <button type="button" className={styles.iconButton} onClick={() => setCreating((previous) => !previous)}
                disabled={busy} aria-label="新建 Markdown 文档" title="新建 Markdown 文档"><FilePlus2 size={16} /></button>
            </div>
          </div>
          <input ref={uploadRef} className={styles.hiddenInput} type="file" aria-label="选择上传文件"
            accept=".md,.txt,.json,.pdf,.docx,.png,.jpg,.jpeg,.webp,.gif"
            onChange={(event) => void upload(event.target.files?.[0])} />
          {creating && <form className={styles.createForm} onSubmit={(event) => void create(event)}>
            <input aria-label="新文档标题" value={newTitle} onChange={(event) => setNewTitle(event.target.value)}
              placeholder="新文档标题" maxLength={200} disabled={busy} />
            <button type="submit" disabled={busy || !newTitle.trim()} aria-label="创建文档"><FilePlus2 size={17} /></button>
          </form>}
          {fileError && <p className={styles.error} role="alert">{fileError}</p>}
          {error && <p className={styles.error} role="alert">{error}</p>}
          {filesLoading && <p className={styles.note}>正在载入文件…</p>}
          {fileUploading && <p className={styles.note}>正在上传文件…</p>}
          <div className={styles.fileList} aria-label="当前工作区文件">
            {documents.filter((document) => document.id != null).map((document) => {
              const id = String(document.id);
              const displayName = displayDocumentName(document);
              return <div key={`document-${id}`}
                className={`${styles.fileRow} ${selectedKey === `document-${id}` ? styles.fileRowSelected : ""}`}
                draggable={!busy} onDragStart={(event) => dragDocument(event, document)}>
                <button type="button" className={styles.fileMain} disabled={busy}
                  aria-label={`文件 ${displayName}`} title="双击预览；拖到输入框可交给 Agent"
                  onClick={() => setSelectedKey(`document-${id}`)}
                  onDoubleClick={() => openDocument(id, "preview")}>
                  <span className={`${styles.fileGlyph} ${styles.markdownGlyph}`}><FileText size={17} /></span>
                  <span className={styles.fileName}>{displayName}</span>
                  <span className={styles.fileMeta}>MD</span>
                </button>
                <div className={styles.fileActions}>
                  <button type="button" disabled={busy} aria-label={`预览 ${displayName}`} title="预览"
                    onClick={() => openDocument(id, "preview")}><Eye size={15} /></button>
                  <button type="button" disabled={busy} aria-label={`编辑 ${displayName}`} title="编辑"
                    onClick={() => openDocument(id, "editor")}><Pencil size={15} /></button>
                </div>
              </div>;
            })}
            {physicalFiles.map((file) => {
              const Icon = file.image ? Image : /\.pdf$/i.test(file.name) ? FileText : FileIcon;
              return <div key={`file-${file.id}`}
                className={`${styles.fileRow} ${selectedKey === `file-${file.id}` ? styles.fileRowSelected : ""}`}
                draggable={!busy} onDragStart={(event) => dragFile(event, file)}>
                <button type="button" className={styles.fileMain} disabled={busy}
                  aria-label={`文件 ${file.name}`} title="双击预览；拖到输入框可交给 Agent"
                  onClick={() => setSelectedKey(`file-${file.id}`)}
                  onDoubleClick={() => openPhysicalFile(file)}>
                  <span className={styles.fileGlyph}><Icon size={17} /></span>
                  <span className={styles.fileName}>{file.name}</span>
                  {file.sizeBytes != null && <span className={styles.fileMeta}>{file.sizeBytes < 1024
                    ? `${file.sizeBytes} B` : file.sizeBytes < 1024 * 1024
                      ? `${Math.round(file.sizeBytes / 1024)} KB` : `${(file.sizeBytes / 1024 / 1024).toFixed(1)} MB`}</span>}
                </button>
                <div className={styles.fileActions}>
                  <button type="button" disabled={busy} aria-label={`预览 ${file.name}`} title="预览"
                    onClick={() => openPhysicalFile(file)}><Eye size={15} /></button>
                </div>
              </div>;
            })}
            {!filesLoading && documents.length === 0 && physicalFiles.length === 0 && (
              <p className={styles.emptyFiles}>{projectId ? "这里还没有文件。创建 Markdown 文档或上传材料。" : "先创建工作区或 Markdown 文档。"}</p>
            )}
          </div>
          <p className={styles.fileHint}>将文件拖到消息输入框，即可在本轮对话中引用。</p>
        </>
      ) : (
        <>
          <div className={styles.detailToolbar}>
            <button type="button" className={styles.backButton} onClick={() => onViewChange("files")}
              aria-label="返回文件列表"><ArrowLeft size={16} />文件</button>
            <strong title={detailName}>{detailName}</strong>
            {!previewFile && documentReady && (
              <div className={styles.detailActions}>
                <button type="button" className={view === "preview" ? styles.activeDetailAction : ""}
                  onClick={() => onViewChange("preview")} aria-label="预览当前文档"><Eye size={15} />预览</button>
                <button type="button" className={view === "editor" ? styles.activeDetailAction : ""}
                  onClick={() => onViewChange("editor")} aria-label="编辑当前文档"><Pencil size={15} />编辑</button>
              </div>
            )}
          </div>
          {error && <p className={styles.error} role="alert">{error}</p>}
          {previewFile ? (
            <div className={styles.physicalPreview}>
              {previewLoading && <p className={styles.note}>正在获取预览地址…</p>}
              {previewError && <p className={styles.error} role="alert">{previewError}</p>}
              {previewBlob && /\.pdf$/i.test(previewFile.name) && <WorkbenchPdfPreview file={previewBlob} />}
              {previewText !== undefined && <pre className={styles.textPreview} aria-label="文本预览">{previewText}</pre>}
              {previewObjectUrl && previewFile.image && <iframe className={styles.fileFrame}
                src={previewObjectUrl} title={`预览 ${previewFile.name}`} />}
              {previewObjectUrl && <a className={styles.openFileLink} href={previewObjectUrl} download={previewFile.name}>
                <ExternalLink size={14} />下载原文件</a>}
              {originalUrl && <>
                <p className={styles.note}>浏览器暂不支持直接预览此格式，可以打开原文件查看。</p>
                <a className={styles.openFileLink} href={originalUrl} target="_blank" rel="noopener noreferrer"
                  referrerPolicy="no-referrer"><ExternalLink size={14} />打开原文件</a>
              </>}
            </div>
          ) : loading || (activeDocumentId && !documentReady) ? <p className={styles.note}>正在载入文档…</p> : documentReady ? <>
          <div className={styles.documentHeader}>
            <div><h2>{activeDocument.title?.trim() || "未命名文档"}</h2><span>版本 {activeDocument.version ?? "?"}{dirty ? " · 未保存" : " · 已保存"}</span></div>
            <button type="button" className={styles.iconButton} onClick={onRefresh} disabled={busy}
              aria-label={dirty ? "放弃修改并刷新文档" : "刷新文档"}
              title={dirty ? "放弃修改并刷新文档" : "刷新文档"}><RefreshCw size={16} /></button>
          </div>
          {view === "preview" ? <div className={styles.preview} aria-label="Markdown 预览"><WorkbenchMarkdown text={draft} /></div> : (
            <textarea className={styles.editor} aria-label="Markdown 内容" value={draft}
              onChange={(event) => onChange(event.target.value)} disabled={busy} spellCheck={false} />
          )}
          {view === "editor" && <footer className={styles.footer}>
            <span>拖入消息输入框，才会在本轮对话中引用此文档。</span>
            <button type="button" onClick={() => void onSave()} disabled={busy || !dirty}><Save size={15} />保存</button>
          </footer>}
          </> : <p className={styles.note}>从文件列表选择文档。</p>}
        </>
      )}
    </aside>
  );
}
