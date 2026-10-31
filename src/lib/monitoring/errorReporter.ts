/**
 * 前端错误记录器
 *
 * 提供统一的错误捕获和本地日志；需要时可显式配置通用错误端点。
 */

import { CONFIG } from '@/src/lib/config/env';
import { debugFlagLog } from '@/src/lib/debug';
import { LocalStorageKeys } from '@/src/lib/config/constants/storage';
import { useCallback } from 'react';

/** 错误级别 */
export type ErrorLevel = 'fatal' | 'error' | 'warning' | 'info' | 'debug';

/** 错误分类 */
export type ErrorCategory =
  | 'network'        // 网络请求错误
  | 'api'           // API 业务错误
  | 'validation'     // 数据验证错误
  | 'ui'            // UI 交互错误
  | 'storage'        // 存储相关错误
  | 'render'         // 渲染错误
  | 'unknown';       // 未知错误

/** 标准化的错误记录 */
export interface ErrorLog {
  /** 错误唯一标识 */
  id: string;
  /** 错误级别 */
  level: ErrorLevel;
  /** 错误分类 */
  category: ErrorCategory;
  /** 错误消息 */
  message: string;
  /** 原始错误对象 */
  error?: unknown;
  /** 错误堆栈 */
  stack?: string;
  /** 发生时间 */
  timestamp: number;
  /** 用户 ID */
  userId?: string;
  /** 当前页面路径 */
  path?: string;
  /** 用户代理 */
  userAgent?: string;
  /** 额外上下文信息 */
  context?: Record<string, unknown>;
  /** 会话 ID（用于关联用户操作） */
  sessionId?: string;
}

/** 错误上报配置 */
interface ErrorReporterConfig {
  /** 是否启用错误上报 */
  enabled?: boolean;
  /** 错误上报端点 */
  endpoint?: string;
  /** 最小上报级别 */
  minLevel?: ErrorLevel;
  /** 采样率 (0-1) */
  sampleRate?: number;
  /** 是否包含用户信息 */
  includeUserInfo?: boolean;
  /** 上报超时时间 */
  timeout?: number;
}

/** 默认配置 */
const DEFAULT_CONFIG: ErrorReporterConfig = {
  enabled: false,
  minLevel: 'error',
  sampleRate: 1.0,
  includeUserInfo: true,
  timeout: 5000,
};

let reporterConfig: ErrorReporterConfig = DEFAULT_CONFIG;

/**
 * 配置错误报告器
 */
export function configureErrorReporter(config: Partial<ErrorReporterConfig>): void {
  reporterConfig = { ...reporterConfig, ...config };
}

/**
 * 生成错误 ID
 */
function generateErrorId(): string {
  return `err_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`;
}

/**
 * 从错误对象中提取堆栈信息
 */
function extractStack(error: unknown): string | undefined {
  if (error instanceof Error) {
    return error.stack;
  }
  if (typeof error === 'object' && error !== null && 'stack' in error) {
    return String((error as { stack?: unknown }).stack);
  }
  return undefined;
}

/**
 * 获取当前用户 ID（如果已登录）
 */
function getCurrentUserId(): string | undefined {
  try {
    if (typeof window === 'undefined') return undefined;

    const raw = localStorage.getItem(LocalStorageKeys.USER);
    if (!raw) return undefined;

    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return undefined;
    }

    const record = parsed as Record<string, unknown>;
    const user = typeof record.user === 'object' && record.user !== null && !Array.isArray(record.user)
      ? record.user as Record<string, unknown>
      : record;
    const id = user.id ?? user.userId;

    if (typeof id === 'string') return id;
    if (typeof id === 'number' && Number.isFinite(id)) return String(id);
    return undefined;
  } catch {
    return undefined;
  }
}

/**
 * 获取当前页面路径
 */
function getCurrentPath(): string {
  if (typeof window === 'undefined') return 'server';
  return window.location.pathname;
}

/**
 * 获取用户代理
 */
function getUserAgent(): string {
  if (typeof navigator === 'undefined') return 'server';
  return navigator.userAgent;
}

/**
 * 判断是否应该上报该错误
 */
function shouldReport(level: ErrorLevel): boolean {
  if (!reporterConfig.enabled) return false;

  const levels: ErrorLevel[] = ['fatal', 'error', 'warning', 'info', 'debug'];
  const minLevelIndex = levels.indexOf(reporterConfig.minLevel ?? 'error');
  const currentLevelIndex = levels.indexOf(level);

  if (currentLevelIndex < minLevelIndex) return false;

  // 采样率检查
  if (typeof reporterConfig.sampleRate === 'number' && reporterConfig.sampleRate < 1) {
    return Math.random() < reporterConfig.sampleRate;
  }

  return true;
}

/**
 * 记录错误日志
 */
