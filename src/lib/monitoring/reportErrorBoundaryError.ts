export function reportErrorBoundaryError(
  error: unknown,
  context?: Record<string, unknown>,
): void {
  void import("@/src/lib/monitoring/errorReporter")
    .then((mod) => {
      mod.handleErrorBoundaryError(error, context);
    })
    .catch(() => {
      // Error reporting should never trigger a second render failure.
    });
}
