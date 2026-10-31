/**
 * 统一的存储键常量
 * 集中管理所有 localStorage 和 sessionStorage 的键名
 */

/**
 * localStorage 键
 */
export const LocalStorageKeys = {
  // 认证相关
  TOKEN: 'token',
  USER: 'user',
  ENV_SIGNATURE: 'env_signature',
  USER_CHECKIN_LAST_DATE_PREFIX: 'checkin:lastDate:',
  USER_CHECKIN_PENDING_PREFIX: 'checkin:pending:',

  // 布局相关
  SLIDES_SPLIT_RATIO: 'layout:slides-split-ratio',
  SLIDES_FLOATING_PREVIEW_LAYOUT: 'layout:slides-floating-preview-layout',
  THEME_PREFERENCE: 'appearance:theme',
  CHAT_MODEL_PREFERENCE: 'chat:model-preference:v1',
  CHAT_INPUT_DRAFT_PREFIX: 'chat:input-draft:',
  SLIDES_DELETE_TRANSIENT_OUTLINE_AFTER_AUTO_GENERATION: 'slides:delete-transient-outline-after-auto-generation:v1',

  // 项目相关
  LAST_VIEWED_NOTE_PREFIX: 'project:', // 使用时拼接 `${projectId}:lastViewedNote:v1`

  // 调试标志
  DEBUG_STREAM: 'debug:stream',
  DEBUG_ANIM: 'debug:anim',
  DEBUG_ANIM_VERBOSE: 'debug:anim-verbose',
  DEBUG_PROJECTS: 'debug:projects',
  DEBUG_FILES: 'debug:files',
  DEBUG_OFFICE_UPLOAD: 'debug:office-upload',
  DEBUG_IMAGE_PREVIEW: 'debug:image-preview',
  DEBUG_SHEET_PREVIEW: 'debug:sheet-preview',
  DEBUG_CHAT_DND: 'debug:chat-dnd',
  DEBUG_CHAT_SESSION_REVEAL: 'debug:chat-session-reveal',
  DEBUG_CHAT_DISABLE_TITLE_SYNC: 'debug:chat-disable-title-sync',
  DEBUG_CHAT_LEGACY_STREAM_DONE: 'debug:chat-legacy-stream-done',
} as const;

/**
 * sessionStorage 键
 */
export const SessionStorageKeys = {
  // 聊天相关
  CHAT_CURRENT_SID: 'chat:currentSid',
  SESSION_MESSAGES_PREFIX: 'session:', // 使用时拼接 `${sid}:messages`
  SESSION_LOADED_PREFIX: 'session:', // 使用时拼接 `${sid}:loaded`
  PROJECT_START_PROMPT_PREFIX: 'project:startPrompt:',

  // 遗留数据清理
  MIDDLE_SCHOLAR_DATA: 'middle:scholar:data',
  MIDDLE_AUTHOR_DATA: 'middle:author:data',
} as const;

/**
 * IndexedDB 相关常量
 */
export const IndexedDB = {
  DB_NAME: 'JuziLabStorage',
  DB_VERSION: 5,

  // Object Store 名称
  STORE_SCHOLAR_ARTICLES: 'scholarArticles',
  STORE_OSS_IMAGES: 'ossImages',
  STORE_DERIVED_ASSETS: 'derivedAssets',

  // 遗留的已删除 Store（用于清理）
  LEGACY_STORE_PDFS: 'pdfs',
  LEGACY_STORE_OSS_PDFS: 'ossPdfs',
} as const;

/**
 * 构建动态存储键的辅助函数
 */
export const StorageKeyBuilders = {
  // Problem: one global chat sid key lets project A reuse project B's active session.
  // Solution: scope sid by project id and use this key as the source of truth on project pages.
  chatCurrentSid: (projectId: string) =>
    `${SessionStorageKeys.CHAT_CURRENT_SID}:${projectId}`,

  projectStartPrompt: (projectId: string) =>
    `${SessionStorageKeys.PROJECT_START_PROMPT_PREFIX}${projectId}`,

  chatInputDraft: (scope: string) =>
    `${LocalStorageKeys.CHAT_INPUT_DRAFT_PREFIX}${encodeURIComponent(scope)}:v1`,

  /** 构建项目最后查看笔记的存储键 */
  lastViewedNote: (projectId: string) =>
    `${LocalStorageKeys.LAST_VIEWED_NOTE_PREFIX}${projectId}:lastViewedNote:v1`,

  /** 构建用户每日签到日期缓存键 */
  userCheckinLastDate: (userKey: string) =>
    `${LocalStorageKeys.USER_CHECKIN_LAST_DATE_PREFIX}${userKey}`,

  /** 构建用户每日签到请求中的短期防抖键 */
  userCheckinPending: (userKey: string) =>
    `${LocalStorageKeys.USER_CHECKIN_PENDING_PREFIX}${userKey}`,

  /** 构建会话消息的存储键 */
  sessionMessages: (sid: string) =>
    `${SessionStorageKeys.SESSION_MESSAGES_PREFIX}${sid}:messages`,

  /** 构建会话加载状态的存储键 */
  sessionLoaded: (sid: string) =>
    `${SessionStorageKeys.SESSION_LOADED_PREFIX}${sid}:loaded`,

} as const;
