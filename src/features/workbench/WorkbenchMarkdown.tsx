"use client";

import { memo } from "react";
import MarkdownBase from "@/src/components/features/markdown/MarkdownBase";
import { isHiddenAssistantText } from "./conversationText";

/** Current Markdown bridge; keeps the workbench independent of the retired chat UI. */
export const WorkbenchMarkdown = memo(function WorkbenchMarkdown({ text }: { text: string }) {
  const content = text.trim();
  if (isHiddenAssistantText(content)) return null;
  return <MarkdownBase role="assistant">{content}</MarkdownBase>;
});
