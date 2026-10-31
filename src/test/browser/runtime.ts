type MemoryStorageSeed = Record<string, string | number | boolean | object | null | undefined>;

class MemoryStorage implements Storage {
  private readonly store = new Map<string, string>();

  constructor(seed?: MemoryStorageSeed) {
    for (const [key, value] of Object.entries(seed ?? {})) {
      if (value === undefined) {
        continue;
      }
      this.store.set(
        key,
        typeof value === "string" ? value : JSON.stringify(value),
      );
    }
  }

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

function restoreGlobalProperty(
  key: "window" | "location" | "localStorage" | "sessionStorage",
  descriptor: PropertyDescriptor | undefined,
) {
  if (descriptor) {
    Object.defineProperty(globalThis, key, descriptor);
    return;
  }

  delete (globalThis as Record<string, unknown>)[key];
}

export function installBrowserRuntime(seed?: {
  localStorage?: MemoryStorageSeed;
  sessionStorage?: MemoryStorageSeed;
}) {
  const windowDescriptor = Object.getOwnPropertyDescriptor(globalThis, "window");
  const locationDescriptor = Object.getOwnPropertyDescriptor(globalThis, "location");
  const localStorageDescriptor = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const sessionStorageDescriptor = Object.getOwnPropertyDescriptor(globalThis, "sessionStorage");

  const localStorage = new MemoryStorage(seed?.localStorage);
  const sessionStorage = new MemoryStorage(seed?.sessionStorage);

  Object.defineProperty(globalThis, "window", {
    configurable: true,
    writable: true,
    value: globalThis,
  });
  Object.defineProperty(globalThis, "location", {
    configurable: true,
    writable: true,
    value: new URL("http://localhost/"),
  });
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    writable: true,
    value: localStorage,
  });
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    writable: true,
    value: sessionStorage,
  });

  return {
    localStorage,
    sessionStorage,
    restore() {
      restoreGlobalProperty("window", windowDescriptor);
      restoreGlobalProperty("location", locationDescriptor);
      restoreGlobalProperty("localStorage", localStorageDescriptor);
      restoreGlobalProperty("sessionStorage", sessionStorageDescriptor);
    },
  };
}
