"use client";

import React, { useCallback, useRef, useState } from "react";
import { Check, Copy } from "lucide-react";
import { codeBlockClassNames } from "@/src/components/features/markdown-surface/contract/classes";
import { CODE_BLOCK_COPY_FEEDBACK_MS } from "@/src/components/features/markdown-surface/contract/constants";
import { copyTextToClipboard } from "@/src/components/features/markdown-surface/core/copy";

type CodeBlockChromeProps = {
  children: React.ReactNode;
  code: string;
  language?: string;
  preProps?: React.HTMLAttributes<HTMLPreElement>;
  copyAriaLabel: string;
  copyLabel: string;
  copiedLabel: string;
  copiedAnnouncement: string;
};

export function CodeBlockChrome({
  children,
  code,
  language,
  preProps,
  copyAriaLabel,
  copyLabel,
  copiedLabel,
  copiedAnnouncement,
}: CodeBlockChromeProps) {
  const preRef = useRef<HTMLPreElement>(null);
  const [copied, setCopied] = useState(false);
  const mergedPreClassName = [
    codeBlockClassNames.scroller,
    codeBlockClassNames.scrollbar,
    codeBlockClassNames.scrollbarLight,
    "bg-inherit text-foreground",
    preProps?.className,
  ].filter(Boolean).join(" ");

  const onCopy = useCallback(async () => {
    let textToCopy = code;
    const el = preRef.current?.querySelector<HTMLElement>("code");
    if (el?.innerText) textToCopy = el.innerText;
    const ok = await copyTextToClipboard(textToCopy);
    setCopied(ok);
    window.setTimeout(() => setCopied(false), CODE_BLOCK_COPY_FEEDBACK_MS);
  }, [code]);

  return (
    <div
      className={`${codeBlockClassNames.root} my-2 text-foreground transition-colors`}
      data-language={language || "text"}
    >
      <button
        onClick={onCopy}
        type="button"
        aria-label={copyAriaLabel}
        title={copied ? copiedLabel : copyLabel}
        data-state={copied ? "copied" : "idle"}
        className={`${codeBlockClassNames.copyButton} focus-ring`}
      >
        {copied ? <Check size={14} strokeWidth={2} /> : <Copy size={14} strokeWidth={2} />}
        <span className="sr-only" aria-live="polite">
          {copied ? copiedAnnouncement : ""}
        </span>
      </button>

      <pre
        ref={preRef}
        {...preProps}
        className={mergedPreClassName}
        style={{ ...preProps?.style, whiteSpace: "pre" }}
      >
        {children}
      </pre>
    </div>
  );
}
