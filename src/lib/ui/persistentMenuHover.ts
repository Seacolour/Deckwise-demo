import { useCallback, useLayoutEffect, useRef, type RefCallback, type RefObject } from "react";

const MENU_ITEM_SELECTOR = ".deckwise-menu-item";
const PERSIST_ATTR = "data-menu-persisted";
const DEBUG_PERSISTENT_MENU_HOVER = process.env.NEXT_PUBLIC_DEBUG_PERSISTENT_MENU_HOVER === "1";
const KEYBOARD_NAVIGATION_KEYS = new Set([
  "ArrowUp",
  "ArrowDown",
  "ArrowLeft",
  "ArrowRight",
  "Home",
  "End",
  "PageUp",
  "PageDown",
]);

type InteractionMode = "auto" | "pointer" | "keyboard";

type MenuMemory = {
  index: number | null;
  label: string | null;
};

// Pitfall: Radix dropdown content is torn down and recreated on every open, so
// "last hovered item" cannot live only on the DOM node itself. We keep a tiny
// in-memory cache per explicitly named menu and restore from it on the next
// mount. Generic unnamed menus intentionally do not persist across openings,
// otherwise dynamic label/order changes become a source of state bleed.
const rememberedMenuItems = new Map<string, MenuMemory>();

// Pitfall: not every menu should use persistent hover. If a menu already owns a
// semantic "current item" state (for example a heading-level picker with a
// built-in selected row / checkmark), let that menu opt out instead of layering
// a second active-looking row on top.

function debugPersistentMenuHover(
  container: HTMLElement,
  phase: string,
  detail: Record<string, unknown> = {},
): void {
  if (!DEBUG_PERSISTENT_MENU_HOVER) {
    return;
  }

  console.warn("[menu-persist]", phase, {
    menu:
      container.dataset.deckwiseMenuName
      || container.getAttribute("data-slot")
      || container.className,
    ...detail,
  });
}

function getMenuItems(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(MENU_ITEM_SELECTOR));
}

function isDisabled(item: HTMLElement): boolean {
  return item.hasAttribute("data-disabled") || item.getAttribute("aria-disabled") === "true";
}

function getPersistableItems(container: HTMLElement): HTMLElement[] {
  const items = getMenuItems(container);
  const enabledItems = items.filter((item) => !isDisabled(item));
  return enabledItems.length > 0 ? enabledItems : items;
}

function getItemLabel(item: HTMLElement | null): string | null {
  return item?.textContent?.trim() || null;
}

function getFirstEnabledItem(container: HTMLElement): HTMLElement | null {
  return getPersistableItems(container)[0] ?? null;
}

function getTrackedIndex(container: HTMLElement, item: HTMLElement | null): number | null {
  if (!item) {
    return null;
  }

  const items = getPersistableItems(container);
  const index = items.indexOf(item);
  return index >= 0 ? index : null;
}

function getMenuMemoryKey(container: HTMLElement): string | null {
  const explicitName = container.dataset.deckwiseMenuName?.trim();
  return explicitName ? `name:${explicitName}` : null;
}

function readMenuMemory(container: HTMLElement): MenuMemory | null {
  const key = getMenuMemoryKey(container);
  return key ? rememberedMenuItems.get(key) ?? null : null;
}

function rememberMenuMemory(container: HTMLElement, memory: MenuMemory): void {
  const key = getMenuMemoryKey(container);
  if (!key) {
    return;
  }

  rememberedMenuItems.set(key, memory);
}

function getTrackedItem(container: HTMLElement, memory: MenuMemory): HTMLElement | null {
  const items = getPersistableItems(container);
  if (items.length === 0) {
    return null;
  }

  if (memory.label) {
    const labelMatch = items.find((item) => getItemLabel(item) === memory.label);
    if (labelMatch) {
      return labelMatch;
    }
  }

  if (memory.index !== null) {
    const clampedIndex = Math.min(Math.max(memory.index, 0), items.length - 1);
    return items[clampedIndex] ?? items[0] ?? null;
  }

  return items[0] ?? null;
}

function getPersistedItem(container: HTMLElement): HTMLElement | null {
  return container.querySelector<HTMLElement>(`${MENU_ITEM_SELECTOR}[${PERSIST_ATTR}="true"]`);
}

function setPersisted(container: HTMLElement, item: HTMLElement | null): void {
  const target = item ?? getFirstEnabledItem(container);
  const current = getPersistedItem(container);
  if (current === target) {
    return;
  }

  current?.removeAttribute(PERSIST_ATTR);
  target?.setAttribute(PERSIST_ATTR, "true");

  // Defensive cleanup for legacy states where multiple items may still carry
  // the persisted attribute from previous logic.
  container
    .querySelectorAll<HTMLElement>(`${MENU_ITEM_SELECTOR}[${PERSIST_ATTR}="true"]`)
    .forEach((node) => {
      if (node !== target) {
        node.removeAttribute(PERSIST_ATTR);
      }
    });

  debugPersistentMenuHover(container, "persist.set", {
    label: getItemLabel(target),
  });
}

function getClosestMenuItem(container: HTMLElement, target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof Element)) return null;
  const item = target.closest<HTMLElement>(MENU_ITEM_SELECTOR);
  if (!item || !container.contains(item) || isDisabled(item)) return null;
  return item;
}

