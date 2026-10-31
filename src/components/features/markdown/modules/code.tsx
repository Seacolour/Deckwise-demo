"use client";

import React, {
    Children,
    ReactElement,
    ReactNode,
    isValidElement,
    useMemo,
} from "react";
import { useTranslations } from "next-intl";
import { codeBlockClassNames } from "@/src/components/features/markdown-surface/contract/classes";
import { extractCodeBlockLanguageFromClassName } from "@/src/components/features/markdown-surface/core/language";
import { CodeBlockChrome } from "@/src/components/features/markdown-surface/react/CodeBlockChrome";

type WithClassAndChildren = { className?: string; children?: ReactNode };
export type CodeProps = React.DetailedHTMLProps<
    React.HTMLAttributes<HTMLElement>,
    HTMLElement
> & { inline?: boolean; className?: string; children?: React.ReactNode; node?: unknown };

function firstElement(node: ReactNode): ReactElement<WithClassAndChildren> | null {
    const arr = Children.toArray(node);
    for (const n of arr) {
        if (isValidElement(n)) return n as ReactElement<WithClassAndChildren>;
    }
    return null;
}

function toText(node: ReactNode): string {
    if (node == null || node === false) return "";
    if (typeof node === "string" || typeof node === "number") return String(node);
    if (Array.isArray(node)) return node.map(toText).join("");
    if (isValidElement(node)) return toText((node.props as WithClassAndChildren).children);
    return "";
}

function extractCodeText(children: ReactNode) {
    const codeEl = firstElement(children);
    const className = typeof codeEl?.props?.className === "string" ? codeEl.props.className : "";
    const lang = extractCodeBlockLanguageFromClassName(className);
    const code = toText(codeEl?.props?.children);
    return { code, lang };
}

export const InlineCode = ({ inline, className, children, ...rest }: CodeProps) => {
    const htmlProps = { ...rest };
    delete htmlProps.node;
    if (inline) {
        return (
            <code
                {...htmlProps}
                className={[codeBlockClassNames.inline, className].filter(Boolean).join(" ")}
            >
                {children}
            </code>
        );
    }

    return <code className={className} {...htmlProps}>{children}</code>;
};

export function PreBlock({ children, ...rest }: { children: React.ReactNode }) {
    const tCode = useTranslations("codeBlock");
    const { code, lang } = extractCodeText(children);

    const patchedChildren = useMemo(() => {
        const first = firstElement(children);
        if (first) {
            const prev = first.props.className ?? "";
            return React.cloneElement(first, {
                className: `${prev} block min-w-max !bg-transparent !p-0 !text-inherit`,
            });
        }

        return (
            <code className="block min-w-max !bg-transparent !p-0 !text-inherit">
                {children}
            </code>
        );
    }, [children]);

    return (
        <CodeBlockChrome
            code={code}
            language={lang}
            preProps={rest}
            copyAriaLabel={tCode("copyAria")}
            copyLabel={tCode("copy")}
            copiedLabel={tCode("copied")}
            copiedAnnouncement={tCode("copiedToClipboard")}
        >
            {patchedChildren}
        </CodeBlockChrome>
    );
}
