export type FenceState = {
  marker: "`" | "~";
  length: number;
};

export type FencedCodeBlock = {
  start: number;
  end: number;
  closed: boolean;
  state: FenceState;
};

export function readFenceOpener(line: string): FenceState | null {
  const match = line.match(/^( {0,3})(`{3,}|~{3,})([^\n]*)$/);
  if (!match) return null;

  const fence = match[2];
  const marker = fence[0] as "`" | "~";
  const info = match[3] ?? "";
  if (marker === "`" && info.includes("`")) {
    return null;
  }

  return { marker, length: fence.length };
}

export function isFenceCloser(line: string, state: FenceState): boolean {
  const match = line.match(/^( {0,3})(`{3,}|~{3,})[ \t]*$/);
  return !!match && match[2][0] === state.marker && match[2].length >= state.length;
}

export function inspectFencedCodeBlocks(src: string): {
  blocks: FencedCodeBlock[];
  trailingFence: FenceState | null;
} {
  const lines = src.split("\n");
  const blocks: FencedCodeBlock[] = [];
  let openFence: FenceState | null = null;
  let blockStart = 0;
  let offset = 0;

  for (const line of lines) {
    const lineEnd = offset + line.length;

    if (openFence) {
      if (isFenceCloser(line, openFence)) {
        blocks.push({
          start: blockStart,
          end: lineEnd,
          closed: true,
          state: openFence,
        });
        openFence = null;
      }
    } else {
      const opener = readFenceOpener(line);
      if (opener) {
        openFence = opener;
        blockStart = offset;
      }
    }

    offset = lineEnd + 1;
  }

  if (openFence) {
    blocks.push({
      start: blockStart,
      end: src.length,
      closed: false,
      state: openFence,
    });
  }

  return {
    blocks,
    trailingFence: openFence,
  };
}

export function isInsideFencedCodeBlock(
  index: number,
  blocks: readonly FencedCodeBlock[],
): boolean {
  return blocks.some((block) => index >= block.start && index < block.end);
}
