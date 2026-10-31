"use client";

import { memo, type ComponentProps, type ReactElement } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "../ui/dropdown-menu";
import ConfirmPanelBody from "./ConfirmPanelBody";
import {
  getMenuContentClass,
  MENU_PANEL_PADDING_PX,
} from "@/src/lib/styles/menu-styles";
import { cn } from "@/src/lib/utils";

type ConfirmDropdownProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  trigger: ReactElement;
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  variant?: "danger" | "default";
  confirmDisabled?: boolean;
  confirmLoading?: boolean;
  confirmLoadingLabel?: string;
  closeOnConfirm?: boolean;
  onConfirm: () => void;
  contentClassName?: string;
  contentProps?: Omit<ComponentProps<typeof DropdownMenuContent>, "children" | "className">;
};

const CONFIRM_DROPDOWN_SUBMENU_VISIBLE_GAP_PX = 8;
const CONFIRM_DROPDOWN_SUBMENU_ALIGN_OFFSET = -MENU_PANEL_PADDING_PX;

function ConfirmDropdown({
  open,
  onOpenChange,
  trigger,
  title,
  description,
  confirmText = "Confirm",
  cancelText = "Cancel",
  variant = "default",
  confirmDisabled = false,
  confirmLoading = false,
  confirmLoadingLabel,
  closeOnConfirm = true,
  onConfirm,
  contentClassName,
  contentProps,
}: ConfirmDropdownProps) {
  const {
    alignOffset: contentAlignOffset,
    side: contentSide,
    sideOffset: contentSideOffset,
    ...dropdownContentProps
  } = contentProps ?? {};
  const isSideSubmenu =
    contentSide === "right" || contentSide === "left";
  const resolvedAlignOffset =
    isSideSubmenu && contentAlignOffset === undefined
      ? CONFIRM_DROPDOWN_SUBMENU_ALIGN_OFFSET
      : contentAlignOffset;
  const resolvedSideOffset = isSideSubmenu
    ? MENU_PANEL_PADDING_PX +
      (contentSideOffset ?? CONFIRM_DROPDOWN_SUBMENU_VISIBLE_GAP_PX)
    : contentSideOffset;

  return (
    <DropdownMenu modal={false} open={open} onOpenChange={onOpenChange}>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent
        className={cn(
          getMenuContentClass("w-[240px]"),
          "max-w-[calc(100vw-16px)]",
          contentClassName,
        )}
        collisionPadding={8}
        sticky="always"
        onClick={(event) => {
          event.stopPropagation();
        }}
        alignOffset={resolvedAlignOffset}
        side={contentSide}
        sideOffset={resolvedSideOffset}
        {...dropdownContentProps}
      >
        {/* Reuse the shared confirm body so standard dropdown confirms and
            custom floating confirms stay visually identical. */}
        <ConfirmPanelBody
          title={title}
          description={description}
          confirmText={confirmText}
          cancelText={cancelText}
          variant={variant}
          confirmDisabled={confirmDisabled}
          confirmLoading={confirmLoading}
          confirmLoadingLabel={confirmLoadingLabel}
          onCancel={() => {
            onOpenChange(false);
          }}
          onConfirm={() => {
            onConfirm();
            if (closeOnConfirm) {
              onOpenChange(false);
            }
          }}
        />
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export default memo(ConfirmDropdown);