export function attachPersistentMenuHover(container: HTMLElement): () => void {
  let trackedMemory = readMenuMemory(container) ?? {
    index: 0,
    label: getItemLabel(getFirstEnabledItem(container)),
  };
  let interactionMode: InteractionMode = "auto";
  let keyboardSyncFrameId: number | null = null;

  const updateTrackedMemory = (item: HTMLElement | null) => {
    trackedMemory = {
      index: getTrackedIndex(container, item),
      label: getItemLabel(item),
    };
    rememberMenuMemory(container, trackedMemory);
  };

  const syncPersistedFromMemory = () => {
    setPersisted(container, getTrackedItem(container, trackedMemory));
  };

  const syncPersistedFromKeyboard = () => {
    const highlighted = container.querySelector<HTMLElement>(`${MENU_ITEM_SELECTOR}[data-highlighted]`);
    const focused = getClosestMenuItem(container, document.activeElement);
    const candidate = highlighted && !isDisabled(highlighted) ? highlighted : focused;
    if (!candidate) {
      return;
    }

    updateTrackedMemory(candidate);
    setPersisted(container, candidate);
  };

  const handlePointerMove = (event: PointerEvent) => {
    const item = getClosestMenuItem(container, event.target);
    if (!item) {
      return;
    }

    interactionMode = "pointer";
    updateTrackedMemory(item);
    setPersisted(container, item);
  };

  const handlePointerLeave = (event: PointerEvent) => {
    if (event.relatedTarget instanceof Node && container.contains(event.relatedTarget)) {
      return;
    }

    syncPersistedFromMemory();
  };

  const handleFocusIn = (event: FocusEvent) => {
    const item = getClosestMenuItem(container, event.target);
    if (!item) {
      return;
    }

    // Ignore pointer/autofocus focus churn and only sync from focus when users
    // are keyboard-navigating. This avoids focus-vs-pointer state tug-of-war.
    if (interactionMode !== "keyboard") {
      return;
    }

    updateTrackedMemory(item);
    setPersisted(container, item);
  };

  const handleKeyDown = (event: KeyboardEvent) => {
    if (!KEYBOARD_NAVIGATION_KEYS.has(event.key)) {
      return;
    }

    interactionMode = "keyboard";
    if (keyboardSyncFrameId !== null) {
      cancelAnimationFrame(keyboardSyncFrameId);
    }
    keyboardSyncFrameId = requestAnimationFrame(() => {
      keyboardSyncFrameId = null;
      syncPersistedFromKeyboard();
    });
  };

  debugPersistentMenuHover(container, "attach", {
    rememberedLabel: trackedMemory.label,
    rememberedIndex: trackedMemory.index,
  });

  syncPersistedFromMemory();
  container.addEventListener("pointermove", handlePointerMove);
  container.addEventListener("pointerleave", handlePointerLeave);
  container.addEventListener("focusin", handleFocusIn);
  container.addEventListener("keydown", handleKeyDown);

  return () => {
    if (keyboardSyncFrameId !== null) {
      cancelAnimationFrame(keyboardSyncFrameId);
    }

    container.removeEventListener("pointermove", handlePointerMove);
    container.removeEventListener("pointerleave", handlePointerLeave);
    container.removeEventListener("focusin", handleFocusIn);
    container.removeEventListener("keydown", handleKeyDown);
  };
}

export function usePersistentMenuHover(
  ref: RefObject<HTMLElement | null>,
  enabled = true,
): void {
  const attachedNodeRef = useRef<HTMLElement | null>(null);
  const cleanupRef = useRef<(() => void) | null>(null);

  useLayoutEffect(() => {
    const node = enabled ? ref.current : null;

    if (attachedNodeRef.current === node) {
      return;
    }

    cleanupRef.current?.();
    cleanupRef.current = null;
    attachedNodeRef.current = node;

    if (node) {
      cleanupRef.current = attachPersistentMenuHover(node);
    }
  });

  useLayoutEffect(() => {
    return () => {
      cleanupRef.current?.();
      cleanupRef.current = null;
      attachedNodeRef.current = null;
    };
  }, []);
}

export function usePersistentMenuHoverRef<T extends HTMLElement>(
  enabled = true,
): RefCallback<T> {
  const cleanupRef = useRef<(() => void) | null>(null);
  const attachedNodeRef = useRef<T | null>(null);

  useLayoutEffect(() => {
    if (enabled) {
      return;
    }

    cleanupRef.current?.();
    cleanupRef.current = null;
    attachedNodeRef.current = null;
  }, [enabled]);

  useLayoutEffect(() => {
    return () => {
      cleanupRef.current?.();
      cleanupRef.current = null;
      attachedNodeRef.current = null;
    };
  }, []);

  return useCallback((node: T | null) => {
    // Pitfall: composed callback refs can be invoked multiple times with the
    // same DOM node during a single mount. Re-attaching here would reset the
    // remembered hover state back to the default item.
    if (attachedNodeRef.current === node) {
      return;
    }

    cleanupRef.current?.();
    cleanupRef.current = null;
    attachedNodeRef.current = node;

    if (!enabled || !node) {
      return;
    }

    cleanupRef.current = attachPersistentMenuHover(node);
  }, [enabled]);
}
