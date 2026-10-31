"use client";

import { LoadingSpinner } from "../ui/loader";
import { Button } from "../ui/button";

type ConfirmPanelBodyProps = {
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  variant?: "danger" | "default";
  confirmDisabled?: boolean;
  confirmLoading?: boolean;
  confirmLoadingLabel?: string;
  onCancel: () => void;
  onConfirm: () => void;
};

// Shared confirm body contract:
// keep copy, spacing, and button order identical whether the confirm surface is
// rendered by a Radix dropdown (`ConfirmDropdown`) or by a custom floating menu
// that needs its own positioning logic.
export default function ConfirmPanelBody({
  title,
  description,
  confirmText = "Confirm",
  cancelText = "Cancel",
  variant = "default",
  confirmDisabled = false,
  confirmLoading = false,
  confirmLoadingLabel = "Loading",
  onCancel,
  onConfirm,
}: ConfirmPanelBodyProps) {
  const confirmActionDisabled = confirmDisabled || confirmLoading;

  return (
    <>
      <div className="px-2 py-1.5">
        <div className="whitespace-normal break-words text-sm font-medium text-foreground">
          {title}
        </div>
        {description ? (
          <div className="mt-1 whitespace-normal break-words text-xs leading-5 text-muted-foreground">
            {description}
          </div>
        ) : null}
      </div>
      <div className="mt-2 flex items-center justify-end gap-2 px-2 pb-1">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={confirmLoading}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onCancel();
          }}
        >
          {cancelText}
        </Button>
        <Button
          type="button"
          variant={variant === "danger" ? "destructive" : "default"}
          size="sm"
          disabled={confirmActionDisabled}
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            onConfirm();
          }}
        >
          {confirmLoading ? (
            <LoadingSpinner size={12} label={confirmLoadingLabel} />
          ) : null}
          {confirmText}
        </Button>
      </div>
    </>
  );
}
