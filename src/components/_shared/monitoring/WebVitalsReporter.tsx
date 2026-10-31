"use client";

import { useReportWebVitals } from "next/web-vitals";
import { reportPerformanceDistribution } from "@/src/lib/monitoring/performanceMetrics";
import { useProductPerformanceSignals } from "./useProductPerformanceSignals";

export function WebVitalsReporter() {
  useProductPerformanceSignals();
  useReportWebVitals((metric) => {
    reportPerformanceDistribution(
      `deckwise.web_vital.${metric.name.toLowerCase()}`,
      metric.value,
      {
        unit: metric.name === "CLS" ? "none" : "millisecond",
        attributes: {
          navigationType: metric.navigationType,
          rating: metric.rating,
        },
      },
    );
  });

  return null;
}
