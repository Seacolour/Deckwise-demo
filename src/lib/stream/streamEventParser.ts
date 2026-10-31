/**
 * 流事件解析器
 *
 * 对齐 iOS StreamEventParser.swift — 把原始 SSE data 字符串解析为结构化 StreamEvent。
 *
 * 解析优先级：
 * 1. Frame JSON（后端按帧发送：{"type":"text","seq":1,"payload":"..."}）
 * 2. [[STREAM_START]] 信号
 * 3. 已知错误前缀匹配
 * 4. JSON 触发词检测 + 分片缓冲（跨 chunk 的不完整 tool JSON 累积）
 * 5. 普通文本 textDelta
 */

import type { StreamEvent } from './streamEvent';
import { ERROR_PREFIXES } from './streamEvent';

// ==================== Frame JSON 解码 ====================

interface StreamFrame {
  type: string;
  seq?: number;
  ts?: string;
  payload?: unknown;
}

/**
 * 尝试把 chunk 解码为 StreamFrame（标准 JSON: {"type":"text","payload":"..."}）
 * 返回 null 表示不是 Frame
 */
function tryParseFrame(chunk: string): StreamFrame | null {
  const trimmed = chunk.trim();

  // 必须以 { 开头，以 } 结尾
  if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) {
    return null;
  }

  // 尝试解析为 JSON
  try {
    const obj = JSON.parse(trimmed) as Record<string, unknown>;
    if (typeof obj.type === 'string') {
      return {
        type: obj.type as string,
        seq: typeof obj.seq === 'number' ? obj.seq : undefined,
        ts: typeof obj.ts === 'string' ? obj.ts : undefined,
        payload: obj.payload,
      };
    }
  } catch {
    // 不是 JSON
  }

  return null;
}

function toStreamErrorMessage(payload: unknown, rawJSON: string): string {
  if (typeof payload === 'string' && payload.trim()) {
    return payload;
  }

  return rawJSON;
}

// ==================== JSON 分片缓冲 ====================

interface JSONBuf {
  inJSON: boolean;
  buf: string;
  depth: number;
}

// ==================== JSON 触发词（对齐 iOS jsonTriggers） ====================

const JSON_TRIGGERS = [
  '"type":"tool_',
  '"type":"ask_human"',
  '"tool_status"',
  '"tool_plan"',
  '"links":[',
  '"photoCount"',
  'ScholarSearchResultCount',
  '"firecrawlCount"',
];

// ==================== Parser ====================

/**
 * 流事件解析器（有状态，维护 JSON 分片缓冲）
 *
 * 用法：
 * ```ts
 * const parser = new StreamEventParser();
 * // 在 SSE onMessage 中：
 * const event = parser.parse(chunk);
 * // 流结束时：
 * parser.clear();
 * ```
 */
export class StreamEventParser {
  private jsonBuf: JSONBuf = { inJSON: false, buf: '', depth: 0 };

  /** 解析单个 SSE data chunk，返回结构化事件 */
  parse(chunk: string): StreamEvent {
    // 1) 优先解析 Frame JSON（后端按帧发送）
    const frame = tryParseFrame(chunk);
    if (frame) {
      return this.handleFrame(frame, chunk.trim());
    }

    // 2) 流开始信号
    if (chunk === '[[STREAM_START]]') {
      return { type: 'streamStart' };
    }

    // 3) 已知错误检测
    for (const { prefix, kind } of ERROR_PREFIXES) {
      if (chunk.includes(prefix)) {
        return {
          type: 'error',
          error: { kind, message: chunk },
        };
      }
    }

    // 4) 清理基本标记
    const cleaned = this.sanitize(chunk);
    if (!cleaned) {
      return { type: 'textDelta', payload: '' };
    }

    // 5) 尝试消费 JSON（tool 数据）
    const jsonEvent = this.consumeJSON(cleaned);
    if (jsonEvent) return jsonEvent;

    // 6) 普通文本
    return { type: 'textDelta', payload: cleaned };
  }

