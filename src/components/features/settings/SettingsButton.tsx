"use client";

import type { ComponentProps } from "react";
import { cn } from "@/src/lib/utils";

type SettingsButtonVariant = "outline" | "primary" | "danger" | "ghostIcon" | "toggle";
type SettingsButtonSize = "sm" | "compact" | "default" | "icon";

const variantClass: Record<SettingsButtonVariant, string> = {
  outline: "!border !border-solid !border-border bg-transparent text-foreground hover:bg-accent/70 hover:text-foreground",
  primary: "border border-transparent bg-primary text-primary-foreground hover:bg-primary/85",
  danger: "!border !border-solid !border-destructive/30 bg-transparent text-destructive hover:bg-destructive/10",
  ghostIcon: "border border-transparent bg-transparent text-muted-foreground hover:bg-accent/70 hover:text-foreground",
  toggle: "border border-transparent bg-accent text-foreground hover:bg-accent/80 data-[state=off]:bg-muted data-[state=off]:text-muted-foreground",
};

const sizeClass: Record<SettingsButtonSize, string> = {
  sm: "h-8 px-3 text-[13px]",
  compact: "h-8 px-4 text-[14px]",
  default: "h-9 px-4 text-[14px]",
  icon: "size-8 p-0",
};

type SettingsButtonProps = ComponentProps<"button"> & {
  size?: SettingsButtonSize;
  variant?: SettingsButtonVariant;
};

export function SettingsButton({
  className,
  ref,
  size = "default",
  type = "button",
  variant = "outline",
  ...props
}: SettingsButtonProps) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        "inline-flex shrink-0 items-center justify-center gap-2 rounded-[8px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60",
        variantClass[variant],
        sizeClass[size],
        className,
      )}
      {...props}
    />
  );
}
