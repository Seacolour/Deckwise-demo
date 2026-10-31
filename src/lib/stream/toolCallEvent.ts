/**
 * 工具调用事件模型
 *
 * 对齐 iOS ToolCallEvent.swift — Agent 工具事件（call / result）的统一表示。
 * - call: AI 发起工具调用（UI 显示 spinner + 动作名）
 * - result: 工具执行完成（UI 把 spinner 更新为 ✅）
 */

/** 工具调用阶段 */
export type ToolCallPhase = 'call' | 'result';

/** 结构化工具调用事件 */
export interface ToolCallEvent {
  /** 本地唯一 id（用于 React key） */
  id: string;
  /** 用于关联 call/result 的 id（来自 payload.call_id） */
  callId: string | null;
  /** 工具名称（如 file_list / document_diff） */
  name: string;
  /** call / result */
  phase: ToolCallPhase;
  /** 原始参数 JSON 字符串 */
  argumentsRaw: string | null;
  /** 原始 seq（调试用） */
  seq: number | null;
  /** 该 call 是否已完成（result 到达后置 true） */
  isCompleted: boolean;
  /** UI 展示：动作标题（如"正在获取项目文件..."） */
  displayTitle: string;
  /** UI 展示：参数列表（如 ["document_id=4", "read_draft=true"]） */
  displayParams: string[];
  /** 工具行在正文中的插入位置（UTF-16 offset），用于穿插渲染 */
  insertAtUTF16: number | null;
}

/** 创建空的 ToolCallEvent（call 阶段） */
export function createToolCallEvent(opts: {
  callId: string | null;
  name: string;
  argumentsRaw: string | null;
  seq: number | null;
  displayTitle: string;
  displayParams: string[];
  insertAtUTF16: number | null;
}): ToolCallEvent {
  return {
    id: `tool-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    callId: opts.callId,
    name: opts.name,
    phase: 'call',
    argumentsRaw: opts.argumentsRaw,
    seq: opts.seq,
    isCompleted: false,
    displayTitle: opts.displayTitle,
    displayParams: opts.displayParams,
    insertAtUTF16: opts.insertAtUTF16,
  };
}
