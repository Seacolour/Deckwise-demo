package com.xiaoyuan.paperpuppyagent.core.tool.document;

/**
 * Markdown 编辑操作类型。
 */
public enum EditOp {
    INSERT, // 在指定位置后插入新行
    DELETE, // 删除指定范围的行
    REPLACE // 替换指定范围的行
}