export function logError(
  level: ErrorLevel,
  category: ErrorCategory,
  message: string,
  error?: unknown,
  context?: Record<string, unknown>
): ErrorLog {
  const errorLog: ErrorLog = {
    id: generateErrorId(),
    level,
    category,
    message,
    error,
    stack: extractStack(error),
    timestamp: Date.now(),
    userId: reporterConfig.includeUserInfo ? getCurrentUserId() : undefined,
    path: getCurrentPath(),
    userAgent: getUserAgent(),
    context,
  };

  // 开发环境输出到控制台
  if (CONFIG.ENV !== 'prod') {
    const payload = {
      category,
      error,
      context,
      stack: errorLog.stack,
    };
    if (level === 'fatal' || level === 'error') {
      console.error(`[${level.toUpperCase()}]`, message, payload);
    } else if (level === 'warning') {
      console.warn(`[${level.toUpperCase()}]`, message, payload);
    } else {
      debugFlagLog(true, `[${level.toUpperCase()}]`, message, payload);
    }
  } else if (level === 'fatal' || level === 'error') {
    console.error(`[${level.toUpperCase()}]`, message);
  }

  // 默认不向外发送；仅在显式配置通用端点时启用。
  if (reporterConfig.endpoint && shouldReport(level)) {
    // 异步上报，不阻塞主流程
    postErrorToEndpoint(errorLog).catch((err) => {
      // 上报失败，静默处理避免无限循环
      console.warn('[ErrorReporter] Failed to report error:', err);
    });
  }

  return errorLog;
}

async function postErrorToEndpoint(errorLog: ErrorLog): Promise<void> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), reporterConfig.timeout);

  try {
    const response = await fetch(reporterConfig.endpoint!, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        ...errorLog,
        // 不发送敏感的用户代理信息
        userAgent: CONFIG.ENV !== 'prod' ? errorLog.userAgent : undefined,
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
  } catch (err) {
    // 上报失败，记录但不抛出
    console.warn('[ErrorReporter] Error reporting failed:', err);
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * 快捷方法：记录致命错误
 */
export function logFatal(
  category: ErrorCategory,
  message: string,
  error?: unknown,
  context?: Record<string, unknown>
): ErrorLog {
  return logError('fatal', category, message, error, context);
}

/**
 * 快捷方法：记录错误
 */
export function logErrorMsg(
  category: ErrorCategory,
  message: string,
  error?: unknown,
  context?: Record<string, unknown>
): ErrorLog {
  return logError('error', category, message, error, context);
}

/**
 * 快捷方法：记录警告
 */
export function logWarning(
  category: ErrorCategory,
  message: string,
  error?: unknown,
  context?: Record<string, unknown>
): ErrorLog {
  return logError('warning', category, message, error, context);
}

/**
 * 快捷方法：记录信息
 */
export function logInfo(
  category: ErrorCategory,
  message: string,
  context?: Record<string, unknown>
): ErrorLog {
  return logError('info', category, message, undefined, context);
}

/**
 * 捕获并记录异步错误
 */
export async function captureAsyncError<T>(
  category: ErrorCategory,
  fn: () => Promise<T>,
  context?: Record<string, unknown>
): Promise<T | null> {
  try {
    return await fn();
  } catch (error) {
    logErrorMsg(category, 'Async operation failed', error, context);
    return null;
  }
}

/**
 * 创建错误边界组件的错误处理器
 */
export function handleErrorBoundaryError(
  error: unknown,
  context?: Record<string, unknown>
): ErrorLog {
  let message = 'Unknown error';
  let category: ErrorCategory = 'unknown';

  if (error instanceof Error) {
    message = error.message;
    // 根据错误消息推断分类
    if (message.includes('fetch') || message.includes('network')) {
      category = 'network';
    } else if (message.includes('render') || message.includes('React')) {
      category = 'render';
    }
  } else if (typeof error === 'string') {
    message = error;
  }

  return logFatal(category, message, error, context);
}

/**
 * React 错误边界 Hook（用于组件内错误处理）
 */
export function useErrorHandler(category: ErrorCategory, context?: Record<string, unknown>) {
  return useCallback((error: unknown) => {
    logErrorMsg(category, 'Error in component', error, context);
  }, [category, context]);
}

/**
 * 全局错误处理器（用于 window.onerror 和 window.onunhandledrejection）
 */
export function setupGlobalErrorHandlers(): void {
  if (typeof window === 'undefined') return;

  // 捕获未处理的错误
  window.onerror = (message, source, lineno, colno, error) => {
    logFatal('unknown', String(message), error, {
      source,
      lineno,
      colno,
    });
    return false; // 让默认错误处理继续执行
  };

  // 捕获未处理的 Promise 拒绝
  window.onunhandledrejection = (event) => {
    logFatal('unknown', 'Unhandled promise rejection', event.reason, {
      promise: event.promise,
    });
    event.preventDefault(); // 阻止默认的控制台警告
  };
}

/**
 * 性能监控 API
 */
export const perfMonitor = {
  /**
   * 记录性能指标
   */
  logMetric(name: string, value: number, unit?: string): void {
    debugFlagLog(CONFIG.ENV !== 'prod', `[Performance] ${name}: ${value}${unit ?? ''}`);
  },

  /**
   * 测量操作执行时间
   */
  async measure<T>(
    name: string,
    fn: () => Promise<T>
  ): Promise<T> {
    const start = globalThis.performance.now();
    try {
      return await fn();
    } finally {
      const duration = globalThis.performance.now() - start;
      perfMonitor.logMetric(name, duration, 'ms');
    }
  },

  /**
   * 测量同步操作执行时间
   */
  measureSync<T>(
    name: string,
    fn: () => T
  ): T {
    const start = globalThis.performance.now();
    try {
      return fn();
    } finally {
      const duration = globalThis.performance.now() - start;
      perfMonitor.logMetric(name, duration, 'ms');
    }
  },
};

/**
 * 初始化错误监控（在应用启动时调用）
 */
export function initErrorMonitoring(): void {
  setupGlobalErrorHandlers();

  if (CONFIG.ENV === 'prod') {
    // 生产环境配置
    configureErrorReporter({
      enabled: true,
      minLevel: 'error',
      sampleRate: 1.0,
    });
  }
}
