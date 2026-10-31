/**
 * 工具调用事件解析器
 *
 * 对齐 iOS ToolCallEventParser.swift — 从 toolJSON 事件中解析出结构化的 ToolCallEvent。
 *
 * 后端帧格式示例：
 * - tool_call:   {"type":"tool_call","call_id":"abc","name":"file_list","arguments":"{}","seq":3}
 * - tool_result: {"type":"tool_result","call_id":"abc","tool_name":"file_list","result":{...},"seq":5}
 * - tool_status: {"type":"tool_status","call_id":"abc","status":"running","seq":4}
 */

import type { ToolCallEvent } from './toolCallEvent';
import { createToolCallEvent } from './toolCallEvent';
import { buildToolDisplay } from './toolRegistry';
import { extractAskHumanArgumentsRaw } from './askHumanEvent';

/**
 * 尝试从 toolJSON payload 中解析出 ToolCallEvent
 * 返回 null 表示该 JSON 不是工具事件（或为空/不完整）
 */
export function parseToolCallEvent(
  payload: string,
  currentTextLength: number
): ToolCallEvent | null {
  if (!payload || !payload.trim()) return null;

  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(payload.trim()) as Record<string, unknown>;
  } catch {
    return null;
  }

  if (typeof obj !== 'object' || obj === null) return null;

  // 展平嵌套的 payload 对象（后端常见格式）：
  // {"type":"tool","seq":1,"payload":{"phase":"call","call_id":"...","name":"file_list","arguments_json":"..."}}
  // iOS ToolCallEventParser 用正则搜索整个 raw string，不受嵌套影响；
  // web 端这里展开 payload，让后续 extractString 能找到嵌套字段。
  const flat = flattenPayload(obj);

  const typeValue = flat.type ?? flat.phase;
  const type = typeof typeValue === 'string' ? typeValue : '';
  if (!type) return null;

  // ==================== ask_human（对齐 iOS ToolCallEventParser ask_human 分支） ====================
  if (type === 'ask_human') {
    const callId = extractString(flat, ['call_id', 'callId']);
    const seq = typeof flat.seq === 'number' ? flat.seq : null;
    // 优先从原始 obj 提取（保留未展平的 payload 结构），兜底用展平后的 flat
    const argumentsRaw = extractAskHumanArgumentsRaw(obj) ?? extractAskHumanArgumentsRaw(flat);
    const { title, params } = buildToolDisplay('ask_human', argumentsRaw);
    return createToolCallEvent({
      callId,
      name: 'ask_human',
      argumentsRaw,
      seq,
      displayTitle: title,
      displayParams: params,
      insertAtUTF16: currentTextLength,
    });
  }

  // 判断 phase（对齐 iOS）
  const phase = (flat.phase as string) ?? '';
  const isResult =
    type === 'tool_result' ||
    phase === 'result';

  // ==================== tool_call ====================
  if (!isResult && (type === 'tool_call' || type === 'tool' || phase === 'call')) {
    const callId = extractString(flat, ['call_id', 'callId']);
    const name = extractString(flat, ['name', 'tool_name', 'toolName']) ?? 'unknown';
    const argumentsRaw = extractArguments(flat);
    const seq = typeof flat.seq === 'number' ? flat.seq : null;

    const { title, params } = buildToolDisplay(name, argumentsRaw);

    return createToolCallEvent({
      callId,
      name,
      argumentsRaw,
      seq,
      displayTitle: title,
      displayParams: params,
      insertAtUTF16: currentTextLength,
    });
  }

  // ==================== tool_result ====================
  if (isResult) {
    const callId = extractString(flat, ['call_id', 'callId']);
    const name = extractString(flat, ['tool_name', 'toolName', 'name']) ?? 'unknown';
    const seq = typeof flat.seq === 'number' ? flat.seq : null;

    const { title } = buildToolDisplay(name, null);
    return {
      id: `tool-result-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      callId,
      name,
      phase: 'result',
      argumentsRaw: null,
      seq,
      isCompleted: true,
      displayTitle: title,
      displayParams: [],
      insertAtUTF16: null,
    };
  }

  // ==================== tool_status ====================
  if (type === 'tool_status') {
    return null;
  }

  // 其他未知 tool 类型 → 尝试作为 call 解析（兼容性兜底）
  if (type.startsWith('tool_')) {
    const callId = extractString(flat, ['call_id', 'callId']);
    const name = extractString(flat, ['name', 'tool_name', 'toolName']) ?? type;
    const { title } = buildToolDisplay(name, null);
    return createToolCallEvent({
      callId,
      name,
      argumentsRaw: null,
      seq: typeof flat.seq === 'number' ? flat.seq : null,
      displayTitle: title,
      displayParams: [],
      insertAtUTF16: currentTextLength,
    });
  }

  return null;
}

// ==================== 辅助函数 ====================

/**
 * 展开嵌套的 payload 对象到顶层（对齐 iOS 的字符串级搜索行为）。
 *
 * 后端常见格式：
 * {"type":"tool","seq":1,"payload":{"phase":"call","call_id":"...","name":"file_list","arguments_json":"..."}}
 *
 * 展开后：
 * {"type":"tool","seq":1,"phase":"call","call_id":"...","name":"file_list","arguments_json":"..."}
 *
 * 注意：顶层字段优先，payload 内同名字段不会覆盖。
 */
function flattenPayload(obj: Record<string, unknown>): Record<string, unknown> {
  const payload = obj.payload;
  if (!payload || typeof payload !== 'object') return obj;
  const inner = payload as Record<string, unknown>;
  // payload 内的字段 merge 到顶层，但不覆盖顶层已有字段（除 payload 本身）
  const result: Record<string, unknown> = { ...obj };
  for (const [k, v] of Object.entries(inner)) {
    if (k === 'payload') continue; // 防止递归
    if (!(k in result) || result[k] === undefined || result[k] === null) {
      result[k] = v;
    }
  }
  return result;
}

/** 从对象中提取第一个匹配的字符串字段 */
function extractString(obj: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const v = obj[key];
    if (typeof v === 'string' && v.length > 0) return v;
  }
  return null;
}

/** 提取 arguments 字段（可能是 string 或 object） */
function extractArguments(obj: Record<string, unknown>): string | null {
  const raw = obj.arguments ?? obj.arguments_json ?? obj.argumentsJson;
  if (typeof raw === 'string') return raw;
  if (raw && typeof raw === 'object') {
    try {
      return JSON.stringify(raw);
    } catch {
      return null;
    }
  }
  return null;
}
