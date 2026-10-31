/**
 * 工具片段提取器（对齐 iOS ToolSnippetExtractor.swift）
 *
 * 从历史消息正文中提取工具 JSON 片段，返回：
 * - cleanedText: 去掉工具片段后的纯文本
 * - toolEvents: 从片段中解析出的 ToolCallEvent 列表
 *
 * 背景：
 * - 后端/Agent 的工具事件可能以两种方式到达：
 *   1) 作为独立帧（StreamEventParser → toolJSON）——流式期间已处理
 *   2) 直接嵌入在文本内容中（历史消息加载时常见）
 * - 流式期间 toolEvents 存在内存中，但刷新后丢失（web 不持久化 toolEvents）
 * - 本模块解决刷新后工具行不显示的问题：从历史文本中提取工具 JSON 并还原为 ToolCallEvent
 */

import type { ToolCallEvent } from './toolCallEvent';
import { parseToolCallEvent } from './toolCallEventParser';
import { buildToolDisplay } from './toolRegistry';

// ==================== 工具 JSON 触发词 ====================
// 用于快速判断一段 JSON 是否是工具事件（避免对所有 JSON 都尝试解析）

// 工具帧前缀（标准格式）
const TOOL_FRAME_PREFIXES = [
  '{"type":"tool_call"',
  '{"type":"tool_result"',
  '{"type":"tool_status"',
  '{"type":"tool"',
  '{"type":"ask_human"',
  '{type:tool',
  '{"phase":"call"',
  '{"phase":"result"',
];

// 工具 payload 前缀（后端可能直接把工具的 arguments/result 嵌入消息正文）
// 这些不是标准工具帧，但需要从文本中剔除（否则会显示为原始 JSON）
const TOOL_PAYLOAD_PREFIXES = [
  '{"document_id"',     // document_save / document_diff 的 arguments
  '{"success":',        // 工具 result payload（{"success":true, ...}）
  '{"saved_version"',   // document_save result
  '{"content_length"',  // document_save result
  '{"file_list"',       // file_list result
  '{"content"',         // document_read / file_content_read result
];

/**
 * 检测字符串是否以工具 JSON 开头（标准帧格式）
 */
function isToolFrameStart(s: string): boolean {
  const trimmed = s.trimStart();
  return TOOL_FRAME_PREFIXES.some((p) => trimmed.startsWith(p));
}

/**
 * 检测字符串是否以工具 payload 开头（非标准帧，但需要剔除）
 */
function isToolPayloadStart(s: string): boolean {
  const trimmed = s.trimStart();
  return TOOL_PAYLOAD_PREFIXES.some((p) => trimmed.startsWith(p));
}

// ==================== 平衡花括号提取 ====================

/**
 * 从位置 start 开始提取一个完整的 { ... } JSON 对象（平衡花括号）。
 * 返回提取到的字符串长度，失败返回 -1。
 */
