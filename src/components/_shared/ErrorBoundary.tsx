"use client";

import * as React from "react";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppErrorFallback } from "@/src/components/_shared/AppErrorFallback";
import { reportErrorBoundaryError } from "@/src/lib/monitoring/reportErrorBoundaryError";
import { getErrorRecoveryHomeHref } from "@/src/lib/routing/errorRecovery";

type Props = {
  children: React.ReactNode;
  fallback?: React.ReactNode;
};

type BoundaryCopy = {
  title: string;
  message: string;
  retryLabel: string;
  homeLabel: string;
  homeHref: string;
};

type State = {
  hasError: boolean;
  error: Error | null;
};

class ErrorBoundaryInner extends React.Component<Props & BoundaryCopy, State> {
  state: State = { hasError: false, error: null };

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    // 上报错误到监控系统
    reportErrorBoundaryError(error, {
      componentStack: errorInfo.componentStack,
    });
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    if (this.props.fallback) return this.props.fallback;

    return (
      <AppErrorFallback
        error={this.state.error}
        title={this.props.title}
        message={this.props.message}
        retryLabel={this.props.retryLabel}
        homeLabel={this.props.homeLabel}
        homeHref={this.props.homeHref}
        onRetry={this.handleRetry}
      />
    );
  }
}

export function ErrorBoundary(props: Props) {
  const pathname = usePathname();
  const tApp = useTranslations("app");
  const tCommon = useTranslations("common");
  const tErrorBoundary = useTranslations("errorBoundary");

  return (
    <ErrorBoundaryInner
      {...props}
      title={tErrorBoundary("title")}
      message={tErrorBoundary("message")}
      retryLabel={tCommon("retry")}
      homeLabel={tApp("backToHome")}
      homeHref={getErrorRecoveryHomeHref(pathname)}
    />
  );
}

/**
 * 自定义错误类型
 */
export class AppError extends Error {
  code: string;
  category: 'network' | 'api' | 'validation' | 'ui' | 'storage' | 'render' | 'unknown';
  context?: Record<string, unknown>;

  constructor(
    message: string,
    code: string,
    category: 'network' | 'api' | 'validation' | 'ui' | 'storage' | 'render' | 'unknown',
    context?: Record<string, unknown>
  ) {
    super(message);
    this.code = code;
    this.category = category;
    this.context = context;
    this.name = 'AppError';
  }
}

/** API 业务错误 */
export class ApiError extends AppError {
  statusCode: number;

  constructor(
    message: string,
    code: string,
    statusCode: number = 500,
    context?: Record<string, unknown>
  ) {
    super(message, code, 'api', context);
    this.statusCode = statusCode;
    this.name = 'ApiError';
  }
}

/** 网络错误 */
export class NetworkError extends AppError {
  statusCode?: number;

  constructor(
    message: string,
    statusCode?: number,
    context?: Record<string, unknown>
  ) {
    super(message, 'NETWORK_ERROR', 'network', context);
    this.statusCode = statusCode;
    this.name = 'NetworkError';
  }
}

/** 验证错误 */
export class ValidationError extends AppError {
  field?: string;

  constructor(
    message: string,
    field?: string,
    context?: Record<string, unknown>
  ) {
    super(message, 'VALIDATION_ERROR', 'validation', context);
    this.field = field;
    this.name = 'ValidationError';
  }
}
