// lib/server/proxyDownload.ts
/**
 * 服务端代理下载模块
 * @deprecated 请直接从 @/src/lib/server/proxy 导入
 * 此文件保留以保持向后兼容
 */

// 导出统一代理系统的服务端模块
export type {ProxyDownloadOptions} from './proxy/core';
export {DEFAULT_FORWARD_HEADERS, proxyDownload} from './proxy/core';

// 默认导出以保持向后兼容
export {proxyDownload as default} from './proxy/core';
