// orval.config.js (orval v8+)
// 注意：orval v8 的 CLI 通过 jiti 按 “default export” 加载配置文件：
// - 必须 `export default ...`
// - `module.exports = ...` 在 v8 下会导致 “doesn't have a default export”
//
// 推荐用环境变量配置 OpenAPI 地址/Token，避免写死内网地址或把 token 提交到仓库。
// 用法示例：
//   ORVAL_OPENAPI_URL="http://localhost:8104/api/v3/api-docs" npm run api:gen
import { defineConfig } from 'orval';

function withToken(url, token) {
    if (!token) return url;
    // 如果用户已经在 URL 里显式写了 token，就不要重复追加
    try {
        const u = new URL(url);
        if (u.searchParams.has('token')) return url;
    } catch {
        // not an absolute URL; fall through to simple string check
        if (url.includes('token=')) return url;
    }

    const joiner = url.includes('?') ? '&' : '?';
    return `${url}${joiner}token=${encodeURIComponent(token)}`;
}

const OPENAPI_URL =
    process.env.ORVAL_OPENAPI_URL ||
    // 兼容旧名字，方便 CI/本地脚本迁移
    process.env.OPENAPI_URL ||
    'http://localhost:8104/api/v3/api-docs';

const OPENAPI_TOKEN =
    process.env.ORVAL_OPENAPI_TOKEN || process.env.OPENAPI_TOKEN || '';

const OPENAPI = withToken(OPENAPI_URL, OPENAPI_TOKEN);

const baseOutput = {
    client: 'fetch',
    prettier: false,
    override: {
        mutator: { path: './scripts/orval/orvalMutator.ts', name: 'orvalFetcher' },
    },
};

// 按当前 Backend OpenAPI tags 分组，保持已使用的生成客户端路径稳定。
// tag 名必须与 operation.tags 完全一致。
const SLUG_TO_TAGS = {
    'project-api': ['project-controller', 'project-session-controller'],
    session: ['session-controller'],
    documents: ['documents-controller'],
    'file-upload': ['files-controller'],
    'project-file-api': ['project-file-controller'],
    'user-account': [
        'user-controller',
        'user-custom-skill-controller',
        'user-memory-controller',
        'user-rule-controller',
        'user-skill-controller',
    ],
    'model-providers': ['model-controller', 'model-credential-controller'],
    'admin-cache': ['admin-cache-controller'],
    toolbook: ['tool-book-controller'],
};

const projects = {};

// 全量兜底：用于 “我就想把所有接口一次性更新出来” 的场景
projects.all = {
    input: {
        target: OPENAPI,
        override: { transformer: './scripts/orval/fixPathParams.js' },
    },
    output: {
        ...baseOutput,
        target: './src/lib/api/generated/all.ts',
        schemas: './src/lib/types/all',
    },
};

for (const [slug, tags] of Object.entries(SLUG_TO_TAGS)) {
    projects[slug] = {
        input: {
            target: OPENAPI,
            filters: { tags },
            override: { transformer: './scripts/orval/fixPathParams.js' },
        },
        output: {
            ...baseOutput,
            target: `./src/lib/api/generated/${slug}.ts`,
            schemas: './src/lib/types/all',
        },
    };
}

export default defineConfig(projects);
