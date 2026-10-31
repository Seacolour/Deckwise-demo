// lib/api/proxyImage.ts
/**
 * 图片代理下载模块
 * @deprecated 请直接从 @/src/lib/api/proxy 导入
 * 此文件保留以保持向后兼容
 */

// 导出统一代理系统的图片模块
export type {ImageProgress, ProxyImageOptions, ProxyImageResult} from './proxy/proxyImage';
export {
    fetchImageViaProxy,
    loadImageToElement,
    revokeProxyImageUrl,
} from './proxy/proxyImage';