function extractBalancedBraces(text: string, start: number): number {
  if (text[start] !== '{') return -1;
  let depth = 0;
  let inString = false;
  let escape = false;

  for (let i = start; i < text.length; i++) {
    const ch = text[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (ch === '\\' && inString) {
      escape = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (!inString) {
      if (ch === '{') depth++;
      else if (ch === '}') {
        depth--;
        if (depth === 0) return i - start + 1;
      }
    }
  }
  return -1; // 未闭合
}

// ==================== 从工具 JSON 构建 ToolCallEvent ====================

/**
 * 尝试把一段 JSON 字符串解析为 ToolCallEvent。
 * 失败返回 null。
 */
function parseToolSnippet(raw: string, insertAtUTF16: number): ToolCallEvent | null {
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }
  if (typeof obj !== 'object' || obj === null) return null;

  // 提取 type
  const type = (obj.type as string) ?? '';

  // ask_human → 委托给 parseToolCallEvent（避免重复解析逻辑）
  if (type === 'ask_human') {
    return parseToolCallEvent(raw, insertAtUTF16);
  }

  // 判断 phase
  let phase: 'call' | 'result' = 'call';
  const phaseField = obj.phase as string | undefined;
  if (
    type === 'tool_result' ||
    phaseField === 'result'
  ) {
    phase = 'result';
  }

  // 提取字段
  const callId = extractStr(obj, ['call_id', 'callId']);
  const name = extractStr(obj, ['name', 'tool_name', 'toolName']) ?? 'unknown';
  const seq = typeof obj.seq === 'number' ? obj.seq : null;

  // 提取 arguments
  let argumentsRaw: string | null = null;
  if (phase === 'call') {
    const args = obj.arguments ?? obj.arguments_json ?? obj.argumentsJson;
    if (typeof args === 'string') {
      argumentsRaw = args;
    } else if (args && typeof args === 'object') {
      try { argumentsRaw = JSON.stringify(args); } catch { /* ignore */ }
    }
  }

  const { title, params } = buildToolDisplay(name, argumentsRaw);

  return {
    id: `tool-hist-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    callId,
    name,
    phase,
    argumentsRaw,
    seq,
    isCompleted: phase === 'result',
    displayTitle: title,
    displayParams: params,
    insertAtUTF16: phase === 'call' ? insertAtUTF16 : null,
  };
}

function extractStr(obj: Record<string, unknown>, keys: string[]): string | null {
  for (const k of keys) {
    const v = obj[k];
    if (typeof v === 'string' && v.length > 0) return v;
  }
  return null;
}

// ==================== 公开 API ====================

export interface ExtractResult {
  /** 去掉工具片段后的文本（用于 markdown 渲染） */
  cleanedText: string;
  /** 从文本中提取出的工具事件（call 行，result 会合并到对应 call 的 isCompleted） */
  toolEvents: ToolCallEvent[];
}

/**
 * 从消息正文中提取工具 JSON 片段，返回清理后的文本和工具事件列表。
 *
 * 对齐 iOS ToolSnippetExtractor.splitIntoSegments：
 * - 扫描文本中的 { 开头，检测是否是工具 JSON
 * - 用平衡花括号提取完整 JSON
 * - 解析为 ToolCallEvent
 * - 从原文中剔除工具片段
 */
export function extractToolSnippets(text: string): ExtractResult {
  if (!text) return { cleanedText: '', toolEvents: [] };

  const callEvents: ToolCallEvent[] = [];
  const resultCallIds = new Set<string>();
  const cleanedParts: string[] = [];
  let cleanedLength = 0;
  let keepStart = 0;
  let i = 0;

  function appendKeptText(until: number) {
    if (until <= keepStart) return;
    const chunk = text.slice(keepStart, until);
    cleanedParts.push(chunk);
    cleanedLength += chunk.length;
  }

  while (i < text.length) {
    if (text[i] === '{') {
      const rest = text.slice(i);

      // 1) 标准工具帧（{"type":"tool_call",...}）→ 解析为 ToolCallEvent
      if (isToolFrameStart(rest)) {
        const len = extractBalancedBraces(text, i);
        if (len > 0) {
          const snippet = text.slice(i, i + len);
          const event = parseToolSnippet(snippet, cleanedLength + i - keepStart);
          if (event) {
            appendKeptText(i);
            if (event.phase === 'result') {
              if (event.callId) resultCallIds.add(event.callId);
            } else {
              callEvents.push(event);
            }
            i += len;
            keepStart = i;
            continue;
          }
        }
      }

      // 2) 工具 payload（{"document_id":..., "success":...}）→ 直接剔除，不解析为事件
      //    后端可能把工具的 arguments/result 直接嵌入消息正文
      if (isToolPayloadStart(rest)) {
        const len = extractBalancedBraces(text, i);
        if (len > 0) {
          // 跳过整个 payload 片段（从文本中剔除）
          appendKeptText(i);
          i += len;
          keepStart = i;
          continue;
        }
      }
    }
    i++;
  }

  appendKeptText(text.length);

  // 把 result 对应的 call 标记为已完成
  for (const ev of callEvents) {
    if (ev.callId && resultCallIds.has(ev.callId)) {
      ev.isCompleted = true;
    }
  }

  const cleaned = cleanedParts.join('');
  const leadingTrimLength = cleaned.length - cleaned.trimStart().length;
  const cleanedText = cleaned.trim();

  return {
    cleanedText,
    toolEvents: callEvents.map((event) => ({
      ...event,
      insertAtUTF16: event.insertAtUTF16 === null
        ? null
        : Math.max(0, event.insertAtUTF16 - leadingTrimLength),
    })),
  };
}
