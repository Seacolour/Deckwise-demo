// lib/api/proxy/proxyImage.ts
/**
 * 图片代理下载模块
 * 使用统一的代理核心，提供图片特定的功能
 */

import {fetchViaProxy, type BaseProxyOptions, type ResponseMetadata} from './core';
import {debugLog} from '@/src/lib/debug';

/* ---------------------------------------------------------------------------
   类型定义
--------------------------------------------------------------------------- */

export type ImageProgress = (progress: number, written: number, expected: number) => void;

export type ProxyImageOptions = BaseProxyOptions & {
    /** 校验返回是否为 image/*；默认 true */
    enforceImageMime?: boolean;
};

export type ProxyImageResult = {
    blob: Blob;
    /** 可直接赋给 <img src={url}>；用完后可调用 revokeProxyImageUrl 释放 */
    url: string;
    mime?: string | null;
    contentLength?: number | null;
};

/* ---------------------------------------------------------------------------
   图片代理实现
--------------------------------------------------------------------------- */

/**
 * 将核心的 ResponseMetadata 转换为 ProxyImageResult
 */
function buildImageResult(blob: Blob, metadata: ResponseMetadata, enforceMime: boolean): ProxyImageResult {
    const mime = metadata.contentType?.toLowerCase() ?? null;

    // MIME 类型校验
    if (enforceMime && mime && !mime.startsWith('image/')) {
        throw new Error(`Response type is not an image: ${metadata.contentType}`);
    }

    const objectUrl = URL.createObjectURL(blob);

    return {
        blob,
        url: objectUrl,
        mime,
        contentLength: metadata.contentLength,
    };
}

/**
 * 通过后端代理接口下载图片（带进度、可取消、超时）
 * @param imageUrl - 图片 URL
 * @param opts - 代理选项
 * @returns 图片下载结果，包含 blob 和 object URL
 */
export async function fetchImageViaProxy(
    imageUrl: string,
    opts: ProxyImageOptions = {}
): Promise<ProxyImageResult> {
    const url = `/api/proxy?url=${encodeURIComponent(imageUrl)}`;
    debugLog('fetchImageViaProxy', imageUrl);

    // 使用核心 fetch 函数
    const {blob, metadata} = await fetchViaProxy(url, {
        ...opts,
        accept: 'image/*',
    });

    const enforceMime = opts.enforceImageMime ?? true;
    return buildImageResult(blob, metadata, enforceMime);
}

/* ---------------------------------------------------------------------------
   辅助函数
--------------------------------------------------------------------------- */

/**
 * 将图片加载到指定 <img> 元素
 * 使用完成后，如无需复用，可调用 revokeProxyImageUrl(img.src) 释放。
 * @param img - 目标 img 元素
 * @param imageUrl - 图片 URL
 * @param opts - 代理选项
 */
export async function loadImageToElement(
    img: HTMLImageElement,
    imageUrl: string,
    opts?: ProxyImageOptions
): Promise<void> {
    const {url} = await fetchImageViaProxy(imageUrl, opts);
    img.src = url;
}

/**
 * 释放由 fetchImageViaProxy 创建的 ObjectURL，防止内存泄漏
 * @param objectUrl - 要释放的 Object URL
 */
export function revokeProxyImageUrl(objectUrl: string): void {
    try {
        URL.revokeObjectURL(objectUrl);
    } catch {
        // noop
    }
}
