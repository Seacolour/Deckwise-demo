/**
 * 结构化流事件模型
 *
 * 对齐 iOS StreamEvent.swift — 把后端 SSE 原始 chunk 解析为结构化事件，
 * 上层按事件类型分发处理（textDelta → 渲染文本, toolJSON → 工具系统, error → 提示）。
 */

// ==================== 流错误类型 ====================

/** 已知流错误（对齐 iOS StreamError） */
export type StreamErrorKind =
  | 'timeout'
  | 'serviceUnavailable'
  | 'deadlineExceeded'
  | 'connectionClosed'
  | 'otherSessionActive'
  | 'aiServiceError'
  | 'unknown';

export interface StreamError {
  kind: StreamErrorKind;
  message: string;
}

// ==================== 流事件类型 ====================

export type StreamEvent =
  | { type: 'streamStart' }
  | { type: 'textDelta'; payload: string }
  | { type: 'toolJSON'; payload: string }
  | { type: 'error'; error: StreamError }
  | { type: 'abort'; reason: string }
  | { type: 'done' };

// ==================== 已知错误前缀映射 ====================

/** 对齐 iOS StreamEventParser.errorPrefixes */
export const ERROR_PREFIXES: readonly { prefix: string; kind: StreamErrorKind }[] = [
  { prefix: 'AI服务异常', kind: 'aiServiceError' },
  { prefix: 'UNAVAILABLE:', kind: 'serviceUnavailable' },
  { prefix: 'DEADLINE_EXCEEDED', kind: 'deadlineExceeded' },
  { prefix: 'Connection closed due to', kind: 'connectionClosed' },
  { prefix: 'io exception', kind: 'connectionClosed' },
  { prefix: '其他会话正在回复中', kind: 'otherSessionActive' },
] as const;

// ==================== 错误提示本地化 key ====================

/**
 * StreamError.kind → i18n key 映射
 * 上层用 tChat(STREAM_ERROR_I18N_KEYS[kind]) 取本地化文案
 */
export const STREAM_ERROR_I18N_KEYS: Record<StreamErrorKind, string> = {
  timeout: 'streamError.timeout',
  serviceUnavailable: 'streamError.serviceUnavailable',
  deadlineExceeded: 'streamError.deadlineExceeded',
  connectionClosed: 'streamError.connectionClosed',
  otherSessionActive: 'streamError.otherSessionActive',
  aiServiceError: 'streamError.aiServiceError',
  unknown: 'streamError.unknown',
};
