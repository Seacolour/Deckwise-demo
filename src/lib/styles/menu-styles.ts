/**
 * Menu 统一样式
 *
 * 这是 Deckwise 内部菜单的共享 class 组合。
 * 调整 menu 的圆角、间距、宽度等视觉规格时，以这里为准。
 */
import { APP_LAYER_CLASS } from "@/src/lib/ui/layers";

/**
 * Menu 内容容器基础样式类
 */
export const MENU_PANEL_PADDING_PX = 8;
const MENU_PANEL_PADDING_CLASS = 'p-2';
const MENU_ITEM_PADDING_X_CLASS = 'px-2.5';
const MENU_ITEM_PADDING_Y_CLASS = 'py-0';
const MENU_ITEM_CONTENT_GAP_CLASS = 'gap-[9px]';
export const MENU_ITEM_HEIGHT_CLASS = 'h-[36px]';
export const MENU_ITEM_RADIUS_CLASS = 'rounded-[7px]';
const MENU_PANEL_RADIUS_CLASS = 'rounded-[14px]';
export const MENU_ITEM_TEXT_CLASS = '!text-[14px] !font-medium !leading-[20px] [font-family:var(--font-body-stack)]';
const MENU_ITEM_INTERACTIVE_SHELL_CLASS = [
  'deckwise-menu-item',
  MENU_ITEM_RADIUS_CLASS,
  'my-0.5',
].join(' ');
const MENU_ITEM_ROW_LAYOUT_CLASS = [
  'flex',
  'items-center',
  MENU_ITEM_CONTENT_GAP_CLASS,
  MENU_ITEM_HEIGHT_CLASS,
].join(' ');
const PANEL_SURFACE_SHARED_CLASS = [
  'deckwise-menu-content',
  MENU_PANEL_RADIUS_CLASS,
  'border',
  'border-border',
  'shadow-[0_16px_36px_rgba(15,23,42,0.16)]',
  'select-none',
  'scrollbar-capsule',
  'overscroll-contain',
].join(' ');
// Keep menus and floating action bars on a solid card surface. These controls
// frequently stack above dense editor/PDF content, and translucency made hover
// fills, dividers, and text contrast read muddy against the background.
const PANEL_SURFACE_FILL_CLASS = 'bg-card';
const FLOATING_ACTION_BAR_CONTROL_HEIGHT_CLASS = 'h-[30px]';
const FLOATING_ACTION_BAR_BUTTON_PADDING_X_CLASS = 'px-2.5';
const FLOATING_ACTION_BAR_VALUE_PADDING_X_CLASS = 'px-1.5';
const FLOATING_ACTION_BAR_TEXT_CLASS = [
  MENU_ITEM_TEXT_CLASS,
  'font-medium',
].join(' ');
export const MENU_ITEM_ICON_LAYOUT_CLASS = [
  '[&_svg]:pointer-events-none',
  '[&_svg]:shrink-0',
  '[&_svg]:transition-colors',
  "[&_svg:not([class*='size-'])]:size-4",
].join(' ');

export const MENU_SURFACE_CLASS = [
  PANEL_SURFACE_SHARED_CLASS,
  PANEL_SURFACE_FILL_CLASS,
].join(' ');

// Floating action bars currently share the same solid surface contract as
// menus. Keep a local alias so bar-specific layout tokens can still talk about
// a "surface" without duplicating the underlying class list.
const FLOATING_ACTION_BAR_SURFACE_CLASS = MENU_SURFACE_CLASS;

export const MENU_CONTENT_CLASS = [
  MENU_SURFACE_CLASS,
  MENU_PANEL_PADDING_CLASS,
].join(' ');

/**
 * Floating menu surface shared by both custom div-based menus and the Radix
 * dropdown wrapper. Keep menu panel geometry/motion here so editor, card, and
 * materials menus stay visually identical.
 */
