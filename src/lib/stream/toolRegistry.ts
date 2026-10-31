/**
 * 工具注册表
 *
 * 对齐 iOS ToolRegistry.swift — 集中管理"工具名 → 动作文案 / 参数展示策略"。
 *
 * 新增/修改工具展示只需改一处配置，可为不同工具定制：
 * - preferredKeys: 参数排序优先级
 * - hiddenKeys: 隐藏的噪音参数
 * - titleBuilder: 根据参数生成更贴近用户感知的动作文案
 * - paramsBuilder: 特殊工具（如 document_diff）生成紧凑的参数摘要
 */

// ==================== ToolSpec（对齐 iOS ToolRegistry.ToolSpec） ====================

export interface ToolSpec {
  /** UI 动作标题（基础标题） */
  baseTitle: string;
  /** i18n key */
  titleKey: string;
  /** 图标（lucide icon name） */
  icon: string;
  /** 参数展示时优先的 key 顺序 */
  preferredKeys: string[];
  /** 参数展示时需要隐藏的 key */
  hiddenKeys: Set<string>;
  /** 自定义标题构建（可选）：根据 arguments 生成更语义化的标题 */
  titleBuilder?: (name: string, args: Record<string, unknown>) => string;
  /** 自定义参数展示（可选）：完全取代通用 key=value 逻辑 */
  paramsBuilder?: (name: string, args: Record<string, unknown>) => string[];
}

// ==================== ToolMeta（向后兼容的简化视图） ====================

export interface ToolMeta {
  titleKey: string;
  fallbackTitle: string;
  icon: string;
}

// ==================== 注册表 ====================

