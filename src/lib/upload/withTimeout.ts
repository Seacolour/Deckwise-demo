export async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, timeoutMessage: string): Promise<T> {
  let tid: ReturnType<typeof setTimeout> | null = null;
  try {
    const timeoutPromise = new Promise<never>((_, reject) => {
      tid = setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
    });
    return (await Promise.race([promise, timeoutPromise])) as T;
  } finally {
    if (tid) clearTimeout(tid);
  }
}