export const MENU_FLOATING_CONTENT_CLASS = [
  MENU_CONTENT_CLASS,
  'deckwise-menu-popover',
  APP_LAYER_CLASS.floatingMenu,
  'will-change-[transform,opacity]',
].join(' ');

export const FLOATING_ACTION_BAR_CLASS = [
  FLOATING_ACTION_BAR_SURFACE_CLASS,
  APP_LAYER_CLASS.floatingToolbar,
  'min-w-0',
  'w-auto',
  'flex',
  'items-center',
  'gap-0',
  MENU_PANEL_PADDING_CLASS,
].join(' ');

// ==================== Menu 宽度变体 ====================
/** 小型 Menu（用于选项较少的菜单） */
export const MENU_SMALL_WIDTH = 'min-w-[200px]';

/** 中型 Menu（默认） */
export const MENU_MEDIUM_WIDTH = 'w-[246px] max-w-[calc(100vw-16px)]';

/** 大型 Menu（用于历史会话等有滚动内容的菜单） */
export const MENU_LARGE_WIDTH = 'w-[360px]';
export const MENU_LARGE_MAX_HEIGHT = 'max-h-[70vh]';

export type FloatingMenuPlacement = "above" | "below";

export type FloatingMenuLayout = {
  top: number;
  left: number;
  placement: FloatingMenuPlacement;
};

export type FloatingMenuAnchorRect = {
  top: number;
  left?: number;
  right: number;
  bottom: number;
};

export type SideFloatingMenuPlacement = "left" | "right";

export type SideFloatingMenuLayout = {
  top: number;
  left: number;
  placement: SideFloatingMenuPlacement;
};

type ResolveFloatingMenuLayoutOptions = {
  preferredLeft: number;
  anchorTop: number;
  anchorBottom: number;
  menuWidth: number;
  menuHeight: number;
  viewportWidth: number;
  viewportHeight: number;
  edgeGapPx: number;
  offsetPx: number;
  preferredPlacement?: FloatingMenuPlacement;
};

/**
 * Shared placement rule for custom floating menus:
 * - prefer the requested side
 * - flip to the other side when it has more room
 * - if neither side fully fits, clamp inside the viewport instead of overflowing
 *
 * Pitfall: do not treat "preferred below" as "force below until it overflows".
 * The page-marker menu looked broken because its first open only had ~78px
 * below but ~989px above, yet a too-naive rule still kept it underneath the
 * anchor. The chooser has to compare both sides before clamping.
 */
export function resolveFloatingMenuLayout({
  preferredLeft,
  anchorTop,
  anchorBottom,
  menuWidth,
  menuHeight,
  viewportWidth,
  viewportHeight,
  edgeGapPx,
  offsetPx,
  preferredPlacement = "below",
}: ResolveFloatingMenuLayoutOptions): FloatingMenuLayout {
  const availableBelow = viewportHeight - anchorBottom - offsetPx - edgeGapPx;
  const availableAbove = anchorTop - offsetPx - edgeGapPx;
  const shouldPlaceBelow = preferredPlacement === "below"
    ? menuHeight <= availableBelow || availableBelow >= availableAbove
    : !(menuHeight <= availableAbove || availableAbove >= availableBelow);
  const maxLeft = Math.max(edgeGapPx, viewportWidth - menuWidth - edgeGapPx);
  const left = Math.min(maxLeft, Math.max(edgeGapPx, preferredLeft));
  const top = shouldPlaceBelow
    ? Math.min(
        viewportHeight - menuHeight - edgeGapPx,
        anchorBottom + offsetPx,
      )
    : Math.max(
        edgeGapPx,
        anchorTop - menuHeight - offsetPx,
      );

  return {
    top,
    left,
    placement: shouldPlaceBelow ? "below" : "above",
  };
}

