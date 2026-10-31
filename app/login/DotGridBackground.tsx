"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { cn } from "@/src/lib/utils";

const DOT_SPACING = 16;
const DOT_RADIUS = 1.1;
const WAVE_COUNT = 3;
const WAVE_ACTIVE_START = 0.08;
const WAVE_ACTIVE_END = 0.56;
const WAVE_FADE_IN_PORTION = 0.18;
const WAVE_FADE_OUT_PORTION = 0.24;
const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const FALLBACK_DOT_RGB = "120, 113, 108";
const FALLBACK_SWEEP_RGB = "28, 25, 23";
const FALLBACK_BACKGROUND_RGB = "25, 25, 25";
const LIGHT_BASE_ALPHA = 0.12;
const DARK_BASE_ALPHA = 0.05;
const LIGHT_SWEEP_ALPHA_MAX = 0.08;
const DARK_SWEEP_ALPHA_MAX = 0.05;

type Size = {
  width: number;
  height: number;
};

type WaveSeed = {
  angle: number;
  speed: number;
  phase: number;
  bandWidth: number;
  strength: number;
};

function createWaveSeeds(): WaveSeed[] {
  return Array.from({ length: WAVE_COUNT }, (_, index) => ({
    angle: Math.PI / 4 + (-0.18 + Math.random() * 0.36),
    speed: 0.000025 + Math.random() * 0.000015,
    phase: index * 0.23 + Math.random() * 0.12,
    bandWidth: 128 + Math.random() * 48,
    strength: 0.078 + Math.random() * 0.03,
  }));
}

function readRgbCssVar(variableName: string, fallback: string) {
  if (typeof window === "undefined") {
    return fallback;
  }

  const value = window.getComputedStyle(document.documentElement)
    .getPropertyValue(variableName)
    .trim();

  return /^\d+\s*,\s*\d+\s*,\s*\d+$/.test(value) ? value : fallback;
}

function clamp01(value: number) {
  return Math.min(1, Math.max(0, value));
}

function smoothstep01(value: number) {
  const clamped = clamp01(value);
  return clamped * clamped * (3 - 2 * clamped);
}

