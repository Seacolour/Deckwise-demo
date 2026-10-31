export const codeBlockClassNames = {
  root: "deckwise-code-surface",
  scroller: "deckwise-code-surface__scroller",
  copyButton: "deckwise-code-surface__copy",
  inline: "deckwise-inline-code",
  editorRoot: "deckwise-code-block",
  scrollbar: "scrollbar-capsule",
  scrollbarLight: "scrollbar-capsule-light",
} as const;

export const surfaceClassNames = {
  secondary: "deckwise-surface-secondary",
} as const;

export const markdownTableClassNames = {
  scroller: "deckwise-table-scroll",
  table: "deckwise-table",
  headerCell: "deckwise-table-header",
  cell: "deckwise-table-cell",
} as const;