const TOOL_SPECS: Record<string, ToolSpec> = {
  echo: {
    baseTitle: 'Echo',
    titleKey: 'tool.echo',
    icon: 'Volume2',
    preferredKeys: ['text'],
    hiddenKeys: new Set(),
  },

  file_list: {
    baseTitle: 'List files',
    titleKey: 'tool.fileList',
    icon: 'FolderOpen',
    preferredKeys: [],
    hiddenKeys: new Set(),
  },

  file_content_read: {
    baseTitle: 'Read file',
    titleKey: 'tool.fileContentRead',
    icon: 'FileText',
    preferredKeys: ['file_id', 'start_fragment', 'max_fragments'],
    hiddenKeys: new Set(),
  },

  image_read: {
    baseTitle: 'Read image',
    titleKey: 'tool.imageRead',
    icon: 'Image',
    preferredKeys: ['file_id'],
    hiddenKeys: new Set(),
  },

  scholar_search: {
    baseTitle: 'Scholar search',
    titleKey: 'tool.scholarSearch',
    icon: 'GraduationCap',
    preferredKeys: ['query', 'num'],
    hiddenKeys: new Set(),
  },

  document_read: {
    baseTitle: 'Read document',
    titleKey: 'tool.documentRead',
    icon: 'BookOpen',
    preferredKeys: ['document_id', 'read_draft'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const docId = args['document_id'];
      return docId != null ? `Read document ${docId}` : 'Read document';
    },
  },

  json_document_read: {
    baseTitle: 'Read JSON document',
    titleKey: 'tool.jsonDocumentRead',
    icon: 'BookOpen',
    preferredKeys: ['document_id'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const docId = args['document_id'];
      return docId != null ? `Read JSON document ${docId}` : 'Read JSON document';
    },
  },

  slides_card_locate: {
    baseTitle: 'Locate slide card',
    titleKey: 'tool.slidesCardLocate',
    icon: 'Search',
    preferredKeys: ['document_id', 'page_number', 'max_results'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const pageNumber = args['page_number'];
      if (pageNumber != null) {
        return `Locate slide card page ${pageNumber}`;
      }
      const docId = args['document_id'];
      return docId != null ? `Locate slide card in document ${docId}` : 'Locate slide card';
    },
  },

  slides_theme_apply: {
    baseTitle: 'Switch slide theme',
    titleKey: 'tool.slidesThemeApply',
    icon: 'Palette',
    preferredKeys: ['document_id', 'theme_id'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const themeId = args['theme_id'];
      return typeof themeId === 'string' && themeId.length > 0
        ? `Switch slide theme to ${themeId}`
        : 'Switch slide theme';
    },
  },

  slides_theme_list: {
    baseTitle: 'List slide themes',
    titleKey: 'tool.slidesThemeList',
    icon: 'List',
    preferredKeys: [],
    hiddenKeys: new Set(),
  },

  document_diff: {
    baseTitle: 'Apply diff',
    titleKey: 'tool.documentDiff',
    icon: 'Pencil',
    preferredKeys: ['document_id'],
    // hunks 很长且噪音大，统一隐藏（由 paramsBuilder 输出摘要）
    hiddenKeys: new Set(['hunks']),
    titleBuilder: (_name, args) => {
      const docId = args['document_id'];
      return docId != null ? `Apply diff to document ${docId}` : 'Apply diff';
    },
    paramsBuilder: (_name, args) => {
      const out: string[] = [];
      const docId = args['document_id'];
      if (docId != null) out.push(`document_id=${docId}`);
      // hunks count + ops summary
      const hunks = args['hunks'];
      if (Array.isArray(hunks)) {
        out.push(`hunks=${hunks.length}`);
        const ops = hunks
          .map((h) => (h as Record<string, unknown>)['op'])
          .filter((op): op is string => typeof op === 'string');
        if (ops.length > 0) {
          const unique = [...new Set(ops)];
          out.push(`ops=${unique.join('|')}`);
        }
      }
      return out;
    },
  },

  document_save: {
    baseTitle: 'Save document',
    titleKey: 'tool.documentSave',
    icon: 'Save',
    preferredKeys: ['document_id', 'message'],
    hiddenKeys: new Set(),
  },

  document_locate: {
    baseTitle: 'Locate in document',
    titleKey: 'tool.documentLocate',
    icon: 'Search',
    preferredKeys: ['document_id', 'query', 'max_results', 'context_lines'],
    hiddenKeys: new Set(),
  },

  document_update_summary: {
    baseTitle: 'Update summary',
    titleKey: 'tool.documentUpdateSummary',
    icon: 'FileEdit',
    preferredKeys: ['document_id', 'summary'],
    hiddenKeys: new Set(),
  },

  document_create: {
    baseTitle: 'Create document',
    titleKey: 'tool.documentCreate',
    icon: 'FilePlus',
    preferredKeys: ['title', 'content'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const title = args['title'];
      if (typeof title === 'string' && title.length > 0) {
        return `Create document "${title}"`;
      }
      return 'Create document';
    },
  },

  todo_list: {
    baseTitle: 'Todo list',
    titleKey: 'tool.todoList',
    icon: 'CheckSquare',
    preferredKeys: ['todos'],
    // todos 数组可能很长，统一隐藏由 paramsBuilder 输出摘要
    hiddenKeys: new Set(['todos']),
    paramsBuilder: (_name, args) => {
      const todos = args['todos'];
      if (!Array.isArray(todos)) return [];
      const count = todos.length;
      const completed = todos.filter(
        (t) => (t as Record<string, unknown>)['status'] === 'completed'
      ).length;
      return [`tasks=${count}`, `completed=${completed}`];
    },
  },

  manage_user_rules: {
    baseTitle: 'Manage rules',
    titleKey: 'tool.manageUserRules',
    icon: 'ShieldCheck',
    preferredKeys: ['action', 'rule_id', 'content'],
    hiddenKeys: new Set(),
    titleBuilder: (_name, args) => {
      const action = args['action'];
      if (typeof action === 'string') {
        switch (action) {
          case 'add':    return 'Add rule';
          case 'update': return 'Update rule';
          case 'remove': return 'Remove rule';
          case 'list':   return 'List rules';
          default:       return 'Manage rules';
        }
      }
      return 'Manage rules';
    },
  },

  manage_user_memory: {
    baseTitle: 'Manage memory',
    titleKey: 'tool.manageUserMemory',
    icon: 'Brain',
    preferredKeys: ['action', 'memory_id', 'title'],
    hiddenKeys: new Set(['content']),
    titleBuilder: (_name, args) => {
      const action = args['action'];
      if (typeof action === 'string') {
        switch (action) {
          case 'create': return 'Create memory';
          case 'update': return 'Update memory';
          case 'delete': return 'Delete memory';
          case 'list':   return 'List memories';
          default:       return 'Manage memory';
        }
      }
      return 'Manage memory';
    },
  },

  ask_human: {
    baseTitle: 'Ask you',
    titleKey: 'tool.askHuman',
    icon: 'MessageCircleQuestion',
    preferredKeys: ['question'],
    // options / allow_free_text 由 AskHumanCard 渲染，不在 tool row 展示
    hiddenKeys: new Set(['options', 'allow_free_text']),
    titleBuilder: (_name, args) => {
      const q = args['question'];
      if (typeof q === 'string' && q.length > 0) {
        const preview = q.length > 30 ? q.slice(0, 30) + '…' : q;
        return `Ask: ${preview}`;
      }
      return 'Ask you';
    },
    paramsBuilder: () => {
      // 参数由 AskHumanCard 完整渲染，tool row 不展示冗余参数
      return [];
    },
  },
};

