/**
 * Shared app layer contract for floating UI.
 *
 * Keep viewport-level overlays here so components do not negotiate stacking
 * with local layout containers such as the workspace sidebar or editor panes.
 */
export const APP_LAYER_Z_INDEX = {
  projectBottomChat: 35,
  projectComposer: 36,
  routeCover: 42,
  materialsBackdrop: 180,
  materialsSheet: 190,
  elevatedComposer: 200,
  dropdownMenu: 310,
  floatingToolbar: 170,
  floatingMenu: 310,
  floatingSubmenu: 320,
  billingBackdrop: 330,
  billingSheet: 340,
  selectionOverlay: 999999,
} as const;

export const APP_LAYER_CLASS = {
  dropdownMenu: "z-[var(--app-layer-dropdown-menu)]",
  floatingMenu: "z-[var(--app-layer-floating-menu)]",
  floatingSubmenu: "z-[var(--app-layer-floating-submenu)]",
  floatingToolbar: "z-[var(--app-layer-floating-toolbar)]",
} as const;