  /** 清理缓冲区（流结束时调用） */
  clear(): void {
    this.jsonBuf = { inJSON: false, buf: '', depth: 0 };
  }

  // ==================== 内部方法 ====================

  /** 处理已解析的 Frame */
  private handleFrame(frame: StreamFrame, rawJSON: string): StreamEvent {
    switch (frame.type) {
      case 'text':
        return { type: 'textDelta', payload: typeof frame.payload === 'string' ? frame.payload : '' };
      case 'done':
        return { type: 'done' };
      case 'abort':
        return { type: 'abort', reason: typeof frame.payload === 'string' ? frame.payload : 'aborted' };
      case 'error':
        return {
          type: 'error',
          error: {
            kind: 'aiServiceError',
            message: toStreamErrorMessage(frame.payload, rawJSON),
          },
        };
      default:
        // tool_call / tool_result / tool_status 等 → 交给上层 Tool 系统处理
        return { type: 'toolJSON', payload: rawJSON };
    }
  }

  /** 清理原始文本：移除 [[STREAM_START]]、去掉独立引号 */
  private sanitize(s: string): string {
    if (!s) return s;
    let result = s.replace(/\[\[STREAM_START]]/g, '');
    result = this.dropStandaloneQuotes(result);
    return result;
  }

  /**
   * 去掉"独立引号"（不构成引用的孤立双引号）
   * 对齐 iOS dropStandaloneQuotes
   */
  private dropStandaloneQuotes(s: string): string {
    const arr = [...s];
    const out: string[] = [];
    for (let i = 0; i < arr.length; i++) {
      const ch = arr[i];
      if (ch !== '"') {
        out.push(ch);
        continue;
      }
      const before = i > 0 ? arr[i - 1] : ' ';
      const after = i < arr.length - 1 ? arr[i + 1] : ' ';
      const leftOK = /[\p{L}\p{N}"]/u.test(before);
      const rightOK = /[\p{L}\p{N}"]/u.test(after);
      if (leftOK && rightOK) out.push(ch);
    }
    return out.join('').replace(/ {2,}/g, ' ');
  }

  /**
   * 消费 JSON 分片（跨 chunk 累积，直到花括号闭合）
   * 对齐 iOS consumeJSON
   */
  private consumeJSON(chunk: string): StreamEvent | null {
    const trimmed = chunk.trim();

    // 已在 JSON 累积模式
    if (this.jsonBuf.inJSON) {
      this.jsonBuf.buf += chunk;
      for (const ch of chunk) {
        if (ch === '{') this.jsonBuf.depth++;
        else if (ch === '}') this.jsonBuf.depth--;
      }

      if (this.jsonBuf.depth > 0) {
        // 仍未闭合，返回空 toolJSON 表示已消费但未完成
        return { type: 'toolJSON', payload: '' };
      }

      // 完整 JSON 到达
      const payload = this.jsonBuf.buf;
      this.jsonBuf = { inJSON: false, buf: '', depth: 0 };
      return { type: 'toolJSON', payload };
    }

    // 未在 JSON 模式，检测是否新进入
    if (trimmed.includes('{') && JSON_TRIGGERS.some((t) => trimmed.includes(t))) {
      this.jsonBuf.inJSON = true;
      this.jsonBuf.buf = chunk;
      this.jsonBuf.depth = 0;
      for (const ch of chunk) {
        if (ch === '{') this.jsonBuf.depth++;
        else if (ch === '}') this.jsonBuf.depth--;
      }

      // 刚好一次性闭合
      if (this.jsonBuf.depth === 0) {
        const payload = this.jsonBuf.buf;
        this.jsonBuf = { inJSON: false, buf: '', depth: 0 };
        return { type: 'toolJSON', payload };
      }

      return { type: 'toolJSON', payload: '' };
    }

    return null; // 不是 JSON
  }
}
