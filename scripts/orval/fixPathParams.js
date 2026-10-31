// scripts/orval/fixPathParams.js
// 1. 自动把路径里的 {xxx} 在 operation.parameters 里补齐为 path 参数
// 2. 将所有 ID 相关字段转换为 string 类型（避免 bigint 精度丢失）
module.exports = function fixPathParams(spec) {
    if (!spec || !spec.paths) return spec;

    const METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];
    
    // ID 相关字段名称模式（不区分大小写）
    const ID_FIELD_PATTERNS = [
        /^id$/i,
        /Id$/,
        /_id$/i,
        /sourceId$/i,
        /projectId$/i,
        /sessionId$/i,
        /userId$/i,
        /cardId$/i,
    ];

    // 判断字段名是否是 ID 字段
    function isIdField(name) {
        if (!name || typeof name !== 'string') return false;
        return ID_FIELD_PATTERNS.some(pattern => pattern.test(name));
    }

    function isBinarySchema(schema) {
        if (!schema || typeof schema !== 'object') return false;
        return schema.type === 'string' && schema.format === 'binary';
    }

    function normalizeJsonContent(content) {
        if (!content || typeof content !== 'object') return content;
        if (content['application/json']) return content;
        if (!content['*/*']) return content;

        const wildcard = content['*/*'];
        const schema = wildcard?.schema;
        if (isBinarySchema(schema)) {
            return content;
        }

        content['application/json'] = wildcard;
        delete content['*/*'];
        return content;
    }

    // 递归转换 schema 中的 ID 字段为 string 类型
    function convertIdFieldsToString(schema) {
        if (!schema || typeof schema !== 'object') return schema;

        // 处理对象属性
        if (schema.properties) {
            for (const [key, value] of Object.entries(schema.properties)) {
                if (isIdField(key) && value && typeof value === 'object') {
                    // 如果是 integer 或 number 类型，转换为 string
                    if (value.type === 'integer' || value.type === 'number') {
                        value.type = 'string';
                        delete value.format; // 移除 format，如 int64
                    }
                } else if (value && typeof value === 'object') {
                    // 递归处理嵌套对象
                    convertIdFieldsToString(value);
                }
            }
        }

        // 处理数组项
        if (schema.items) {
            convertIdFieldsToString(schema.items);
        }

        // 处理 allOf, oneOf, anyOf
        ['allOf', 'oneOf', 'anyOf'].forEach(key => {
            if (Array.isArray(schema[key])) {
                schema[key].forEach(item => convertIdFieldsToString(item));
            }
        });

        return schema;
    }

    // 处理路径参数和查询参数
    for (const [path, pathItem] of Object.entries(spec.paths)) {
        // 抓取 /foo/{id}/bar 里的 id
        const namesInPath = Array.from(path.matchAll(/{([^}]+)}/g)).map((m) => m[1]);

        // path-level parameters 也要考虑
        const pathLevelParams = Array.isArray(pathItem.parameters) ? pathItem.parameters : [];
        pathItem.parameters = pathLevelParams.filter((param) => !(param?.in === 'query' && param?.name === 'userId'));

        for (const method of METHODS) {
            const op = pathItem[method];
            if (!op) continue;

            op.parameters = Array.isArray(op.parameters) ? op.parameters : [];
            op.parameters = op.parameters.filter((param) => !(param?.in === 'query' && param?.name === 'userId'));

            // 1. 补齐路径参数
            const existing = new Set(
                [...(pathItem.parameters || []), ...op.parameters]
                    .filter((p) => p && p.in === 'path')
                    .map((p) => p.name)
            );

            for (const name of namesInPath) {
                if (existing.has(name)) continue;
                op.parameters.push({
                    name,
                    in: 'path',
                    required: true,
                    schema: { type: 'string' },
                });
            }

            // 2. 转换所有参数中的 ID 字段为 string
            [...(pathItem.parameters || []), ...op.parameters].forEach(param => {
                if (!param || !param.schema) return;

                // 路径参数和查询参数中的 ID 字段
                if (isIdField(param.name)) {
                    if (param.schema.type === 'integer' || param.schema.type === 'number') {
                        param.schema.type = 'string';
                        delete param.schema.format;
                    }
                }
                
                // 递归处理嵌套 schema
                convertIdFieldsToString(param.schema);
            });

            // 3. 处理请求体中的 ID 字段
            if (op.requestBody) {
                const content = op.requestBody.content || {};
                Object.values(content).forEach(mediaType => {
                    if (mediaType.schema) {
                        convertIdFieldsToString(mediaType.schema);
                    }
                });
            }

            // 4. 处理响应中的 ID 字段（可选，如果需要的话）
            if (op.responses) {
                Object.values(op.responses).forEach(response => {
                    const content = normalizeJsonContent(response.content || {});
                    response.content = content;
                    Object.values(content).forEach(mediaType => {
                        if (mediaType.schema) {
                            convertIdFieldsToString(mediaType.schema);
                        }
                    });
                });
            }
        }
    }

    // 5. 处理全局 schemas/components 中的 ID 字段
    if (spec.components && spec.components.schemas) {
        Object.values(spec.components.schemas).forEach(schema => {
            convertIdFieldsToString(schema);
        });
    }

    return spec;
};
