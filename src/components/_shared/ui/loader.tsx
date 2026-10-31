import React from "react";
import { cn } from "@/src/lib/utils";

export function LoadingSpinner({
  size,
  className,
  label = "Loading",
}: {
  size?: number;
  className?: string;
  label?: string;
}) {
  return (
    <div
      role="status"
      aria-label={label}
      className={cn(
        "animate-spin shrink-0 rounded-full border-2",
        className
      )}
      style={{
        width: size ?? "var(--spinner-size)",
        height: size ?? "var(--spinner-size)",
        // Theme tokens are stored as raw RGB triplets, so compose the final
        // colors here instead of relying on border-* utilities.
        borderColor: "rgba(var(--spinner-track-rgb), var(--spinner-track-opacity))",
        borderTopColor: "rgb(var(--spinner-head-rgb))",
      }}
    />
  );
}

export function CenteredLoader({
  className,
  size,
  label = "Loading",
}: {
  className?: string;
  size?: number;
  label?: string;
}) {
  return (
    <div className={cn("w-full h-full min-h-[240px] flex items-center justify-center", className)}>
      <LoadingSpinner size={size} label={label} />
      <span className="sr-only">{label}</span>
    </div>
  );
}