type ResolveIconTriggerFloatingMenuLayoutOptions = {
  anchorRect: FloatingMenuAnchorRect;
  menuWidth: number;
  menuHeight: number;
  viewportWidth: number;
  viewportHeight: number;
  edgeGapPx: number;
  offsetPx?: number;
  preferredPlacement?: FloatingMenuPlacement;
};

const ICON_TRIGGER_MENU_OFFSET_PX = 8;

/**
 * Shared placement rule for icon-triggered custom menus.
 *
 * Contract:
 * - default origin is trigger bottom-right (avoids covering the trigger itself)
 * - still flips above when below has less room
 * - clamps horizontally inside viewport edges
 *
 * Use this in sidebar/header icon menus so placement behavior stays consistent
 * instead of every feature hand-rolling another "where should this open?" rule.
 */
export function resolveIconTriggerFloatingMenuLayout({
  anchorRect,
  menuWidth,
  menuHeight,
  viewportWidth,
  viewportHeight,
  edgeGapPx,
  offsetPx = ICON_TRIGGER_MENU_OFFSET_PX,
  preferredPlacement = "below",
}: ResolveIconTriggerFloatingMenuLayoutOptions): FloatingMenuLayout {
  return resolveFloatingMenuLayout({
    preferredLeft: anchorRect.right + offsetPx,
    anchorTop: anchorRect.top,
    anchorBottom: anchorRect.bottom,
    menuWidth,
    menuHeight,
    viewportWidth,
    viewportHeight,
    edgeGapPx,
    offsetPx,
    preferredPlacement,
  });
}

type ResolveSideFloatingMenuLayoutOptions = {
  anchorRect: Required<FloatingMenuAnchorRect>;
  menuWidth: number;
  menuHeight: number;
  viewportWidth: number;
  viewportHeight: number;
  edgeGapPx: number;
  offsetPx: number;
  preferredPlacement?: SideFloatingMenuPlacement;
};

/**
 * Shared placement rule for menus that expand horizontally from a compact
 * trigger, such as the editor block-handle menu.
 *
 * Contract:
 * - prefer the requested side
 * - flip to the other side when the preferred side has less room
 * - clamp both axes inside the viewport
 *
 * This keeps floating menus in viewport space instead of letting local panels
 * like the file sidebar participate in their stacking or clipping behavior.
 */
export function resolveSideFloatingMenuLayout({
  anchorRect,
  menuWidth,
  menuHeight,
  viewportWidth,
  viewportHeight,
  edgeGapPx,
  offsetPx,
  preferredPlacement = "left",
}: ResolveSideFloatingMenuLayoutOptions): SideFloatingMenuLayout {
  const availableLeft = anchorRect.left - offsetPx - edgeGapPx;
  const availableRight = viewportWidth - anchorRect.right - offsetPx - edgeGapPx;
  const shouldPlaceLeft = preferredPlacement === "left"
    ? menuWidth <= availableLeft || availableLeft >= availableRight
    : !(menuWidth <= availableRight || availableRight >= availableLeft);

  const placement: SideFloatingMenuPlacement = shouldPlaceLeft ? "left" : "right";
  const preferredLeft = placement === "left"
    ? anchorRect.left - menuWidth - offsetPx
    : anchorRect.right + offsetPx;
  const maxLeft = Math.max(edgeGapPx, viewportWidth - menuWidth - edgeGapPx);
  const maxTop = Math.max(edgeGapPx, viewportHeight - menuHeight - edgeGapPx);

  return {
    left: Math.min(maxLeft, Math.max(edgeGapPx, preferredLeft)),
    top: Math.min(maxTop, Math.max(edgeGapPx, anchorRect.top)),
    placement,
  };
}

// ==================== Menu Item 样式 ====================
/**
 * 标准 Menu Item 样式
 */
