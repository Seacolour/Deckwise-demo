/**
 * 统一的错误处理工具
 *
 * 用于处理那些可以安全忽略但仍需要记录的错误
 * 在开发环境中会输出警告，生产环境中静默处理
 */

import { CONFIG } from '@/src/lib/config/env';

/** 日志级别 */

/** 错误上下文信息 */
type ErrorContext = {
  /** 错误来源 */
  source?: string;
  /** 额外信息 */
  extra?: Record<string, unknown>;
};

/**
 * 安全执行函数，忽略错误
 * 用于事件派发、清理操作等非关键路径
 *
 * @example
 * silentExecute(() => window.dispatchEvent(event), { source: 'file:active' });
 */
export function silentExecute(
  fn: () => void,
  context?: ErrorContext
): void {
  try {
    fn();
  } catch (err) {
    logIgnoredError(err, context);
  }
}

/**
 * 安全执行异步函数，忽略错误
 */
export async function silentExecuteAsync(
  fn: () => Promise<void>,
  context?: ErrorContext
): Promise<void> {
  try {
    await fn();
  } catch (err) {
    logIgnoredError(err, context);
  }
}

/**
 * 安全获取值，失败时返回默认值
 */
export function safeGet<T>(
  fn: () => T,
  defaultValue: T,
  context?: ErrorContext
): T {
  try {
    return fn();
  } catch (err) {
    logIgnoredError(err, context);
    return defaultValue;
  }
}

/**
 * 记录被忽略的错误
 */
function logIgnoredError(err: unknown, context?: ErrorContext): void {
  // 生产环境静默处理
  if (CONFIG.ENV === 'prod') {
    return;
  }

  // 开发环境输出警告
  const prefix = context?.source ? `[${context.source}]` : '[Ignored Error]';
  const message = err instanceof Error ? err.message : String(err);

  console.warn(`${prefix} ${message}`, context?.extra ?? '');
}

// 导出别名 `safeExecute` 用于向后兼容
export { silentExecute as safeExecute };

/**
 * 创建一个安全的包装函数，用于事件回调
 */
export function createEventHandler(
  handler: () => void,
  source: string
): () => void {
  return () => silentExecute(handler, { source });
}

/**
 * 创建安全的 DOM 操作函数
 * 用于可能因为元素不存在而失败的操作
 */
export function safeDOMOperation<T>(
  fn: () => T,
  defaultValue: T,
  operation?: string
): T {
  return safeGet(fn, defaultValue, { source: 'DOM', extra: { operation } });
}

/**
 * 安全存储操作
 * 用于 localStorage/sessionStorage 操作
 */
export function safeStorageOperation(
  fn: () => void,
  storageType: 'localStorage' | 'sessionStorage',
  operation?: string
): void {
  silentExecute(fn, {
    source: 'Storage',
    extra: { type: storageType, operation }
  });
}

/**
 * 安全事件派发
 */
export function safeDispatchEvent(
  event: Event,
  eventType?: string
): void {
  silentExecute(
    () => window.dispatchEvent(event),
    { source: 'EventDispatch', extra: { type: eventType ?? event.type } }
  );
}

/**
 * 安全清理操作
 * 用于 reader.cancel()、cleanup 函数等
 */
export async function safeCleanup(
  fn: () => Promise<void> | void,
  cleanupType?: string
): Promise<void> {
  await silentExecuteAsync(
    () => Promise.resolve(fn()),
    { source: 'Cleanup', extra: { type: cleanupType } }
  );
}
