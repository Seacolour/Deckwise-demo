/**
 * ask_human 工具的结构化数据模型
 *
 * 对齐 iOS AskHumanEvent.swift — 从 ToolCallEvent.argumentsRaw 解析出
 * question / options / allowFreeText，供 AskHumanCard 渲染交互式回答框。
 */

/** 单个选项（label + 可选描述） */
export interface AskHumanOption {
  label: string;
  description: string;
}

/** ask_human 工具的解析结果 */
export interface AskHumanEvent {
  /** 提问内容 */
  question: string;
  /** 预定义选项（可为空） */
  options: AskHumanOption[];
  /** 是否允许自由文本输入（默认 true） */
  allowFreeText: boolean;
}

/**
 * 从 ask_human JSON 对象中提取 argumentsRaw 字符串。
 *
 * 统一处理多种后端格式：
 * 1. { payload: "..." }         → 直接取 string
 * 2. { payload: { question, options, ... } } → JSON.stringify
 * 3. { question, options, ... } → 扁平格式，组装后 JSON.stringify
 *
 * 供 toolCallEventParser / toolSnippetExtractor / history 共用，避免重复。
 */
export function extractAskHumanArgumentsRaw(obj: Record<string, unknown>): string | null {
  const payloadObj = obj.payload;
  if (typeof payloadObj === 'string') return payloadObj;
  if (payloadObj && typeof payloadObj === 'object') {
    try { return JSON.stringify(payloadObj); } catch { return null; }
  }
  if (typeof obj.question === 'string') {
    const askObj: Record<string, unknown> = {};
    if (obj.question) askObj.question = obj.question;
    if (obj.options) askObj.options = obj.options;
    if (obj.allow_free_text !== undefined) askObj.allow_free_text = obj.allow_free_text;
    try { return JSON.stringify(askObj); } catch { return null; }
  }
  return null;
}

/**
 * 尝试从 ToolCallEvent.argumentsRaw 解析 AskHumanEvent。
 * 容错：字段缺失时使用默认值。返回 null 表示解析失败。
 */
export function parseAskHumanEvent(argumentsRaw: string | null): AskHumanEvent | null {
  if (!argumentsRaw) return null;

  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(argumentsRaw) as Record<string, unknown>;
  } catch {
    return null;
  }

  const question = obj.question;
  if (typeof question !== 'string' || !question.trim()) return null;

  const parsedOptions: AskHumanOption[] = [];
  const optionsArray = obj.options;
  if (Array.isArray(optionsArray)) {
    for (const optObj of optionsArray) {
      if (typeof optObj !== 'object' || optObj === null) continue;
      const opt = optObj as Record<string, unknown>;
      const label = opt.label;
      if (typeof label !== 'string') continue;
      const desc = typeof opt.description === 'string' ? opt.description : '';
      parsedOptions.push({ label, description: desc });
    }
  }

  const allowFreeText = typeof obj.allow_free_text === 'boolean' ? obj.allow_free_text : true;

  return {
    question,
    options: parsedOptions,
    allowFreeText,
  };
}
