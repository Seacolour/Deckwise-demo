"use client";

import React, { useMemo, type HTMLAttributes, type ReactNode } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkBreaks from "remark-breaks";
import rehypeHighlight from "rehype-highlight";
import type { Pluggable } from "unified";
import {
  getMathRehypePlugins,
  getMathRemarkPlugins,
  normalizeMarkdownInput,
  remarkMathInTablesAsInline,
} from "./modules/math";
import type { MarkdownRole } from "./modules/normalizeInput";
import { InlineCode, PreBlock } from "./modules/code";
import { codeBlockClassNames, markdownTableClassNames } from "@/src/components/features/markdown-surface/contract/classes";
import styles from "./MarkdownBase.module.css";

type MarkdownProps = HTMLAttributes<HTMLElement> & { children?: ReactNode; node?: unknown };

function domProps<P extends MarkdownProps>(props: P): Omit<P, "node"> {
  const clean = { ...props };
  delete clean.node;
  return clean;
}

const markdownComponents = {
  code: InlineCode,
  pre: ({ children, ...rest }: { children?: ReactNode } & MarkdownProps) => (
    <PreBlock {...domProps(rest)}>{children}</PreBlock>
  ),
  blockquote: ({ children, ...props }: { children?: ReactNode } & MarkdownProps) => (
    <blockquote
      className="my-3 border-l-4 pl-4 italic text-muted-foreground select-text"
      {...domProps(props)}
    >
      {children}
    </blockquote>
  ),
  h1: (props: MarkdownProps) => <h1 className="text-3xl font-bold my-3 select-text" {...domProps(props)} />,
  h2: (props: MarkdownProps) => <h2 className="text-2xl font-bold my-3 select-text" {...domProps(props)} />,
  h3: (props: MarkdownProps) => <h3 className="text-xl font-semibold my-2 select-text" {...domProps(props)} />,
  h4: (props: MarkdownProps) => <h4 className="text-lg font-semibold my-2 select-text" {...domProps(props)} />,
  h5: (props: MarkdownProps) => <h5 className="font-semibold my-2 select-text" {...domProps(props)} />,
  h6: (props: MarkdownProps) => <h6 className="font-medium my-2 text-sm select-text" {...domProps(props)} />,
  ul: (props: MarkdownProps) => <ul className="list-disc pl-6 my-2 space-y-1 select-text" {...domProps(props)} />,
  ol: (props: MarkdownProps) => <ol className="list-decimal pl-6 my-2 space-y-1 select-text" {...domProps(props)} />,
  img: ({ src, alt = "", ...rest }: { src?: string | Blob; alt?: string } & MarkdownProps) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={typeof src === "string" ? src : undefined}
      alt={alt}
      loading="lazy"
      referrerPolicy="no-referrer"
      className="rounded-lg max-w-full my-2 shadow-sm"
      {...domProps(rest)}
    />
  ),
  a: (props: MarkdownProps) => (
    <a
      {...domProps(props)}
      target="_blank"
      rel="noreferrer"
      className="underline underline-offset-4 decoration-[var(--jz-theme-yellow)]"
    />
  ),
  table: (props: MarkdownProps) => (
    <div className={`${markdownTableClassNames.scroller} ${codeBlockClassNames.scrollbar} my-4 block max-w-full`}>
      <table className={markdownTableClassNames.table} {...domProps(props)} />
    </div>
  ),
  th: (props: MarkdownProps) => (
    <th
      className={`${markdownTableClassNames.headerCell} select-text`}
      {...domProps(props)}
    />
  ),
  td: (props: MarkdownProps) => (
    <td
      className={`${markdownTableClassNames.cell} select-text`}
      {...domProps(props)}
    />
  ),
  p: (props: MarkdownProps) => <div className="whitespace-pre-wrap leading-relaxed my-2 select-text" {...domProps(props)} />,
  li: (props: MarkdownProps) => <li className="leading-relaxed my-1 select-text" {...domProps(props)} />,
  input: (props: MarkdownProps & { type?: string }) =>
    props.type === "checkbox" ? (
      <input {...domProps(props)} className="mr-2 align-middle" />
    ) : (
      <input {...domProps(props)} />
    ),
};

export default function MarkdownBase({
  children,
  role,
}: {
  children: string;
  role?: MarkdownRole;
  preset?: string;
}) {
  const text = useMemo(() => normalizeMarkdownInput(children, role), [children, role]);

  const remarkPlugins = useMemo<Pluggable[]>(() => [
    ...getMathRemarkPlugins(),
    remarkGfm as Pluggable,
    remarkMathInTablesAsInline() as Pluggable,
    remarkBreaks as Pluggable,
  ], []);

  const rehypePlugins = useMemo<Pluggable[]>(() => {
    const plugins: Pluggable[] = [
      ...getMathRehypePlugins(),
      [rehypeHighlight, { detect: true, ignoreMissing: true }] as Pluggable,
    ];
    return plugins;
  }, []);

  return (
    <div className={`${styles.root} relative select-text`}>
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={markdownComponents}
      >
        {text}
      </ReactMarkdown>

    </div>
  );
}
