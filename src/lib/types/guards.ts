/**
 * 统一的类型守卫函数
 *
 * 提供项目中常用的类型检查工具，避免重复定义。
 */

/**
 * 检查值是否为非空对象（包括数组和普通对象）
 * 这是 TypeScript 中 "object" 类型的常见守卫
 */
export function isObject(v: unknown): v is Record<string, unknown> {
    return typeof v === 'object' && v !== null;
}

/**
 * 检查值是否为普通对象（非数组、非 null、非特殊对象）
 * 用于排除数组和特殊内置对象
 */
export function isPlainObject(v: unknown): v is Record<string, unknown> {
    if (!isObject(v)) return false;
    // 排除数组
    if (Array.isArray(v)) return false;
    // 排除 Date、RegExp、null 等特殊对象
    const proto = Object.getPrototypeOf(v);
    return proto === null || proto === Object.prototype;
}

/**
 * 检查值是否为 Record 类型（即非 null 的对象）
 * 与 isObject 功能相同，提供别名方便语义化使用
 */
export function isRecord(v: unknown): v is Record<string, unknown> {
    return typeof v === 'object' && v !== null;
}

/**
 * 检查对象是否包含指定的属性键
 */
export function hasProp(obj: unknown, key: string | number | symbol): boolean {
    return isObject(obj) && key in obj;
}

/**
 * 检查对象是否包含所有指定的属性键
 */
export function hasProps(obj: unknown, keys: readonly (string | number | symbol)[]): boolean {
    if (!isObject(obj)) return false;
    return keys.every(key => key in obj);
}

/**
 * 从对象中提取字符串类型的属性值
 * 支持多个候选键名，返回第一个有效的字符串值
 */
export function pickString(
    obj: Record<string, unknown>,
    keys: string[]
): string | undefined;

/**
 * 检查值是否为非空字符串并返回
 */
export function pickString(v: unknown): string | undefined;

export function pickString(
    arg1: Record<string, unknown> | unknown,
    arg2?: string[]
): string | undefined {
    // 单参数版本：pickString(v)
    if (arg2 === undefined) {
        const v = arg1 as unknown;
        return typeof v === 'string' && v.trim().length > 0 ? v : undefined;
    }
    // 双参数版本：pickString(obj, keys)
    const obj = arg1 as Record<string, unknown>;
    const keys = arg2;
    for (const k of keys) {
        if (!(k in obj)) continue;
        const v = obj[k];
        if (typeof v === 'string') return v;
        if (typeof v === 'number') return String(v);
        if (typeof v === 'boolean') return String(v);
    }
    return undefined;
}

/**
 * 从对象中提取第一个符合条件的值
 */
export function pickFirst<T>(
    obj: Record<string, unknown>,
    keys: string[],
    map: (v: unknown) => T | undefined
): T | undefined {
    for (const k of keys) {
        if (!(k in obj)) continue;
        const out = map(obj[k]);
        if (out !== undefined) return out;
    }
    return undefined;
}

/**
 * 检查值是否为非空数组
 */
export function isNonEmptyArray<T>(v: unknown): v is [T, ...T[]] {
    return Array.isArray(v) && v.length > 0;
}

/**
 * 检查值是否为字符串且非空
 */
export function isNonEmptyString(v: unknown): v is string {
    return typeof v === 'string' && v.trim().length > 0;
}

/**
 * 检查值是否为数字且非 NaN
 */
export function isFiniteNumber(v: unknown): v is number {
    return typeof v === 'number' && Number.isFinite(v);
}
