"use client"

import * as React from "react"
import * as DropdownMenuPrimitive from "@radix-ui/react-dropdown-menu"
import { CheckIcon, ChevronRightIcon, CircleIcon } from "lucide-react"

import {
  getFloatingMenuContentClass,
  MENU_HEADER_CLASS,
  MENU_ICON_CLASS,
  MENU_ITEM_ICON_LAYOUT_CLASS,
  MENU_ITEM_TEXT_CLASS,
  MENU_ITEM_CLASS,
  MENU_PANEL_PADDING_PX,
  MENU_SUBMENU_TRIGGER_END_ICON_CLASS,
  MENU_SEPARATOR_CLASS,
  getMenuItemVariantClass,
  type MenuItemVariant,
} from "@/src/lib/styles/menu-styles"
import { usePersistentMenuHoverRef } from "@/src/lib/ui/persistentMenuHover"
import { APP_LAYER_CLASS } from "@/src/lib/ui/layers"
import {
  registerTransientUiOpen,
  TRANSIENT_UI_EVENTS,
  unregisterTransientUiOpen,
} from "@/src/lib/events/transientUi"
import { cn } from "@/src/lib/utils"

const DROPDOWN_MENU_PORTAL_LAYER_CLASS = APP_LAYER_CLASS.dropdownMenu
const DROPDOWN_SUBMENU_VISIBLE_GAP_PX = 8
const DROPDOWN_MENU_ITEM_BASE_CLASS =
  "relative cursor-pointer outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
export const DROPDOWN_MENU_CLOSE_ANIMATION_MS = 120
const DROPDOWN_MENU_HOVER_OPEN_DELAY_MS = 40
const DROPDOWN_MENU_HOVER_CLOSE_DELAY_MS = 140
// Submenus are anchored to the trigger row inside the shared padded menu panel,
// so add panel padding to preserve a visible gap between level-1 and level-2 surfaces.
const DROPDOWN_SUBMENU_DEFAULT_SIDE_OFFSET =
  MENU_PANEL_PADDING_PX + DROPDOWN_SUBMENU_VISIBLE_GAP_PX
// Vertical submenu contract:
// level-2 menus should visually align their first actionable row with the
// level-1 row that opened them. Because the submenu panel has the same shared
// shell padding as the parent menu, its first item would otherwise sit too low.
// Compensate in Popper positioning instead of CSS transforms so the shared
// open/close scale animation keeps owning `transform`.
const DROPDOWN_SUBMENU_DEFAULT_ALIGN_OFFSET = -MENU_PANEL_PADDING_PX

type DropdownMenuHoverContextValue = {
  openOnHover: boolean
  scheduleHoverOpen: () => void
  scheduleHoverClose: () => void
  cancelHoverClose: () => void
  clearHoverTimers: () => void
}

const DropdownMenuHoverContext =
  React.createContext<DropdownMenuHoverContextValue | null>(null)

function composeEventHandlers<EventType extends { defaultPrevented?: boolean }>(
  userEventHandler: ((event: EventType) => void) | undefined,
  internalEventHandler: (event: EventType) => void,
) {
  return (event: EventType) => {
    userEventHandler?.(event)
    if (!event.defaultPrevented) {
      internalEventHandler(event)
    }
  }
}