// Background states intentionally live in `app/globals.css` through the shared
// `.deckwise-menu-item` selectors and `--menu-item-active-bg`. Keep the TS-side
// menu class focused on structure/spacing so hover/highlight colors stay
// centralized in one place.
export const MENU_ITEM_ACTIVE_BG_CLASS = 'bg-[var(--menu-item-active-bg)]';
export const MENU_ITEM_FOCUS_WITHIN_BG_CLASS = 'focus-within:bg-[var(--menu-item-active-bg)]';

export const MENU_ITEM_CLASS = [
  MENU_ITEM_INTERACTIVE_SHELL_CLASS,
  MENU_ITEM_ROW_LAYOUT_CLASS,
  'w-full',
  'cursor-pointer',
  'select-none',
  'border-0',
  'bg-transparent',
  MENU_ITEM_PADDING_X_CLASS,
  MENU_ITEM_PADDING_Y_CLASS,
  MENU_ITEM_TEXT_CLASS,
  'text-left',
  'text-foreground',
].join(' ');

export const MENU_TWO_LINE_ITEM_CLASS = [
  MENU_ITEM_INTERACTIVE_SHELL_CLASS,
  'flex',
  'min-h-[54px]',
  'items-center',
  'cursor-pointer',
  'gap-2',
  MENU_ITEM_PADDING_X_CLASS,
  'py-2',
  MENU_ITEM_TEXT_CLASS,
  'text-left',
  'text-foreground',
].join(' ');

export const FLOATING_ACTION_BAR_BUTTON_CLASS = [
  MENU_ITEM_INTERACTIVE_SHELL_CLASS,
  'inline-flex',
  'items-center',
  'cursor-pointer',
  'gap-2',
  FLOATING_ACTION_BAR_CONTROL_HEIGHT_CLASS,
  FLOATING_ACTION_BAR_BUTTON_PADDING_X_CLASS,
  'py-0',
  FLOATING_ACTION_BAR_TEXT_CLASS,
  'text-foreground',
  'whitespace-nowrap',
  MENU_ITEM_ICON_LAYOUT_CLASS,
].join(' ');

export const FLOATING_ACTION_BAR_ICON_BUTTON_CLASS = [
  FLOATING_ACTION_BAR_BUTTON_CLASS,
  'justify-center',
  'gap-0',
  'w-[30px]',
  'px-0',
].join(' ');

export const FLOATING_ACTION_BAR_VALUE_CLASS = [
  'flex',
  'min-w-0',
  'items-center',
  FLOATING_ACTION_BAR_CONTROL_HEIGHT_CLASS,
  FLOATING_ACTION_BAR_VALUE_PADDING_X_CLASS,
  FLOATING_ACTION_BAR_TEXT_CLASS,
  'tabular-nums',
  'text-foreground',
  'whitespace-nowrap',
].join(' ');

export const FLOATING_ACTION_BAR_INPUT_CLASS = [
  'h-6',
  'min-w-0',
  'border-0',
  'rounded-[6px]',
  'bg-muted/70',
  'px-0',
  'py-0',
  'text-center',
  FLOATING_ACTION_BAR_TEXT_CLASS,
  'leading-none',
  'tabular-nums',
  'text-foreground',
  'shadow-none',
  'appearance-none',
  'outline-none',
  'focus:outline-none',
  'focus:ring-0',
  'focus-visible:outline-none',
  'focus-visible:ring-0',
  'disabled:cursor-default',
  'disabled:opacity-60',
].join(' ');

export const MENU_PANEL_INPUT_CLASS = [
  'h-10',
  'w-full',
  'rounded-2xl',
  'border',
  'border-border/70',
  'bg-card',
  'px-3',
  'text-sm',
  'text-foreground',
  'outline-none',
  'shadow-none',
  'placeholder:text-muted-foreground',
  'focus:outline-none',
  'focus:ring-0',
  'focus-visible:outline-none',
  'focus-visible:ring-0',
].join(' ');

/**
 * Destructive menu items stay neutral until hover/focus/highlight, then turn red.
 * Keep this centralized so file/materials/project menus do not drift.
 */
