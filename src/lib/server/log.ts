export function serverInfo(...args: unknown[]) {
  // eslint-disable-next-line no-console -- centralized server request tracing.
  console.info(...args);
}

export function serverDebug(enabled: boolean, ...args: unknown[]) {
  if (!enabled) {
    return;
  }
  // eslint-disable-next-line no-console -- centralized server debug tracing.
  console.info(...args);
}
