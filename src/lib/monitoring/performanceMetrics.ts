"use client";

import { debugFlagLog } from "@/src/lib/debug";
import { stripLocalePrefix } from "@/src/lib/routing/localePrefix";

type MetricAttribute = boolean | number | string;

type PerformanceMetricOptions = {
  attributes?: Record<string, MetricAttribute | null | undefined>;
  unit?: string;
};

type NetworkInformationLike = {
  downlink?: number;
  effectiveType?: string;
  rtt?: number;
  saveData?: boolean;
};

export type ClientPerformanceContextInput = {
  connection?: NetworkInformationLike;
  deviceMemory?: number;
  devicePixelRatio?: number;
  hardwareConcurrency?: number;
  maxTouchPoints?: number;
  viewportHeight?: number;
  viewportWidth?: number;
};

const UUID_SEGMENT = /^[0-9a-f]{8}-[0-9a-f-]{27,}$/iu;
const NUMERIC_SEGMENT = /^\d+$/u;
const OPAQUE_ID_SEGMENT = /^[A-Za-z0-9_-]{24,}$/u;

export function normalizePerformanceRoute(pathname: string): string {
  const normalized = stripLocalePrefix(pathname)
    .split("/")
    .map((segment) => (
      UUID_SEGMENT.test(segment) || NUMERIC_SEGMENT.test(segment) || OPAQUE_ID_SEGMENT.test(segment)
        ? ":id"
        : segment
    ))
    .join("/");
  return normalized || "/";
}

function currentRoute(): string {
  return typeof window === "undefined"
    ? "server"
    : normalizePerformanceRoute(window.location.pathname);
}

function numericBucket(
  value: number | undefined,
  buckets: ReadonlyArray<{ label: string; max: number }>,
  overflow: string,
): string {
  if (value === undefined || !Number.isFinite(value) || value < 0) return "unknown";
  return buckets.find((bucket) => value <= bucket.max)?.label ?? overflow;
}

function networkEffectiveType(value: string | undefined): string {
  return value === "slow-2g" || value === "2g" || value === "3g" || value === "4g"
    ? value
    : "unknown";
}

export function resolveClientPerformanceAttributes(
  input: ClientPerformanceContextInput,
): Record<string, MetricAttribute> {
  const width = input.viewportWidth;
  const viewportBucket = numericBucket(width, [
    { label: "xs", max: 479 },
    { label: "sm", max: 767 },
    { label: "md", max: 1023 },
    { label: "lg", max: 1439 },
  ], "xl");
  const deviceClass = width === undefined || !Number.isFinite(width)
    ? "unknown"
    : width < 768
      ? "mobile"
      : width < 1024 || (input.maxTouchPoints ?? 0) > 1 && width < 1366
        ? "tablet"
        : "desktop";

  return compactAttributes({
    deviceClass,
    deviceMemoryBucket: numericBucket(input.deviceMemory, [
      { label: "0-2gb", max: 2 },
      { label: "3-4gb", max: 4 },
      { label: "5-8gb", max: 8 },
    ], "9gb+"),
    devicePixelRatioBucket: numericBucket(input.devicePixelRatio, [
      { label: "1x", max: 1.25 },
      { label: "2x", max: 2.25 },
    ], "3x+"),
    hardwareConcurrencyBucket: numericBucket(input.hardwareConcurrency, [
      { label: "1-2", max: 2 },
      { label: "3-4", max: 4 },
      { label: "5-8", max: 8 },
    ], "9+"),
    networkDownlinkBucket: numericBucket(input.connection?.downlink, [
      { label: "<1mbps", max: 0.99 },
      { label: "1-4mbps", max: 4.99 },
      { label: "5-9mbps", max: 9.99 },
    ], "10mbps+"),
    networkEffectiveType: networkEffectiveType(input.connection?.effectiveType),
    networkRttBucket: numericBucket(input.connection?.rtt, [
      { label: "<100ms", max: 99 },
      { label: "100-299ms", max: 299 },
      { label: "300-999ms", max: 999 },
    ], "1000ms+"),
    networkSaveData: input.connection?.saveData,
    viewportBucket,
    viewportHeightBucket: numericBucket(input.viewportHeight, [
      { label: "short", max: 599 },
      { label: "medium", max: 899 },
      { label: "tall", max: 1199 },
    ], "very-tall"),
  });
}

function currentClientPerformanceAttributes(): Record<string, MetricAttribute> {
  if (typeof window === "undefined" || typeof navigator === "undefined") return {};
  const clientNavigator = navigator as Navigator & {
    connection?: NetworkInformationLike;
    deviceMemory?: number;
  };
  return resolveClientPerformanceAttributes({
    connection: clientNavigator.connection,
    deviceMemory: clientNavigator.deviceMemory,
    devicePixelRatio: window.devicePixelRatio,
    hardwareConcurrency: navigator.hardwareConcurrency,
    maxTouchPoints: navigator.maxTouchPoints,
    viewportHeight: window.innerHeight,
    viewportWidth: window.innerWidth,
  });
}

function compactAttributes(
  attributes: Record<string, MetricAttribute | null | undefined>,
): Record<string, MetricAttribute> {
  return Object.fromEntries(
    Object.entries(attributes).filter((entry): entry is [string, MetricAttribute] => (
      entry[1] !== null && entry[1] !== undefined
    )),
  );
}

export function reportPerformanceDistribution(
  name: string,
  value: number,
  options: PerformanceMetricOptions = {},
): boolean {
  if (!Number.isFinite(value)) {
    return false;
  }

  debugFlagLog(true, `[Performance] ${name}: ${value} ${options.unit ?? ""}`.trim(),
    compactAttributes({
      route: currentRoute(),
      ...currentClientPerformanceAttributes(),
      ...options.attributes,
    }));
  return true;
}

export function reportBusinessOperationDuration(
  operation:
    | "deck_generation"
    | "document_save"
    | "file_upload"
    | "image_export"
    | "pdf_export"
    | "pptx_export",
  durationMs: number,
  attributes: Record<string, MetricAttribute | null | undefined> = {},
): boolean {
  return reportPerformanceDistribution("deckwise.business_operation.duration", durationMs, {
    unit: "millisecond",
    attributes: {
      operation,
      ...attributes,
    },
  });
}
