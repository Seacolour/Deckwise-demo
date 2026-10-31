// 让 orval 生成的所有请求都走带鉴权拦截的 fetcher（40102/401 自动跳 /login）
import { apiFetchWithAuth } from '@/src/lib/api/fetcherWithAuth';

/** URL 可能是绝对的，转成路径（+查询）以便走 Next 的 /api 代理或统一网关 */
function toPathOnly(u: string): string {
    try {
        const p = new URL(u);
        return p.pathname + p.search;
    } catch {
        return u;
    }
}

/** 允许作为 body 的类型（与底层 fetcher 的自动 JSON 逻辑兼容） */
type AllowedBody = Record<string, unknown> | BodyInit | null | undefined;

/** Query 参数的原子类型与集合 */
type QueryPrimitive = string | number | boolean | null | undefined;
type QueryValue = QueryPrimitive | ReadonlyArray<QueryPrimitive>;

/** 供底层 apiFetch 使用的 init（与 /lib/api/fetcher.ts 兼容的子集） */
interface ApiFetchInit extends Omit<RequestInit, 'body' | 'headers'> {
    body?: AllowedBody;
    headers?: HeadersInit;
    params?: Record<string, string | number | boolean | null | undefined>;
    requestPolicy?: "query" | "mutation" | "upload";
    timeoutMs?: number;
    retries?: number;
    retryOn?: number[];
    disableAuthHeader?: boolean;
    raw?: true;
}

/** 兼容 orval 的旧式调用签名（配置对象形式） */
export type OrvalConfig<
    TVars extends AllowedBody = undefined,
    TParams extends Record<string, QueryValue> = Record<string, QueryValue>
> = {
    url: string;
    method?: string;
    params?: TParams;
    data?: TVars;
    headers?: Record<string, string>;
    signal?: AbortSignal;
    /** 对于登录等无需鉴权的接口可禁用 Authorization 头（透传给底层 fetcher） */
    disableAuthHeader?: boolean;
};

/** 重载：支持 orval v8 默认的 (url, init) 与旧式 ({ url, ...cfg }) 两种方式 */
export async function orvalFetcher<TData>(url: string, init?: ApiFetchInit): Promise<TData>;
export async function orvalFetcher<
    TData,
    TVars extends AllowedBody = undefined,
    TParams extends Record<string, QueryValue> = Record<string, QueryValue>
>(cfg: OrvalConfig<TVars, TParams>): Promise<TData>;

/** 实现：运行时判断第一个参数类型来兼容两种写法（无 any） */
export async function orvalFetcher<TData>(
    arg1: string | OrvalConfig<AllowedBody, Record<string, QueryValue>>,
    arg2?: ApiFetchInit
): Promise<TData> {
    // 方式 A：orvalFetcher(url, options)
    if (typeof arg1 === 'string') {
        const path = toPathOnly(arg1);
        return apiFetchWithAuth<TData>(path, arg2);
    }

    // 方式 B：orvalFetcher({ url, method, data, params, ... })
    const cfg = arg1;
    const path = toPathOnly(cfg.url);

    const normalizedParams: ApiFetchInit['params'] | undefined = cfg.params
        ? normalizeParams(cfg.params)
        : undefined;

    const init: ApiFetchInit = {
        method: cfg.method ?? (cfg.data != null ? 'POST' : 'GET'),
        params: normalizedParams,
        body: cfg.data,
        headers: cfg.headers,
        signal: cfg.signal,
        ...(cfg.disableAuthHeader ? { disableAuthHeader: true } : {}),
    };

    return apiFetchWithAuth<TData>(path, init);
}

/** 将可能包含数组的查询参数规整为底层 fetcher 能接受的原子类型 */
function normalizeParams(
    params: Record<string, QueryValue>
): Record<string, string | number | boolean | null | undefined> {
    const out: Record<string, string | number | boolean | null | undefined> = {};
    Object.entries(params).forEach(([k, v]) => {
        if (Array.isArray(v)) {
            const joined = v
                .filter((x): x is Exclude<QueryPrimitive, undefined> => x !== undefined)
                .map((x) => String(x))
                .join(',');
            out[k] = joined;
        } else {
            out[k] = v as QueryPrimitive;
        }
    });
    return out;
}

export default orvalFetcher;