// ==================== 公开 API ====================

/** 获取工具 ToolSpec（完整配置） */
export function getToolSpec(name: string): ToolSpec | null {
  return TOOL_SPECS[name] ?? null;
}

/** 将后端工具名转为紧凑的 PascalCase 展示名，例如 slides_card_structure_read -> SlidesCardStructureRead。 */
export function formatToolNameForDisplay(name: string): string {
  const text = String(name || "").trim();
  if (!text) {
    return "Tool";
  }

  const parts = text
    .split(/[^A-Za-z0-9]+/)
    .map((part) => part.trim())
    .filter(Boolean);
  if (parts.length === 0) {
    return "Tool";
  }

  return parts
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join("");
}

/** 获取工具元信息（向后兼容），未注册的工具返回通用信息 */
export function getToolMeta(name: string): ToolMeta {
  const spec = TOOL_SPECS[name];
  if (spec) {
    return {
      titleKey: spec.titleKey,
      fallbackTitle: spec.baseTitle,
      icon: spec.icon,
    };
  }
  return {
    titleKey: 'tool.unknown',
    fallbackTitle: formatToolNameForDisplay(name),
    icon: 'Wrench',
  };
}

// ==================== 参数格式化（对齐 iOS summarizeArguments） ====================

/**
 * 把原始参数 JSON 格式化为人类可读的展示列表。
 * 使用 ToolSpec 中的 preferredKeys / hiddenKeys / paramsBuilder。
 */
export function formatToolParams(toolName: string, argumentsRaw: string | null): string[] {
  if (!argumentsRaw) return [];

  const spec = TOOL_SPECS[toolName];
  let argsObj: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(argumentsRaw);
    if (typeof parsed === 'object' && parsed !== null) {
      argsObj = parsed as Record<string, unknown>;
    }
  } catch {
    return [];
  }

  // 特殊工具用 paramsBuilder 生成紧凑摘要
  if (spec?.paramsBuilder) {
    return spec.paramsBuilder(toolName, argsObj);
  }

  const hiddenKeys = spec?.hiddenKeys ?? new Set<string>();
  const preferredKeys = spec?.preferredKeys ?? [];

  const allKeys = Object.keys(argsObj).filter((k) => !hiddenKeys.has(k));

  // 按 preferredKeys 排序
  let sortedKeys: string[];
  if (preferredKeys.length > 0) {
    const preferredSet = new Set(preferredKeys);
    const preferred = preferredKeys.filter((k) => allKeys.includes(k));
    const rest = allKeys.filter((k) => !preferredSet.has(k)).sort();
    sortedKeys = [...preferred, ...rest];
  } else {
    sortedKeys = allKeys.sort();
  }

  return sortedKeys
    .map((k) => {
      const v = argsObj[k];
      if (v === undefined || v === null) return null;
      if (typeof v === 'boolean') return `${k}=${v}`;
      if (typeof v === 'number') return `${k}=${v}`;
      if (typeof v === 'string') {
        return v.length > 80 ? `${k}=${v.slice(0, 77)}...` : `${k}=${v}`;
      }
      // 复杂对象不展开
      return `${k}=(object)`;
    })
    .filter((x): x is string => x !== null);
}

/**
 * 生成工具的展示标题和参数（对齐 iOS ToolCallEventParser.display）
 */
export function buildToolDisplay(
  toolName: string,
  argumentsRaw: string | null
): { title: string; params: string[] } {
  const spec = TOOL_SPECS[toolName];
  const baseTitle = spec?.baseTitle ?? formatToolNameForDisplay(toolName);

  const params = formatToolParams(toolName, argumentsRaw);

  // 自定义标题
  if (spec?.titleBuilder) {
    let argsObj: Record<string, unknown> = {};
    if (argumentsRaw) {
      try {
        const parsed = JSON.parse(argumentsRaw);
        if (typeof parsed === 'object' && parsed !== null) {
          argsObj = parsed as Record<string, unknown>;
        }
      } catch { /* ignore */ }
    }
    return { title: spec.titleBuilder(toolName, argsObj), params };
  }

  return { title: baseTitle, params };
}
