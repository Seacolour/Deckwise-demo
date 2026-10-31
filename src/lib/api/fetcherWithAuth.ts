import { apiFetch } from './fetcher';
import {
    ensureAuthProbe,
    isAuthProbePath,
    isAuthTransitionInProgress,
    shouldBypassAuthRedirect,
    triggerAuthFailureRedirect,
} from '@/src/lib/auth/authProbe';
import { shouldTreatAsAuthError } from './fetchers/error-handler';

function isApiErrorLike(e: unknown): e is { code?: number; message?: string; payload?: unknown } {
    return typeof e === 'object' &&
           e !== null &&
           (!('code' in e) || typeof (e as { code?: unknown }).code === 'number') &&
           (typeof (e as { message?: unknown }).message === 'undefined' ||
            typeof (e as { message?: unknown }).message === 'string');
}

function getCurrentRelativeLocation(): string | null {
    if (typeof window === 'undefined' || !window.location) {
        return null;
    }

    const { pathname, search, hash } = window.location;
    if (typeof pathname !== 'string' || !pathname.startsWith('/')) {
        return null;
    }

    return `${pathname}${typeof search === 'string' ? search : ''}${typeof hash === 'string' ? hash : ''}`;
}

function getCurrentPathname(): string | null {
    if (typeof window === 'undefined' || !window.location) {
        return null;
    }

    const { pathname } = window.location;
    return typeof pathname === 'string' ? pathname : null;
}

function shouldRunAuthProbe(path: string, init?: ApiFetchInit): boolean {
    const pathname = getCurrentPathname();
    if (!pathname || shouldBypassAuthRedirect(pathname)) {
        return false;
    }

    if (init?.disableAuthHeader || isAuthProbePath(path)) {
        return false;
    }

    return true;
}

function waitForAuthRedirect<T>(): Promise<T> {
    return new Promise<never>(() => {});
}


export type ApiFetchInit = Parameters<typeof apiFetch>[1];
type ApiFetchInitValue = NonNullable<ApiFetchInit>;
export type ApiRequestOptions = RequestInit & Pick<
    ApiFetchInitValue,
    'requestPolicy' | 'timeoutMs' | 'retries' | 'retryOn' | 'disableAuthHeader'
>;

export async function apiFetchWithAuth<T>(path: string, init?: ApiFetchInit): Promise<T> {
    if (shouldRunAuthProbe(path, init)) {
        if (isAuthTransitionInProgress()) {
            return waitForAuthRedirect<T>();
        }

        const ok = await ensureAuthProbe();
        if (!ok) {
            triggerAuthFailureRedirect({
                pathname: getCurrentPathname(),
                returnTo: getCurrentRelativeLocation(),
            });
            return waitForAuthRedirect<T>();
        }
    }

    try {
        return await apiFetch<T>(path, init);
    } catch (err) {
        if (isApiErrorLike(err) && shouldTreatAsAuthError({
            code: err.code,
            message: err.message,
            payload: err.payload,
        })) {
            if (typeof window !== 'undefined' && window.location) {
                const pathname = getCurrentPathname();
                if (!pathname) {
                    throw err;
                }
                if (!shouldBypassAuthRedirect(pathname)) {
                    triggerAuthFailureRedirect({
                        pathname,
                        returnTo: getCurrentRelativeLocation(),
                    });
                    //  不再把错误抛给 React，避免红屏；等待跳转
                    return waitForAuthRedirect<T>();
                }
            }
            // SSR 环境交由上层（middleware/路由守卫）处理
        }
        throw err;
    }
}
