/**
 * 全局布局常量。
 */

/** Middle / Chat panel 统一圆角。Keep TS inline styles synced with the CSS token. */
export const PANEL_RADIUS = "var(--panel-radius)";

/** Workspace 主 panel 到屏幕边缘的统一留白 */
export const WORKSPACE_EDGE_INSET_PX = 6;

/** Workspace 主 panel 之间的统一间距（Middle <-> Chat、Slides 双面板间距） */
export const WORKSPACE_PANEL_GAP_PX = 6;

/** Chat 标题栏高度 */
export const CHAT_PANEL_HEADER_HEIGHT_PX = 56;

/** Chat 输入区底部留白 */
export const CHAT_COMPOSER_BOTTOM_GAP_PX = 24;

/** Chat 输入区单行胶囊基线高度 */
export const CHAT_COMPOSER_SINGLE_LINE_HEIGHT_PX = 40;

/** Chat 输入区内容胶囊额外上下空间 */
export const CHAT_COMPOSER_CAPSULE_VERTICAL_PADDING_PX = 16;

/** Chat 输入区外层白卡内边距 */
export const CHAT_COMPOSER_OUTER_PADDING_PX = 10;

/** Chat 输入区外层白卡边框宽度 */
export const CHAT_COMPOSER_BORDER_WIDTH_PX = 1;

/** Chat 输入区单行默认总高度 */
export const CHAT_COMPOSER_DEFAULT_HEIGHT_PX =
  CHAT_COMPOSER_SINGLE_LINE_HEIGHT_PX +
  CHAT_COMPOSER_OUTER_PADDING_PX * 2 +
  CHAT_COMPOSER_BORDER_WIDTH_PX * 2;

/** Materials 文件列表行高。 */
export const PROJECT_FILE_ROW_HEIGHT_PX = 36;

/** 照片墙拖拽 / hover 视觉允许溢出裁剪边界的水平缓冲。 */
export const IMAGE_WALL_OVERFLOW_CLIP_MARGIN = 400;
