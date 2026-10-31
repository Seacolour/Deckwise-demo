"use client";

import { useSyncExternalStore } from "react";
import { CONFIG } from '@/src/lib/config/env';
import { getString, setString, getJSON, setJSON, remove } from '@/src/lib/storage/localStorageManager';
import { isObject } from "@/src/lib/types/guards";
import { clearDerivedCachePartitionsForUsers, clearLegacyDerivedCaches } from "@/src/stores/cacheCleanup";
import { clearProjectImageUploads } from "@/src/lib/images/projectImageUploadStore";
import { clearImageCache } from "@/src/lib/images/warmImageCache";
import { clearSharedReadState } from "@/src/lib/api/sharedReads";
import { clearPdfBufferCache } from "@/src/lib/pdf/pdfBinaryCache";
import { clearDesktopRecovery } from "@/src/lib/desktop/desktopStorage";
import { clearRecentNoteContent } from "@/src/stores/recentNoteContent";
import { clearRecentFilePreview } from "@/src/stores/recentFilePreview";
import { clearLocalNotesMemory } from "@/src/stores/localNotes";
import { clearLocalProjectFilesMemory } from "@/src/stores/localProjectFiles";
import { ossImageStorage } from "@/src/stores/persist/indexeddb/oss/ossImageStorage";
import { LocalStorageKeys, SessionStorageKeys } from '@/src/lib/config/constants/storage';
import {
    deriveUserKey,
    resolveCurrentUser,
    type AuthUser,
} from "@/src/stores/authUserKey";

export { deriveUserKey, getUserKey } from "@/src/stores/authUserKey";
export type { AuthUser } from "@/src/stores/authUserKey";

export const TOKEN_STORAGE_KEY = LocalStorageKeys.TOKEN;
export const USER_STORAGE_KEY = LocalStorageKeys.USER;
export const LOGOUT_REDIRECT_STORAGE_KEY = "auth:logout-redirect";
const ENV_SIGNATURE_STORAGE_KEY = LocalStorageKeys.ENV_SIGNATURE;
const AUTH_CHANGE_EVENT = "deckwise:auth-change";

/**
 * SECURITY NOTE:
 * - Web auth credentials are carried by httpOnly cookies set by the server.
 * - localStorage stores only the current user snapshot for UI rendering.
 */

export function extractToken(user: unknown): string | null {
    if (!isObject(user)) return null;
    const maybe = user as { userToken?: unknown; appAccountToken?: unknown };
    const token = (maybe.userToken ?? maybe.appAccountToken) as unknown;
    return typeof token === 'string' && token.length > 0 ? token : (token != null ? String(token) : null);
}

export type AuthSnapshot = {
    authenticated: boolean;
    authKey: string | null;
    token: string | null;
    rawUser: AuthUser | null;
};

const ANONYMOUS_AUTH_SNAPSHOT: AuthSnapshot = {
    authenticated: false,
    authKey: null,
    token: null,
    rawUser: null,
};

let cachedAuthSnapshot: AuthSnapshot = ANONYMOUS_AUTH_SNAPSHOT;
let cachedAuthSnapshotToken: string | null = null;
let cachedAuthSnapshotUserRaw: string | null = null;

function readServerAuthSnapshot(): AuthSnapshot {
    // SSR and hydration must agree; browser-only localStorage auth is applied after hydration.
    return ANONYMOUS_AUTH_SNAPSHOT;
}

function readAuthSnapshot(): AuthSnapshot {
    const token = null;
    const rawUserSerialized = getString(USER_STORAGE_KEY);

    if (
        token === cachedAuthSnapshotToken &&
        rawUserSerialized === cachedAuthSnapshotUserRaw
    ) {
        return cachedAuthSnapshot;
    }

    let rawUser: AuthUser | null = null;
    if (rawUserSerialized) {
        try {
            rawUser = JSON.parse(rawUserSerialized) as AuthUser;
        } catch {
            rawUser = null;
        }
    }

    cachedAuthSnapshotToken = token;
    cachedAuthSnapshotUserRaw = rawUserSerialized;
    cachedAuthSnapshot = {
        authenticated: Boolean(token || rawUser),
        authKey: token ? `bearer:${token}` : (rawUser ? `cookie:${deriveUserKey(rawUser)}` : null),
        token,
        rawUser,
    };

    return cachedAuthSnapshot;
}

function emitAuthChange(): void {
    if (typeof window === "undefined") return;
    window.dispatchEvent(new Event(AUTH_CHANGE_EVENT));
}

export function subscribeAuth(listener: () => void): () => void {
    if (typeof window === "undefined") {
        return () => {};
    }

    const handleAuthChange = () => {
        listener();
    };
    const handleStorageChange = (event: StorageEvent) => {
        if (event.storageArea !== window.localStorage) {
            return;
        }

        if (event.key && event.key !== TOKEN_STORAGE_KEY && event.key !== USER_STORAGE_KEY) {
            return;
        }

        listener();
    };

    window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange);
    window.addEventListener("storage", handleStorageChange);

    return () => {
        window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange);
        window.removeEventListener("storage", handleStorageChange);
    };
}

