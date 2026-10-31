"use client";

import { type ComponentProps, type ReactNode, useLayoutEffect, useRef } from "react";
import { X } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/src/components/_shared/ui/sheet";
import { dispatchAppCenteredSheetWillOpen } from "@/src/lib/events/appCenteredSheet";
import { cn } from "@/src/lib/utils";

type AppCenteredSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  modal?: ComponentProps<typeof Sheet>["modal"];
  title: string;
  description?: string;
  closeLabel: string;
  children: ReactNode;
  dialogClassName?: string;
  overlayClassName?: string;
  contentClassName?: string;
  onFocusOutside?: ComponentProps<typeof SheetContent>["onFocusOutside"];
  onInteractOutside?: ComponentProps<typeof SheetContent>["onInteractOutside"];
  onEscapeKeyDown?: ComponentProps<typeof SheetContent>["onEscapeKeyDown"];
  onOpenAutoFocus?: ComponentProps<typeof SheetContent>["onOpenAutoFocus"];
  onPointerDownOutside?: ComponentProps<typeof SheetContent>["onPointerDownOutside"];
};

export function AppCenteredSheet({
  children,
  closeLabel,
  contentClassName,
  description,
  dialogClassName,
  modal,
  onFocusOutside,
  onInteractOutside,
  onEscapeKeyDown,
  onOpenAutoFocus,
  onOpenChange,
  onPointerDownOutside,
  open,
  overlayClassName,
  title,
}: AppCenteredSheetProps) {
  const wasOpenRef = useRef(false);

  useLayoutEffect(() => {
    if (!open) {
      wasOpenRef.current = false;
      return;
    }

    if (wasOpenRef.current) {
      return;
    }

    wasOpenRef.current = true;
    // Deckwise closes some toolbars with flushSync, so notify after React has
    // finished the sheet commit instead of during this layout-effect stack.
    queueMicrotask(dispatchAppCenteredSheetWillOpen);
  }, [open]);

  return (
    <Sheet open={open} modal={modal} onOpenChange={onOpenChange}>
      <SheetContent
        side="center"
        size="lg"
        className={cn("app-centered-sheet-dialog", dialogClassName)}
        overlayClassName={cn("app-centered-sheet-overlay", overlayClassName)}
        showCloseButton={false}
        onEscapeKeyDown={onEscapeKeyDown}
        onFocusOutside={onFocusOutside}
        onInteractOutside={onInteractOutside}
        onOpenAutoFocus={onOpenAutoFocus}
        onPointerDownOutside={onPointerDownOutside}
      >
        <SheetHeader className="sr-only">
          <SheetTitle>{title}</SheetTitle>
          {description ? <SheetDescription>{description}</SheetDescription> : null}
        </SheetHeader>
        <section className={cn("app-centered-sheet", contentClassName)} aria-label={title}>
          <button
            type="button"
            className="app-centered-sheet-close-button"
            aria-label={closeLabel}
            title={closeLabel}
            onClick={() => onOpenChange(false)}
          >
            <X size={19} strokeWidth={1.9} />
          </button>
          {children}
        </section>
      </SheetContent>
    </Sheet>
  );
}