export const MENU_DESTRUCTIVE_ITEM_CLASS = [
  MENU_ITEM_CLASS,
  'deckwise-menu-item-destructive',
  'transition-[background-color,color]',
  'duration-150',
  'ease-out',
  // Keep only transition plumbing here. The actual destructive color contract
  // lives in app/globals.css so root text, nested labels, and icons all share
  // one source of truth instead of splitting stateful color rules across TS and CSS.
  '[&_.deckwise-menu-label]:transition-colors',
  '[&_.deckwise-menu-label]:duration-150',
  '[&_.deckwise-menu-label]:ease-out',
  '[&_.deckwise-menu-icon]:transition-colors',
  '[&_.deckwise-menu-icon]:duration-150',
  '[&_.deckwise-menu-icon]:ease-out',
].join(' ');

/**
 * Delete / trash actions are a narrower subset of destructive actions.
 * Divider placement belongs to the concrete menu layout via the shared menu
 * separator contract, not to the delete item class itself. Keeping separators
 * inside the item class created a second divider style that drifted from the
 * menu-wide contract.
 */
export const MENU_DELETE_ITEM_CLASS = MENU_DESTRUCTIVE_ITEM_CLASS;

export type MenuItemVariant = "default" | "destructive" | "delete";

export function getMenuItemVariantClass(variant: MenuItemVariant = "default"): string {
  if (variant === "delete") return MENU_DELETE_ITEM_CLASS;
  if (variant === "destructive") return MENU_DESTRUCTIVE_ITEM_CLASS;
  return MENU_ITEM_CLASS;
}

/** 禁用状态的 Menu Item */
export const MENU_ITEM_DISABLED_CLASS = [
  'deckwise-menu-item-disabled',
  'text-muted-foreground',
  'pointer-events-none',
].join(' ');

// ==================== 完整的 Menu 组合样式 ====================
/**
 * 获取标准 Menu 内容样式（可变宽度）
 * @param width - 宽度类名
 */
export function getMenuContentClass(width?: string): string {
  return [
    MENU_CONTENT_CLASS,
    width || MENU_MEDIUM_WIDTH,
  ].join(' ');
}

/**
 * Floating custom menu content with shared Deckwise motion.
 * Use this for non-Radix menus rendered as plain divs/portals.
 */
export function getFloatingMenuContentClass(width?: string): string {
  return [
    MENU_FLOATING_CONTENT_CLASS,
    width || MENU_MEDIUM_WIDTH,
  ].join(' ');
}

/**
 * 获取带最大高度的 Menu 内容样式（用于有滚动内容的菜单）
 * @param width - 宽度类名
 */
export function getScrollableMenuContentClass(width?: string): string {
  return [
    MENU_CONTENT_CLASS,
    width || MENU_LARGE_WIDTH,
    MENU_LARGE_MAX_HEIGHT,
  ].join(' ');
}

// ==================== 其他 Menu 相关样式 ====================
/**
 * Menu 图标样式类
 */
export const MENU_ICON_CLASS = [
  'deckwise-menu-icon',
  'w-5',
  'h-5',
  'text-icon-strong',
  'shrink-0',
  'flex',
  'items-center',
  'justify-center',
].join(' ');

/**
 * Direct icon class for React/Lucide glyphs rendered inside menu items.
 * Use this when there is no dedicated wrapper span.
 */
export const MENU_ICON_GLYPH_CLASS = [
  'deckwise-menu-icon',
  'h-4',
  'w-4',
  'shrink-0',
  'text-icon-strong',
].join(' ');

/** Shared Lucide stroke width for menu glyphs. */
export const MENU_ICON_STROKE_WIDTH = 2.15;

/**
 * Shared right-side indicator slot for submenu triggers.
 * Keep custom hover menus and Radix submenu triggers on the same visual cue.
 */