export default function DotGridBackground({ className }: { className?: string }) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const staticCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const frameRef = useRef<number | null>(null);
  const startTimeRef = useRef<number | null>(null);
  const elapsedRef = useRef(0);
  const sizeRef = useRef<Size>({ width: 0, height: 0 });
  const reducedMotionRef = useRef(false);
  const isVisibleRef = useRef(true);
  const dotRgbRef = useRef(FALLBACK_DOT_RGB);
  const sweepRgbRef = useRef(FALLBACK_SWEEP_RGB);
  const baseAlphaRef = useRef(LIGHT_BASE_ALPHA);
  const sweepAlphaMaxRef = useRef(LIGHT_SWEEP_ALPHA_MAX);
  const waveSeedsRef = useRef<WaveSeed[]>(createWaveSeeds());
  const [size, setSize] = useState<Size>({ width: 0, height: 0 });

  const stopAnimation = useCallback(() => {
    if (frameRef.current !== null) {
      window.cancelAnimationFrame(frameRef.current);
      frameRef.current = null;
    }
  }, []);

  const measure = useCallback(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }

    const nextSize = {
      width: Math.round(container.clientWidth),
      height: Math.round(container.clientHeight),
    };
    sizeRef.current = nextSize;

    setSize((prev) => (
      prev.width === nextSize.width && prev.height === nextSize.height
        ? prev
        : nextSize
    ));
  }, []);

  const syncThemeValues = useCallback(() => {
    const root = document.documentElement;
    const isDark = root.dataset.themeResolved === "dark" || root.style.colorScheme === "dark";
    dotRgbRef.current = readRgbCssVar("--muted-foreground", FALLBACK_DOT_RGB);
    sweepRgbRef.current = isDark
      ? readRgbCssVar("--background", FALLBACK_BACKGROUND_RGB)
      : readRgbCssVar("--foreground", FALLBACK_SWEEP_RGB);
    baseAlphaRef.current = isDark ? DARK_BASE_ALPHA : LIGHT_BASE_ALPHA;
    sweepAlphaMaxRef.current = isDark ? DARK_SWEEP_ALPHA_MAX : LIGHT_SWEEP_ALPHA_MAX;
  }, []);

  const renderStaticLayer = useCallback(() => {
    const { width, height } = sizeRef.current;
    if (!width || !height || typeof document === "undefined") {
      return;
    }

    let staticCanvas = staticCanvasRef.current;
    if (!staticCanvas) {
      staticCanvas = document.createElement("canvas");
      staticCanvasRef.current = staticCanvas;
    }

    const context = staticCanvas.getContext("2d");
    if (!context) {
      return;
    }

    const dpr = Math.max(window.devicePixelRatio || 1, 1);
    const nextCanvasWidth = Math.max(1, Math.round(width * dpr));
    const nextCanvasHeight = Math.max(1, Math.round(height * dpr));

    if (staticCanvas.width !== nextCanvasWidth || staticCanvas.height !== nextCanvasHeight) {
      staticCanvas.width = nextCanvasWidth;
      staticCanvas.height = nextCanvasHeight;
    }

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, staticCanvas.width, staticCanvas.height);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);

    const cols = Math.ceil(width / DOT_SPACING) + 1;
    const rows = Math.ceil(height / DOT_SPACING) + 1;
    const baseAlpha = Math.min(baseAlphaRef.current, 0.19);

    for (let row = 0; row < rows; row += 1) {
      for (let col = 0; col < cols; col += 1) {
        const x = col * DOT_SPACING + DOT_SPACING / 2;
        const y = row * DOT_SPACING + DOT_SPACING / 2;

        context.beginPath();
        context.arc(x, y, DOT_RADIUS, 0, Math.PI * 2);
        context.fillStyle = `rgba(${dotRgbRef.current}, ${baseAlpha})`;
        context.fill();
      }
    }
  }, []);

  const drawFrame = useCallback(function runFrame(timestamp: number) {
    frameRef.current = null;

    const canvas = canvasRef.current;
    const { width, height } = sizeRef.current;

    if (!canvas || !width || !height) {
      return;
    }

    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }

    if (startTimeRef.current === null) {
      startTimeRef.current = timestamp - elapsedRef.current;
    }

    const elapsed = timestamp - startTimeRef.current;
    elapsedRef.current = elapsed;

    const dpr = Math.max(window.devicePixelRatio || 1, 1);
    const nextCanvasWidth = Math.max(1, Math.round(width * dpr));
    const nextCanvasHeight = Math.max(1, Math.round(height * dpr));

    if (canvas.width !== nextCanvasWidth || canvas.height !== nextCanvasHeight) {
      canvas.width = nextCanvasWidth;
      canvas.height = nextCanvasHeight;
    }

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (staticCanvasRef.current) {
      context.drawImage(staticCanvasRef.current, 0, 0);
    }
    context.setTransform(dpr, 0, 0, dpr, 0, 0);

    const activeSweeps = waveSeedsRef.current.flatMap((wave) => {
      const cycleProgress = (elapsed * wave.speed + wave.phase) % 1;
      if (cycleProgress < WAVE_ACTIVE_START || cycleProgress > WAVE_ACTIVE_END) {
        return [];
      }

      const activeProgress =
        (cycleProgress - WAVE_ACTIVE_START) / (WAVE_ACTIVE_END - WAVE_ACTIVE_START);
      const fadeIn = smoothstep01(activeProgress / WAVE_FADE_IN_PORTION);
      const fadeOut = smoothstep01((1 - activeProgress) / WAVE_FADE_OUT_PORTION);
      const dirX = Math.cos(wave.angle);
      const dirY = Math.sin(wave.angle);
      const sweepSpan = [
        0,
        width * dirX,
        height * dirY,
        width * dirX + height * dirY,
      ];
      const minProjection = Math.min(...sweepSpan) - wave.bandWidth * 1.5;
      const maxProjection = Math.max(...sweepSpan) + wave.bandWidth * 1.5;

      return [{
        dirX,
        dirY,
        bandWidth: wave.bandWidth,
        strength: wave.strength * 1.7,
        envelope: fadeIn * fadeOut,
        sweepCenter: minProjection + activeProgress * (maxProjection - minProjection),
      }];
    });

    if (activeSweeps.length === 0) {
      if (!reducedMotionRef.current && isVisibleRef.current) {
        frameRef.current = window.requestAnimationFrame(runFrame);
      }
      return;
    }

    const cols = Math.ceil(width / DOT_SPACING) + 1;
    const rows = Math.ceil(height / DOT_SPACING) + 1;
    for (let row = 0; row < rows; row += 1) {
      for (let col = 0; col < cols; col += 1) {
        const x = col * DOT_SPACING + DOT_SPACING / 2;
        const y = row * DOT_SPACING + DOT_SPACING / 2;

        let sweepAlpha = 0;

        for (const sweep of activeSweeps) {
          const projection = x * sweep.dirX + y * sweep.dirY;
          const distanceFromWave = Math.abs(projection - sweep.sweepCenter);
          if (distanceFromWave >= sweep.bandWidth) {
            continue;
          }

          const sweepIntensity = 1 - distanceFromWave / sweep.bandWidth;
          const easedSweep = sweepIntensity * sweepIntensity * (3 - 2 * sweepIntensity);
          sweepAlpha += easedSweep * sweep.strength * sweep.envelope;
        }

        sweepAlpha = Math.min(sweepAlpha, sweepAlphaMaxRef.current);

        if (sweepAlpha <= 0.001) {
          continue;
        }

        context.beginPath();
        context.arc(x, y, DOT_RADIUS, 0, Math.PI * 2);
        context.fillStyle = `rgba(${sweepRgbRef.current}, ${sweepAlpha})`;
        context.fill();
      }
    }

    if (!reducedMotionRef.current && isVisibleRef.current) {
      frameRef.current = window.requestAnimationFrame(runFrame);
    }
  }, []);

  const requestRender = useCallback(() => {
    const { width, height } = sizeRef.current;
    if (!width || !height) {
      return;
    }

    if (reducedMotionRef.current) {
      drawFrame(window.performance.now());
      return;
    }

    if (!isVisibleRef.current || frameRef.current !== null) {
      return;
    }

    frameRef.current = window.requestAnimationFrame(drawFrame);
  }, [drawFrame]);

  useEffect(() => {
    sizeRef.current = size;
  }, [size]);

  useEffect(() => {
    measure();
    syncThemeValues();
    renderStaticLayer();

    const container = containerRef.current;
    if (!container) {
      return;
    }

    if (typeof ResizeObserver === "undefined") {
      const handleResize = () => {
        measure();
        renderStaticLayer();
        requestRender();
      };
      window.addEventListener("resize", handleResize);
      return () => {
        window.removeEventListener("resize", handleResize);
      };
    }

    const observer = new ResizeObserver(() => {
      measure();
      renderStaticLayer();
      requestRender();
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
    };
  }, [measure, renderStaticLayer, requestRender, syncThemeValues]);

  useEffect(() => {
    syncThemeValues();
    renderStaticLayer();

    const root = document.documentElement;
    const observer = new MutationObserver(() => {
      syncThemeValues();
      renderStaticLayer();
      requestRender();
    });

    observer.observe(root, {
      attributes: true,
      attributeFilter: ["class", "style", "data-theme", "data-theme-resolved"],
    });

    return () => {
      observer.disconnect();
    };
  }, [renderStaticLayer, requestRender, syncThemeValues]);

  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      requestRender();
      return;
    }

    const mediaQuery = window.matchMedia(REDUCED_MOTION_QUERY);
    const syncReducedMotion = () => {
      reducedMotionRef.current = mediaQuery.matches;
      stopAnimation();
      requestRender();
    };

    syncReducedMotion();

    if (typeof mediaQuery.addEventListener === "function") {
      mediaQuery.addEventListener("change", syncReducedMotion);
      return () => {
        mediaQuery.removeEventListener("change", syncReducedMotion);
      };
    }

    mediaQuery.addListener(syncReducedMotion);
    return () => {
      mediaQuery.removeListener(syncReducedMotion);
    };
  }, [requestRender, stopAnimation]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      isVisibleRef.current = !document.hidden;

      if (document.hidden) {
        stopAnimation();
        return;
      }

      startTimeRef.current = window.performance.now() - elapsedRef.current;
      requestRender();
    };

    isVisibleRef.current = !document.hidden;
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [requestRender, stopAnimation]);

  useEffect(() => {
    requestRender();

    return () => {
      stopAnimation();
    };
  }, [requestRender, size.height, size.width, stopAnimation]);

  return (
    <div
      ref={containerRef}
      aria-hidden="true"
      className={cn("pointer-events-none absolute inset-0 overflow-hidden", className)}
    >
      <canvas ref={canvasRef} className="absolute inset-0 size-full" />
    </div>
  );
}