export function useAuthSnapshot(): AuthSnapshot {
    return useSyncExternalStore(subscribeAuth, readAuthSnapshot, readServerAuthSnapshot);
}

export function saveAuth(user: AuthUser): void {
    remove(TOKEN_STORAGE_KEY);
    setJSON(USER_STORAGE_KEY, stripAuthTokens(user));
    emitAuthChange();
}

export function saveSessionAuth(user: AuthUser): void {
    remove(TOKEN_STORAGE_KEY);
    setJSON(USER_STORAGE_KEY, stripAuthTokens(user));
    emitAuthChange();
}

export function getToken(): string | null {
    return null;
}

export function getAuthKey(): string | null {
    return readAuthSnapshot().authKey;
}

export function getUser<T = AuthUser | Record<string, unknown> | null>(): T | null {
    return getJSON<T>(USER_STORAGE_KEY);
}

export function getCurrentUser<T extends object = Record<string, unknown>>(): T | null {
    const raw = getJSON<AuthUser>(USER_STORAGE_KEY);
    return resolveCurrentUser(raw) as T | null;
}

function clearSessionAuthState(): void {
    if (typeof window === "undefined") return;
    try {
        // Auth switch guard:
        // clear session-scoped UI state so a previous account's active session/project context
        // does not leak into the next account after logout/login in the same browser profile.
        const fixedKeys = [
            SessionStorageKeys.CHAT_CURRENT_SID,
            "returnTo",
        ];
        for (const key of fixedKeys) {
            sessionStorage.removeItem(key);
        }

        // Include project-scoped chat sid keys in logout cleanup so account switch never reuses old session state.
        const prefixedKeys = [
            "session:",
            "chat:currentSid:",
        ];
        for (let i = sessionStorage.length - 1; i >= 0; i -= 1) {
            const key = sessionStorage.key(i);
            if (!key) continue;
            if (prefixedKeys.some((prefix) => key.startsWith(prefix))) {
                sessionStorage.removeItem(key);
            }
        }
    } catch {
        // best effort
    }
}

function clearPersistentAuthState(): void {
    if (typeof window === "undefined") return;
    try {
        for (let i = localStorage.length - 1; i >= 0; i -= 1) {
            const key = localStorage.key(i);
            if (key?.startsWith(LocalStorageKeys.CHAT_INPUT_DRAFT_PREFIX)) {
                localStorage.removeItem(key);
            }
        }
    } catch {
        // best effort
    }
}

export function clearAuth(): void {
    const activeUserKey = deriveUserKey(readAuthSnapshot().rawUser);
    const userKeysToClear = new Set<string>([activeUserKey, "anon"]);

    for (const userKey of userKeysToClear) {
        void clearDesktopRecovery({ userKey });
    }
    void clearDerivedCachePartitionsForUsers(userKeysToClear);
    void Promise.resolve(clearLegacyDerivedCaches()).catch(() => {
        // Logout cleanup remains best effort when IndexedDB is unavailable.
    });
    void ossImageStorage.clearUserPartitions(userKeysToClear).catch(() => {
        // best effort
    });

    remove(TOKEN_STORAGE_KEY);
    remove(USER_STORAGE_KEY);
    clearSessionAuthState();
    clearPersistentAuthState();
    clearProjectImageUploads();
    clearImageCache();
    clearSharedReadState();
    clearPdfBufferCache();
    clearRecentNoteContent();
    clearRecentFilePreview();
    clearLocalNotesMemory();
    clearLocalProjectFilesMemory();
    emitAuthChange();
}

export function isLoggedIn(): boolean {
    return readAuthSnapshot().authenticated;
}

function stripAuthTokens<T>(value: T): T {
    if (Array.isArray(value)) {
        return value.map((item) => stripAuthTokens(item)) as T;
    }

    if (!isObject(value)) {
        return value;
    }

    const output: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value)) {
        if (key === "userToken" || key === "appAccountToken") {
            continue;
        }
        output[key] = stripAuthTokens(item);
    }
    return output as T;
}

function computeEnvSignature(): string {
    // 使用公开签名，避免把真实后端地址（server-only）暴露给客户端 bundle
    return CONFIG.ENV_SIGNATURE;
}

export function ensureEnvConsistency(): boolean {
    const current = computeEnvSignature();
    const stored = getString(ENV_SIGNATURE_STORAGE_KEY);
    if (stored !== current) {
        // 环境切换：清理登录态，并写入最新签名
        clearAuth();
        setString(ENV_SIGNATURE_STORAGE_KEY, current);
        return true;
    }
    return false;
}

export function writeEnvSignature(): void {
    setString(ENV_SIGNATURE_STORAGE_KEY, computeEnvSignature());
}
