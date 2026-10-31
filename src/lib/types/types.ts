import { AUTH_ERROR_CODES } from "@/src/lib/config/constants/api";

/**
 * 核心 API 类型定义
 */

/** API 统一响应格式 */
export interface ApiResp<T = unknown> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
  timestamp: string;
}

/** API 错误类 */
export class ApiError extends Error {
  code: number;
  traceId?: string;
  payload?: unknown;

  constructor(
    message: string,
    code: number,
    traceId?: string,
    payload?: unknown
  ) {
    super(message);
    this.code = code;
    this.traceId = traceId;
    this.payload = payload;
    this.name = "ApiError";
  }

  /** 是否为认证错误 */
  isAuthError(): boolean {
    return AUTH_ERROR_CODES.has(this.code);
  }
}

/** 检查响应是否成功 */
export function isSuccessCode(code: number): boolean {
  return code === 0;
}
