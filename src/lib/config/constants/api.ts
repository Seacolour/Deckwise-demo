/**
 * API 相关常量
 * 集中管理所有 API 路径、HTTP 状态码、请求头等
 */

/**
 * API 路径常量
 */
export const ApiPaths = {
  // 认证相关
  LOGIN: '/user/login',
  SEND_LOGIN_EMAIL_CODE: '/user/login/email-code',

  // 会话相关
  SESSION_SEND_RPC: (sessionId: string | number) => `/sessions/${sessionId}/sendRPC`,

  // 代理相关
  SSE_PROXY_PREFIX: '/sse/',
  PROXY: '/api/proxy',

  // 健康检查
  HEALTH: '/api/health',
} as const;

/**
 * HTTP 状态码常量
 */
export const HttpStatus = {
  // 成功
  OK: 200,
  CREATED: 201,
  NO_CONTENT: 204,

  // 客户端错误
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,

  // 服务端错误
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
  SERVICE_UNAVAILABLE: 503,
  GATEWAY_TIMEOUT: 504,
} as const;

/**
 * 业务错误码常量
 */
export const BusinessErrorCodes = {
  AUTH_FAILED: 40102, // 业务未登录/鉴权失败
} as const;

/**
 * 认证错误码集合（用于判断是否需要重新登录）
 */
export const AUTH_ERROR_CODES: ReadonlySet<number> = new Set<number>([
  BusinessErrorCodes.AUTH_FAILED,
  HttpStatus.UNAUTHORIZED,
]);

/**
 * HTTP 请求头常量
 */
export const HttpHeaders = {
  // 内容类型
  CONTENT_TYPE_JSON: 'application/json',
  CONTENT_TYPE_FORM: 'application/x-www-form-urlencoded',
  CONTENT_TYPE_MULTIPART: 'multipart/form-data',

  // 接受类型
  ACCEPT_JSON: 'application/json',
  // Keep JSON as an explicit fallback so Spring can serialize pre-stream
  // validation/auth failures instead of raising a 406 negotiation error.
  ACCEPT_STREAM: 'text/event-stream, application/json',
  ACCEPT_ALL: '*/*',
  // 认证
  AUTHORIZATION_BEARER_PREFIX: 'Bearer ',

  // 缓存
  CACHE_CONTROL_NO_STORE: 'no-store',
  CACHE_CONTROL_NO_CACHE: 'no-cache',

  // Content-Disposition
  CONTENT_DISPOSITION_INLINE: 'inline',
  CONTENT_DISPOSITION_ATTACHMENT: 'attachment',
} as const;

/**
 * SSE（Server-Sent Events）相关常量
 */
export const SseConstants = {
  /** SSE 完成标记 */
  DONE_TOKEN: '[DONE]',

  /** SSE 事件类型 */
  EVENT_MESSAGE: 'message',
  EVENT_DONE: 'done',

  /** SSE 字段前缀 */
  FIELD_EVENT: 'event:',
  FIELD_DATA: 'data:',
  FIELD_ID: 'id:',
  FIELD_RETRY: 'retry:',

  /** 默认缓冲区大小（非 SSE 响应的行缓冲限制） */
  DEFAULT_BUFFER_SIZE: 2048,

  /** 数据摘要长度（调试用） */
  DATA_SNIPPET_LENGTH: 120,
  BODY_SUMMARY_LENGTH: 500,
} as const;

/**
 * 请求重试相关常量
 */
export const RetryConstants = {
  /** 默认重试延迟基数（毫秒） */
  BASE_DELAY_MS: 500,

  /** 默认重试状态码 */
  RETRY_ON_STATUS_CODES: [429, 503, 504] as const,

  /** 默认重试次数 */
  DEFAULT_RETRIES: 0,
} as const;

/**
 * JSON 解析相关常量
 */
export const JsonParseConstants = {
  /** 控制字符清理正则（非打印字符） */
  CONTROL_CHARS_REGEX: /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g,

  /** 空响应文本预览长度 */
  TEXT_PREVIEW_LENGTH: 500,

  /** 安全整数最大值（用于大数字判断） */
  MAX_SAFE_INTEGER: Number.MAX_SAFE_INTEGER,
} as const;

/**
 * 代理下载相关常量
 */
export const ProxyConstants = {
  /** 默认片段长度（错误信息截取） */
  SNIPPET_LENGTH: 200,

  /** Content-Disposition 匹配模式 */
  FILENAME_STAR_PATTERN: /filename\*\s*=\s*([^;]+)/i,
  FILENAME_QUOTED_PATTERN: /filename\s*=\s*"([^"]+)"/i,
  FILENAME_BARE_PATTERN: /filename\s*=\s*([^;]+)/i,
  CONTENT_RANGE_PATTERN: /bytes\s+\d+-\d+\/(\d+|\*)/i,
} as const;
