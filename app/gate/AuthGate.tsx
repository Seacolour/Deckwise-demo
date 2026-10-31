'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { AppRouteLoadingFallback } from '@/src/components/_shared/layouts/AppRouteLoadingFallback';
import { ensureAuthProbe, triggerAuthFailureRedirect } from '@/src/lib/auth/authProbe';
import { buildLoginRedirectPath } from '@/src/lib/auth/loginRedirect';
import { isLogoutLandingPath } from '@/src/lib/auth/logoutRedirect';
import { useAutoDailyCheckin } from '@/src/lib/checkin/useAutoDailyCheckin';
import { useSafeAppRouter } from '@/src/lib/routing/navigation';
import {
    CANVAS_PATH,
    HOME_PATH,
} from '@/src/lib/routing/projectRoutes';
import {
    SETTINGS_CENTER_SECTIONS,
    SETTINGS_ROOT_PATH,
} from '@/src/lib/routing/settingsSheetRoutes';
import {
    deriveUserKey,
    ensureEnvConsistency,
    getAuthKey,
    LOGOUT_REDIRECT_STORAGE_KEY,
    useAuthSnapshot,
    writeEnvSignature,
} from '@/src/stores/auth';
import { safeStorageOperation } from '@/src/lib/utils/errorHandler';

const PUBLIC_PATHS = new Set<string>([
    '/login',
    // '/register',
]);

const isPublicPath = (pathname: string) => {
    if (pathname === '/') {
        return true;
    }

    return [...PUBLIC_PATHS].some((pub) => pathname.startsWith(pub));
};

const shouldRedirectAuthedPublicPath = (pathname: string) =>
    pathname === '/' || pathname.startsWith('/login');

const isKnownSettingsPath = (pathname: string) => (
    pathname === SETTINGS_ROOT_PATH ||
    SETTINGS_CENTER_SECTIONS.some((section) => pathname === `${SETTINGS_ROOT_PATH}/${section}`)
);

const isKnownProtectedPath = (pathname: string) => (
    pathname === HOME_PATH ||
    pathname === CANVAS_PATH ||
    isKnownSettingsPath(pathname)
);

type Props = { children: React.ReactNode };

export default function AuthGate({ children }: Props) {
    const pathname = usePathname();
    const search = useSearchParams();
    const router = useSafeAppRouter();
    const authSnapshot = useAuthSnapshot();

    // 1) 仅在客户端“挂载”后再决定渲染（避免 SSR → CSR 水合不一致）
    const [mounted, setMounted] = useState(false);
    const [validatedAuthKey, setValidatedAuthKey] = useState<string | null>(null);
    useEffect(() => { setMounted(true); }, []);

    // 2) 计算当前是否白名单路径 & 读取 token（仅在客户端）
    const isPublic = useMemo(() => isPublicPath(pathname), [pathname]);
    const isKnownProtected = useMemo(() => isKnownProtectedPath(pathname), [pathname]);
    const redirectsAuthedPublicPath = useMemo(
        () => shouldRedirectAuthedPublicPath(pathname),
        [pathname],
    );
    const authKey = mounted ? authSnapshot.authKey : null;
    const isAuthenticated = mounted ? authSnapshot.authenticated : false;
    const userKey = mounted && isAuthenticated ? deriveUserKey(authSnapshot.rawUser) : null;
    const isProtectedAuthed = !isPublic && isKnownProtected && isAuthenticated && validatedAuthKey === authKey;
    // 2.5) 环境切换检测：若检测到环境签名变化，清空登录态并按需跳转登录
    useEffect(() => {
        if (!mounted) return;
        const changed = ensureEnvConsistency();
        if (changed) {
            // 写入最新签名，随后在受保护页引导到登录
            writeEnvSignature();
            if (!isPublic && isKnownProtected) {
                const qs = search?.toString();
                router.replace(buildLoginRedirectPath(pathname + (qs ? `?${qs}` : '')));
            }
        }
    }, [mounted, isKnownProtected, isPublic, pathname, router, search]);

    useEffect(() => {
        if (!mounted) return;
        if (isPublic || !isKnownProtected) return;
        if (isAuthenticated && validatedAuthKey === authKey) return;

        let active = true;

        const runProbe = async () => {
            const qs = search?.toString();
            const returnTo = pathname + (qs ? `?${qs}` : '');
            const ok = await ensureAuthProbe();
            if (!active) {
                return;
            }

            if (ok) {
                const nextAuthKey = getAuthKey();
                if (nextAuthKey) {
                    setValidatedAuthKey(nextAuthKey);
                    return;
                }

                // A successful transient probe without any local auth materialized
                // cannot safely unlock the app. Treat it as unauthenticated.
                triggerAuthFailureRedirect({ pathname, returnTo });
                return;
            }

            triggerAuthFailureRedirect({ pathname, returnTo });
        };

        void runProbe();

        return () => {
            active = false;
        };
    }, [authKey, isAuthenticated, mounted, isKnownProtected, isPublic, pathname, router, search, validatedAuthKey]);


    // Clear the explicit logout marker only after the public root is actually active.
    // Keeping it during the protected-page teardown prevents late auth failures from
    // racing the user-requested sign-out navigation back to `/login`.
    useEffect(() => {
        if (!mounted) return;
        if (!isLogoutLandingPath(pathname) || isAuthenticated) return;

        safeStorageOperation(() => {
            sessionStorage.removeItem(LOGOUT_REDIRECT_STORAGE_KEY);
        }, 'sessionStorage', 'removeItem');
    }, [isAuthenticated, mounted, pathname]);

    // 3.5) 若在登录页或公开落地页且已登录，则直接进入 app。
    useEffect(() => {
        if (!mounted) return;
        if (!redirectsAuthedPublicPath) return;
        if (!isAuthenticated) return;

        let active = true;
        void ensureAuthProbe().then((ok) => {
            if (!active) {
                return;
            }

            if (!ok) {
                triggerAuthFailureRedirect({ pathname });
                return;
            }

            router.replace(HOME_PATH);
        });

        return () => {
            active = false;
        };
    }, [isAuthenticated, mounted, pathname, redirectsAuthedPublicPath, router]);

    useAutoDailyCheckin({
        enabled: mounted && isProtectedAuthed,
        userKey,
    });

    // 4) Public pages must SSR real content for SEO. Protected pages still wait
    // for the client auth snapshot before rendering app data.
    if (!mounted) return isPublic || !isKnownProtected ? <>{children}</> : null;
    if (!isPublic && !isKnownProtected) return <>{children}</>;
    if (!isPublic && !isAuthenticated) return null;
    if (!isPublic && isAuthenticated && validatedAuthKey !== authKey) {
        return <AppRouteLoadingFallback />;
    }
    if (redirectsAuthedPublicPath && isAuthenticated) return null;

    // 5) 其它情况正常渲染
    return <>{children}</>;
}