function DropdownMenu({
  modal = false,
  open: controlledOpen,
  defaultOpen,
  onOpenChange,
  openOnHover = false,
  hoverOpenDelayMs = DROPDOWN_MENU_HOVER_OPEN_DELAY_MS,
  hoverCloseDelayMs = DROPDOWN_MENU_HOVER_CLOSE_DELAY_MS,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Root> & {
  openOnHover?: boolean
  hoverOpenDelayMs?: number
  hoverCloseDelayMs?: number
}) {
  const isControlled = controlledOpen !== undefined
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(defaultOpen ?? false)
  const open = isControlled ? controlledOpen : uncontrolledOpen
  const transientUiId = React.useId()
  const openTimerRef = React.useRef<number | null>(null)
  const closeTimerRef = React.useRef<number | null>(null)

  const clearHoverTimers = React.useCallback(() => {
    if (typeof window === "undefined") {
      return
    }

    if (openTimerRef.current !== null) {
      window.clearTimeout(openTimerRef.current)
      openTimerRef.current = null
    }

    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current)
      closeTimerRef.current = null
    }
  }, [])

  const cancelHoverClose = React.useCallback(() => {
    if (typeof window === "undefined" || closeTimerRef.current === null) {
      return
    }

    window.clearTimeout(closeTimerRef.current)
    closeTimerRef.current = null
  }, [])

  const setOpen = React.useCallback((nextOpen: boolean) => {
    clearHoverTimers()
    if (!isControlled) {
      setUncontrolledOpen(nextOpen)
    }
    onOpenChange?.(nextOpen)
  }, [clearHoverTimers, isControlled, onOpenChange])

  const scheduleHoverOpen = React.useCallback(() => {
    if (!openOnHover || typeof window === "undefined") {
      return
    }

    cancelHoverClose()

    if (open) {
      return
    }

    if (openTimerRef.current !== null) {
      return
    }

    openTimerRef.current = window.setTimeout(() => {
      openTimerRef.current = null
      setOpen(true)
    }, hoverOpenDelayMs)
  }, [cancelHoverClose, hoverOpenDelayMs, open, openOnHover, setOpen])

  const scheduleHoverClose = React.useCallback(() => {
    if (!openOnHover || typeof window === "undefined") {
      return
    }

    if (openTimerRef.current !== null) {
      window.clearTimeout(openTimerRef.current)
      openTimerRef.current = null
    }

    if (!open || closeTimerRef.current !== null) {
      return
    }

    closeTimerRef.current = window.setTimeout(() => {
      closeTimerRef.current = null
      setOpen(false)
    }, hoverCloseDelayMs)
  }, [hoverCloseDelayMs, open, openOnHover, setOpen])

  React.useEffect(() => {
    return () => {
      clearHoverTimers()
    }
  }, [clearHoverTimers])

  React.useEffect(() => {
    if (!open) {
      unregisterTransientUiOpen(transientUiId)
      return
    }

    registerTransientUiOpen(transientUiId)
    return () => {
      unregisterTransientUiOpen(transientUiId)
    }
  }, [open, transientUiId])

  React.useEffect(() => {
    if (!open || typeof window === "undefined") {
      return
    }

    // Embedded surfaces such as slide preview iframes cannot participate in
    // Radix outside-pointer detection. Listen for the shared transient-dismiss
    // request so dropdowns close through the same path as normal outside clicks.
    const handleDismissRequested = () => {
      setOpen(false)
    }

    window.addEventListener(
      TRANSIENT_UI_EVENTS.DISMISS_REQUESTED,
      handleDismissRequested as EventListener,
    )

    return () => {
      window.removeEventListener(
        TRANSIENT_UI_EVENTS.DISMISS_REQUESTED,
        handleDismissRequested as EventListener,
      )
    }
  }, [open, setOpen])

  const hoverContextValue = React.useMemo<DropdownMenuHoverContextValue>(() => ({
    openOnHover,
    scheduleHoverOpen,
    scheduleHoverClose,
    cancelHoverClose,
    clearHoverTimers,
  }), [
    cancelHoverClose,
    clearHoverTimers,
    openOnHover,
    scheduleHoverClose,
    scheduleHoverOpen,
  ])

  return (
    <DropdownMenuHoverContext.Provider value={hoverContextValue}>
      <DropdownMenuPrimitive.Root
        data-slot="dropdown-menu"
        modal={modal}
        open={open}
        onOpenChange={setOpen}
        {...props}
      />
    </DropdownMenuHoverContext.Provider>
  )
}

