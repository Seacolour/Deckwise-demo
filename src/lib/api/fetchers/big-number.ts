/**
 * 大数字处理模块
 *
 * 处理 JSON 中超过 Number.MAX_SAFE_INTEGER 的数字，避免精度丢失
 * 提供两种处理方式：
 * 1. 文本预处理：在 JSON.parse 之前将大整数转换为字符串
 * 2. 对象后处理：在解析完成后递归遍历对象转换大数字
 */

/**
 * 在 JSON 解析之前，将大数字（超过 Number.MAX_SAFE_INTEGER）转换为字符串
 * 这样可以避免 JSON.parse() 解析时丢失精度
 *
 * 使用状态机遍历 JSON 文本，识别不在字符串内的整数，并将超过安全范围的大整数转换为字符串
 *
 * @param jsonText - 原始 JSON 文本
 * @returns 处理后的 JSON 文本，大数字被转换为字符串格式
 *
 * @example
 * ```ts
 * const json = '{"id": 9007199254740992, "small": 123}';
 * const processed = preprocessBigNumbersInJson(json);
 * // '{"id": "9007199254740992", "small": 123}'
 * const result = JSON.parse(processed);
 * ```
 */
export function preprocessBigNumbersInJson(jsonText: string): string {
    let result = '';
    let inString = false;
    let escapeNext = false;
    let i = 0;

    while (i < jsonText.length) {
        const char = jsonText[i];

        if (escapeNext) {
            result += char;
            escapeNext = false;
            i++;
            continue;
        }

        if (char === '\\') {
            result += char;
            escapeNext = true;
            i++;
            continue;
        }

        if (char === '"') {
            result += char;
            inString = !inString;
            i++;
            continue;
        }

        if (!inString) {
            // 不在字符串内，检查是否是整数的开始
            // 匹配：-?[0-9]+（只匹配整数，不包括小数和科学计数法）
            const numberMatch = jsonText.slice(i).match(/^-?\d+/);
            if (numberMatch) {
                const numStr = numberMatch[0];
                const numStrLen = numStr.length;

                // 检查这个数字后面是否跟着小数点或 e/E（如果是，说明是小数或科学计数法，不处理）
                const nextChar = jsonText[i + numStrLen];
                const isDecimalOrScientific = nextChar === '.' || nextChar === 'e' || nextChar === 'E';

                if (!isDecimalOrScientific) {
                    // 检查是否是独立的 JSON 值（前面是 : 或 , 或 [ 或 { 或空白，后面是 , 或 } 或 ] 或空白或结束）
                    const prevChar = i > 0 ? jsonText[i - 1] : '';
                    const isValueStart = /^[\s:,{\[]$/.test(prevChar) || i === 0;

                    if (isValueStart) {
                        // 使用字符串比较来判断是否超过安全整数范围，避免精度丢失
                        // MAX_SAFE_INTEGER = 9007199254740991 (16位)
                        const maxSafeStr = String(Number.MAX_SAFE_INTEGER);
                        const absNumStr = numStr.startsWith('-') ? numStr.slice(1) : numStr;

                        // 如果数字长度超过 MAX_SAFE_INTEGER 的位数，或者长度相同但数值更大，则转换为字符串
                        if (absNumStr.length > maxSafeStr.length ||
                            (absNumStr.length === maxSafeStr.length && absNumStr > maxSafeStr)) {
                            result += `"${numStr}"`;
                            i += numStrLen;
                            continue;
                        }
                    }
                }
            }
        }

        result += char;
        i++;
    }

    return result;
}

/**
 * 递归遍历对象，将超过 Number.MAX_SAFE_INTEGER 的数字转换为字符串
 * 这是备用方案，用于处理已经解析的 JSON（如果预处理失败）
 *
 * @param obj - 已解析的 JavaScript 对象
 * @returns 处理后的对象，大数字被转换为字符串
 *
 * @example
 * ```ts
 * const data = { id: 9007199254740992, small: 123, nested: { big: 9007199254740993 } };
 * const processed = convertBigNumbersToString(data);
 * // { id: "9007199254740992", small: 123, nested: { big: "9007199254740993" } }
 * ```
 */
export function convertBigNumbersToString(obj: unknown): unknown {
    if (typeof obj === 'number') {
        // 如果是大整数，转换为字符串
        if (Number.isInteger(obj) && (obj > Number.MAX_SAFE_INTEGER || obj < Number.MIN_SAFE_INTEGER)) {
            return String(obj);
        }
        return obj;
    }

    if (Array.isArray(obj)) {
        return obj.map(convertBigNumbersToString);
    }

    if (obj !== null && typeof obj === 'object') {
        const result: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(obj)) {
            result[key] = convertBigNumbersToString(value);
        }
        return result;
    }

    return obj;
}

/**
 * 处理 JSON 响应文本，确保大数字不会丢失精度
 *
 * 组合使用预处理和后处理两种方式：
 * 1. 先用 preprocessBigNumbersInJson 处理文本
 * 2. 解析后用 convertBigNumbersToString 作为备用
 *
 * @param rawText - 原始响应文本
 * @returns 处理后的对象
 *
 * @example
 * ```ts
 * const responseText = await res.text();
 * const data = parseJsonWithBigNumberSupport(responseText);
 * ```
 */
export function parseJsonWithBigNumberSupport<T = unknown>(rawText: string): T {
    // 在解析 JSON 之前，预处理大数字（转换为字符串以避免精度丢失）
    // 这是关键步骤：必须在 JSON.parse() 之前处理，否则精度已经丢失
    const preprocessedText = preprocessBigNumbersInJson(rawText);

    // 解析 JSON
    let json: T;
    try {
        json = JSON.parse(preprocessedText) as T;
    } catch (parseError) {
        // 如果预处理后的文本解析失败，尝试清理控制字符后再解析
        const processedText = preprocessedText.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '');

        try {
            json = JSON.parse(processedText) as T;
        } catch {
            // 清理后仍然失败，抛出原始错误
            throw parseError;
        }
    }

    // 解析成功后，再次递归处理大数字（作为备用方案，处理可能遗漏的情况）
    return convertBigNumbersToString(json) as T;
}
