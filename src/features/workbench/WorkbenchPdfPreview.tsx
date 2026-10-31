"use client";

import { useEffect, useRef, useState } from "react";
import type { PDFDocumentProxy, PDFDocumentLoadingTask, RenderTask } from "pdfjs-dist";
import styles from "./markdownDocumentPanel.module.css";

type LoadedPdf = { source: Blob; document: PDFDocumentProxy };

export function WorkbenchPdfPreview({ file }: { file: Blob }) {
  const [loaded, setLoaded] = useState<LoadedPdf | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const viewportRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    let loadingTask: PDFDocumentLoadingTask | null = null;
    void (async () => {
      try {
        const pdfjs = await import("pdfjs-dist");
        pdfjs.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();
        loadingTask = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) });
        const document = await loadingTask.promise;
        if (!cancelled) setLoaded({ source: file, document });
      } catch {
        if (!cancelled) setError("PDF 解析失败，请打开原文件查看");
      }
    })();
    return () => {
      cancelled = true;
      if (loadingTask) void loadingTask.destroy();
    };
  }, [file]);

  const current = loaded?.source === file ? loaded.document : null;
  const currentPage = current ? Math.min(pageNumber, current.numPages) : 1;

  useEffect(() => {
    if (!current) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    let cancelled = false;
    let task: RenderTask | null = null;
    void (async () => {
      try {
        const page = await current.getPage(currentPage);
        if (cancelled) return;
        const unit = page.getViewport({ scale: 1 });
        const width = Math.max(260, (viewportRef.current?.clientWidth ?? 560) - 24);
        const scale = Math.min(2, width / unit.width);
        const pixelRatio = Math.min(2, window.devicePixelRatio || 1);
        const viewport = page.getViewport({ scale: scale * pixelRatio });
        canvas.width = Math.ceil(viewport.width);
        canvas.height = Math.ceil(viewport.height);
        canvas.style.width = `${Math.ceil(viewport.width / pixelRatio)}px`;
        canvas.style.height = `${Math.ceil(viewport.height / pixelRatio)}px`;
        task = page.render({ canvas, viewport });
        await task.promise;
      } catch {
        if (!cancelled) setError("PDF 页面渲染失败，请打开原文件查看");
      }
    })();
    return () => {
      cancelled = true;
      if (task) task.cancel();
    };
  }, [current, currentPage]);

  return <div className={styles.pdfViewer} aria-label="PDF 预览">
    {error && <p className={styles.error} role="alert">{error}</p>}
    {!current && !error && <p className={styles.note}>正在解析 PDF…</p>}
    {current && <>
      <div className={styles.pdfToolbar}>
        <button type="button" onClick={() => setPageNumber(Math.max(1, currentPage - 1))} disabled={currentPage <= 1}>上一页</button>
        <span>{currentPage} / {current.numPages}</span>
        <button type="button" onClick={() => setPageNumber(Math.min(current.numPages, currentPage + 1))}
          disabled={currentPage >= current.numPages}>下一页</button>
      </div>
      <div className={styles.pdfViewport} ref={viewportRef}><canvas ref={canvasRef} aria-label={`第 ${currentPage} 页`} /></div>
    </>}
  </div>;
}