function DropdownMenuPortal({
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Portal>) {
  return (
    <DropdownMenuPrimitive.Portal data-slot="dropdown-menu-portal" {...props} />
  )
}

function DropdownMenuTrigger({
  onPointerEnter,
  onPointerLeave,
  onPointerDown,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Trigger>) {
  const hoverContext = React.useContext(DropdownMenuHoverContext)

  return (
    <DropdownMenuPrimitive.Trigger
      data-slot="dropdown-menu-trigger"
      {...props}
      onPointerEnter={composeEventHandlers(onPointerEnter, (event) => {
        if (hoverContext?.openOnHover && event.pointerType === "mouse") {
          hoverContext.scheduleHoverOpen()
        }
      })}
      onPointerLeave={composeEventHandlers(onPointerLeave, (event) => {
        if (hoverContext?.openOnHover && event.pointerType === "mouse") {
          hoverContext.scheduleHoverClose()
        }
      })}
      onPointerDown={composeEventHandlers(onPointerDown, () => {
        hoverContext?.clearHoverTimers()
      })}
    />
  )
}

function DropdownMenuContent({
  className,
  disablePersistentHover = true,
  sideOffset = 4,
  collisionPadding = 8,
  sticky = "always",
  onPointerEnter,
  onPointerLeave,
  onCloseAutoFocus,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Content> & {
  disablePersistentHover?: boolean
}) {
  // KISS guard:
  // Radix dropdowns already own pointer + keyboard highlight state. Keeping the
  // extra persistent-hover layer enabled by default makes hover transitions
  // harder to reason about and can cause "previous row flashes" while moving
  // between items. Opt in only for menus that demonstrably need memory across
  // pointer gaps; the common dropdown path should stay on plain Radix behavior.
  const contentRef = usePersistentMenuHoverRef<HTMLDivElement>(!disablePersistentHover)
  const hoverContext = React.useContext(DropdownMenuHoverContext)

  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.Content
        ref={contentRef}
        data-slot="dropdown-menu-content"
        data-persistent-hover-disabled={disablePersistentHover ? "true" : undefined}
        sideOffset={sideOffset}
        collisionPadding={collisionPadding}
        sticky={sticky}
        onPointerEnter={composeEventHandlers(onPointerEnter, (event) => {
          if (hoverContext?.openOnHover && event.pointerType === "mouse") {
            hoverContext.cancelHoverClose()
          }
        })}
        onPointerLeave={composeEventHandlers(onPointerLeave, (event) => {
          if (hoverContext?.openOnHover && event.pointerType === "mouse") {
            hoverContext.scheduleHoverClose()
          }
        })}
        onCloseAutoFocus={composeEventHandlers(onCloseAutoFocus, (event) => {
          event.preventDefault()
        })}
        className={cn(
          getFloatingMenuContentClass(),
          `${DROPDOWN_MENU_PORTAL_LAYER_CLASS} max-h-(--radix-dropdown-menu-content-available-height) origin-(--radix-dropdown-menu-content-transform-origin) overflow-x-hidden overflow-y-auto outline-none`,
          className
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  )
}

function DropdownMenuGroup({
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Group>) {
  return (
    <DropdownMenuPrimitive.Group data-slot="dropdown-menu-group" {...props} />
  )
}

function DropdownMenuItem({
  className,
  inset,
  variant = "default",
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Item> & {
  inset?: boolean
  variant?: MenuItemVariant
}) {
  const itemVariantClass = getMenuItemVariantClass(variant)

  return (
    <DropdownMenuPrimitive.Item
      data-slot="dropdown-menu-item"
      data-inset={inset}
      data-variant={variant}
      className={cn(
        itemVariantClass,
        DROPDOWN_MENU_ITEM_BASE_CLASS,
        variant === "default"
          ? "data-[highlighted]:text-foreground data-[inset]:pl-8"
          : "data-[inset]:pl-8",
        MENU_ITEM_ICON_LAYOUT_CLASS,
        className
      )}
      {...props}
    />
  )
}

function DropdownMenuCheckboxItem({
  className,
  children,
  checked,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.CheckboxItem>) {
  return (
    <DropdownMenuPrimitive.CheckboxItem
      data-slot="dropdown-menu-checkbox-item"
      className={cn(
        MENU_ITEM_CLASS,
        DROPDOWN_MENU_ITEM_BASE_CLASS,
        "pr-3 pl-8 data-[highlighted]:text-foreground",
        MENU_ITEM_ICON_LAYOUT_CLASS,
        className
      )}
      checked={checked}
      {...props}
    >
      <span className="pointer-events-none absolute left-2 flex size-3.5 items-center justify-center">
        <DropdownMenuPrimitive.ItemIndicator>
          <CheckIcon className="size-4 text-icon-strong" />
        </DropdownMenuPrimitive.ItemIndicator>
      </span>
      {children}
    </DropdownMenuPrimitive.CheckboxItem>
  )
}

function DropdownMenuRadioGroup({
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.RadioGroup>) {
  return (
    <DropdownMenuPrimitive.RadioGroup
      data-slot="dropdown-menu-radio-group"
      {...props}
    />
  )
}

function DropdownMenuRadioItem({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.RadioItem>) {
  return (
    <DropdownMenuPrimitive.RadioItem
      data-slot="dropdown-menu-radio-item"
      className={cn(
        MENU_ITEM_CLASS,
        DROPDOWN_MENU_ITEM_BASE_CLASS,
        "pr-3 pl-8 data-[highlighted]:text-foreground",
        MENU_ITEM_ICON_LAYOUT_CLASS,
        className
      )}
      {...props}
    >
      <span className="pointer-events-none absolute left-2 flex size-3.5 items-center justify-center">
        <DropdownMenuPrimitive.ItemIndicator>
          <CircleIcon className="size-2 fill-current text-icon-strong" />
        </DropdownMenuPrimitive.ItemIndicator>
      </span>
      {children}
    </DropdownMenuPrimitive.RadioItem>
  )
}

function DropdownMenuLabel({
  className,
  inset,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Label> & {
  inset?: boolean
}) {
  return (
    <DropdownMenuPrimitive.Label
      data-slot="dropdown-menu-label"
      data-inset={inset}
      className={cn(
        "px-2 py-1.5 text-sm font-medium data-[inset]:pl-8",
        className
      )}
      {...props}
    />
  )
}

function DropdownMenuHeader({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="dropdown-menu-header"
      className={cn(MENU_HEADER_CLASS, className)}
      {...props}
    />
  )
}

function DropdownMenuIcon({
  className,
  ...props
}: React.ComponentProps<"span">) {
  return (
    <span
      data-slot="dropdown-menu-icon"
      className={cn(MENU_ICON_CLASS, className)}
      {...props}
    />
  )
}

function DropdownMenuSeparator({
  className,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Separator>) {
  return (
    <DropdownMenuPrimitive.Separator
      data-slot="dropdown-menu-separator"
      className={cn(MENU_SEPARATOR_CLASS, className)}
      {...props}
    />
  )
}

function DropdownMenuShortcut({
  className,
  ...props
}: React.ComponentProps<"span">) {
  return (
    <span
      data-slot="dropdown-menu-shortcut"
      className={cn(
        "ml-auto text-muted-foreground tracking-normal",
        MENU_ITEM_TEXT_CLASS,
        className
      )}
      {...props}
    />
  )
}

function DropdownMenuSub({
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.Sub>) {
  return <DropdownMenuPrimitive.Sub data-slot="dropdown-menu-sub" {...props} />
}

function DropdownMenuSubTrigger({
  className,
  inset,
  variant = "default",
  endIcon,
  children,
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.SubTrigger> & {
  inset?: boolean
  variant?: MenuItemVariant
  // Optional right-side indicator. Useful for replacing the default arrow with
  // a loading spinner while keeping submenu trigger layout stable.
  endIcon?: React.ReactNode
}) {
  const itemVariantClass = getMenuItemVariantClass(variant)

  return (
    <DropdownMenuPrimitive.SubTrigger
      data-slot="dropdown-menu-sub-trigger"
      data-inset={inset}
      className={cn(
        itemVariantClass,
        DROPDOWN_MENU_ITEM_BASE_CLASS,
        "data-[state=open]:text-foreground data-[inset]:pl-8",
        MENU_ITEM_ICON_LAYOUT_CLASS,
        className
      )}
      {...props}
    >
      {children}
      <span className={MENU_SUBMENU_TRIGGER_END_ICON_CLASS}>
        {endIcon ?? <ChevronRightIcon className="size-4" />}
      </span>
    </DropdownMenuPrimitive.SubTrigger>
  )
}

function DropdownMenuSubContent({
  className,
  disablePersistentHover = true,
  sideOffset = DROPDOWN_SUBMENU_DEFAULT_SIDE_OFFSET,
  alignOffset = DROPDOWN_SUBMENU_DEFAULT_ALIGN_OFFSET,
  collisionPadding = 8,
  sticky = "always",
  ...props
}: React.ComponentProps<typeof DropdownMenuPrimitive.SubContent> & {
  disablePersistentHover?: boolean
}) {
  const contentRef = usePersistentMenuHoverRef<HTMLDivElement>(!disablePersistentHover)

  return (
    <DropdownMenuPrimitive.Portal>
      <DropdownMenuPrimitive.SubContent
        ref={contentRef}
        data-slot="dropdown-menu-sub-content"
        data-persistent-hover-disabled={disablePersistentHover ? "true" : undefined}
        sideOffset={sideOffset}
        alignOffset={alignOffset}
        collisionPadding={collisionPadding}
        sticky={sticky}
        className={cn(
          getFloatingMenuContentClass(),
          `${DROPDOWN_MENU_PORTAL_LAYER_CLASS} max-h-(--radix-dropdown-menu-content-available-height) origin-(--radix-dropdown-menu-content-transform-origin) overflow-x-hidden overflow-y-auto outline-none`,
          className
        )}
        {...props}
      />
    </DropdownMenuPrimitive.Portal>
  )
}

export {
  DropdownMenu,
  DropdownMenuPortal,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuHeader,
  DropdownMenuIcon,
  DropdownMenuLabel,
  DropdownMenuItem,
  DropdownMenuCheckboxItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuShortcut,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
}