export const MENU_SUBMENU_TRIGGER_END_ICON_CLASS = [
  'ml-auto',
  'inline-flex',
  'items-center',
  'text-icon-strong',
].join(' ');

/**
 * Shared right-side keyboard shortcut hint for menu rows.
 */
export const MENU_SHORTCUT_CLASS = [
  'ml-auto',
  'shrink-0',
  'pl-3',
  'text-[12px]',
  'font-medium',
  'leading-none',
  'text-[rgba(var(--muted-foreground),0.72)]',
  'whitespace-nowrap',
  '[font-family:var(--font-body-stack)]',
].join(' ');

/**
 * Standard single-line menu label region.
 */
export const MENU_LABEL_CLASS = [
  'deckwise-menu-label',
  'block',
  'min-w-0',
  'flex-1',
  'truncate',
  'leading-[20px]',
].join(' ');

/**
 * Menu header 样式类
 */
export const MENU_HEADER_CLASS = [
  MENU_ITEM_RADIUS_CLASS,
  MENU_ITEM_ROW_LAYOUT_CLASS,
  'cursor-default',
  'text-[14px]',
  'font-normal',
  'leading-[20px]',
  'text-muted-foreground',
  MENU_ITEM_PADDING_X_CLASS,
  MENU_ITEM_PADDING_Y_CLASS,
  // Treat headers as a typography-only variant of a standard menu row so their
  // inset and row height stay aligned with menu items, but keep them out of the
  // interactive hover contract because section titles are not actionable.
  // Keep menu titles in sentence case: capitalize the first word only and let
  // the source string control the rest. Full uppercase reads too shouty for
  // this UI and breaks multi-word labels like "Create file" / "Page 3".
].join(' ');

/**
 * Shared menu divider contract for both Radix dropdown separators and custom
 * div-based menu panels. This is the single place to tune horizontal divider
 * spacing/inset/opacity across menus. Keep a small horizontal inset so menu
 * dividers do not read as hard edge-to-edge panel borders.
 */
export const MENU_SEPARATOR_CLASS = [
  'mx-2',
  'my-1.5',
  'h-px',
  'bg-border-subtle',
].join(' ');

export const FLOATING_ACTION_BAR_DIVIDER_CLASS = [
  'pointer-events-none',
  'self-stretch',
  'mx-0.5',
  'my-1',
  'w-px',
  'shrink-0',
  'bg-border',
].join(' ');

/**
 * Shared menu switch style.
 * Used by controls such as "Show hidden".
 */
export const MENU_SWITCH_CLASS = [
  'deckwise-menu-switch',
  'ml-auto',
  'shrink-0',
].join(' ');

/**
 * 开关轨道基础样式 (灰色背景，关闭状态)
 */
export const MENU_SWITCH_TRACK_CLASS = [
  'deckwise-menu-switch-track',
  'relative',
  'inline-flex',
  'h-5',
  'w-8',
  'shrink-0',
  'cursor-pointer',
  'rounded-full',
  'bg-border',
  'transition-colors',
  'duration-200',
  'ease-in-out',
].join(' ');

/**
 * 开关轨道激活状态 (蓝色背景)
 */
export const MENU_SWITCH_TRACK_ACTIVE_CLASS = [
  'bg-blue-500',
].join(' ');

/**
 * 开关滑块基础样式 (白色，左侧)
 */
export const MENU_SWITCH_THUMB_CLASS = [
  'deckwise-menu-switch-thumb',
  'pointer-events-none',
  'inline-block',
  'h-4',
  'w-4',
  'rounded-full',
  'bg-card',
  'shadow',
  'transition-transform',
  'duration-200',
  'ease-in-out',
  'translate-x-0',
].join(' ');

/**
 * 开关滑块激活状态 (向右移动)
 */
export const MENU_SWITCH_THUMB_ACTIVE_CLASS = [
  'translate-x-3',
].join(' ');
