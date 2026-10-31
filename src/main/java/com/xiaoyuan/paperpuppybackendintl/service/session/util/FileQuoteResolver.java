package com.xiaoyuan.paperpuppybackendintl.service.session.util;

import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.service.session.util.FileQuoteParser.FileQuote;
import com.xiaoyuan.paperpuppybackendintl.service.session.util.FileQuoteParser.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link FileQuoteParser} 解析出的文件引用标记转换为 AI 可读的文本。
 *
 * <h3>策略</h3>
 * <ul>
 *   <li><b>Markdown (type=md)</b>：生成锚点提示，AI 可通过 {@code document_locate} / {@code document_read} 自行获取完整内容。</li>
 *   <li><b>PDF (type=pdf)</b>：在已有 fragment 中做内存模糊匹配，将最匹配的 fragment 直接注入 AI 上下文。</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>
 * ParseResult pr = FileQuoteParser.parse(rawMessage);
 * if (pr.hasQuotes()) {
 *     String aiMessage = resolver.resolve(pr);
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileQuoteResolver {

    private final FileTextFragmentRepository fileTextFragmentRepository;

    /** PDF fragment 匹配时最多读取的分片数 */
    private static final int MAX_FRAGMENTS_TO_SCAN = 50;
    /** PDF fragment 匹配时最多注入的分片数 */
    private static final int MAX_FRAGMENTS_TO_INJECT = 2;

    /**
     * 将解析结果中的所有引用标记替换为 AI 可读文本。
     *
     * @param parseResult FileQuoteParser.parse() 的结果
     * @return AI 可读的 message（所有 @pp[...]{...} 标记已被替换）
     */
    public String resolve(ParseResult parseResult) {
        if (!parseResult.hasQuotes()) {
            return parseResult.originalMessage();
        }

        List<String> replacements = new ArrayList<>();
        for (FileQuote quote : parseResult.quotes()) {
            String replacement;
            try {
                if (quote.isPdf()) {
                    replacement = resolvePdfQuote(quote);
                } else if (quote.isMd()) {
                    replacement = resolveMdQuote(quote);
                } else {
                    // 未知类型：退化为简单提示
                    replacement = resolveUnknownQuote(quote);
                }
            } catch (Exception e) {
                log.warn("[FileQuoteResolver] Failed to resolve quote: rawTag={}, error={}",
                        quote.rawTag(), e.getMessage());
                replacement = resolveUnknownQuote(quote);
            }
            replacements.add(replacement);
        }

        return parseResult.replaceAll(replacements);
    }

    // ==================== Markdown 引用 ====================

    /**
     * Markdown 引用：生成锚点提示，不注入文件内容。
     * AI 可通过 document_locate / document_read 自行获取。
     */
    private String resolveMdQuote(FileQuote quote) {
        StringBuilder sb = new StringBuilder();
        sb.append("[用户引用了文档「").append(quote.fileName()).append("」");

        // 提供 ID 信息
        if (quote.docId() != null) {
            sb.append("(document_id=").append(quote.docId());
            if (quote.fileId() != null) {
                sb.append(", file_id=").append(quote.fileId());
            }
            sb.append(")");
        } else if (quote.fileId() != null) {
            sb.append("(file_id=").append(quote.fileId()).append(")");
        }

        // 行号范围
        int[] range = quote.lineRange();
        if (range != null) {
            sb.append(" 的第 ").append(range[0]).append("-").append(range[1]).append(" 行");
        }
        sb.append("]");

        // 选中文本
        if (quote.hasSelectedText()) {
            sb.append("\n");
            for (String line : quote.selectedText().split("\n")) {
                sb.append("> ").append(line).append("\n");
            }
        }

        sb.append("\n如需查看完整上下文，可使用 document_locate 或 document_read 工具。");
        return sb.toString();
    }

    // ==================== PDF 引用 ====================

    /**
     * PDF 引用：在 fragment 中做模糊匹配，将最匹配的 fragment 直接注入。
     */
    private String resolvePdfQuote(FileQuote quote) {
        StringBuilder sb = new StringBuilder();
        sb.append("[用户引用了文件「").append(quote.fileName()).append("」");

        if (quote.fileId() != null) {
            sb.append("(file_id=").append(quote.fileId());
            if (quote.docId() != null) {
                sb.append(", document_id=").append(quote.docId());
            }
            sb.append(")");
        }
        sb.append(" 中的以下片段]");

        // 选中文本
        if (quote.hasSelectedText()) {
            sb.append("\n");
            for (String line : quote.selectedText().split("\n")) {
                sb.append("> ").append(line).append("\n");
            }
        }

        // 尝试匹配 fragment 并注入
        if (quote.fileId() != null && quote.hasSelectedText()) {
            String injected = matchAndInjectFragments(quote.fileId(), quote.fileName(), quote.selectedText());
            if (injected != null && !injected.isEmpty()) {
                sb.append("\n").append(injected);
            }
        } else if (quote.fileId() != null) {
            sb.append("\n如需查看文件内容，可使用 file_content_read 工具（file_id=")
                    .append(quote.fileId()).append("）。");
        }

        return sb.toString();
    }

    /**
     * 在 file 的所有 fragment 中做模糊匹配，返回注入文本。
     */
    private String matchAndInjectFragments(Long fileId, String fileName, String selectedText) {
        try {
            List<String> fragments = fileTextFragmentRepository.listFragmentContents(fileId, 0, MAX_FRAGMENTS_TO_SCAN);
            if (fragments == null || fragments.isEmpty()) {
                log.debug("[FileQuoteResolver] No fragments found for fileId={}", fileId);
                return "如需查看文件内容，可使用 file_content_read 工具（file_id=" + fileId + "）。";
            }

            // 对每个 fragment 计算匹配分数
            List<ScoredFragment> scored = new ArrayList<>();
            for (int i = 0; i < fragments.size(); i++) {
                String frag = fragments.get(i);
                if (frag == null || frag.isEmpty()) {
                    continue;
                }
                double score = computeMatchScore(frag, selectedText);
                if (score > 0) {
                    scored.add(new ScoredFragment(i, frag, score));
                }
            }

            if (scored.isEmpty()) {
                log.debug("[FileQuoteResolver] No matching fragments for fileId={}, selectedText='{}'",
                        fileId, selectedText.length() > 50 ? selectedText.substring(0, 50) + "..." : selectedText);
                return "如需查看文件内容，可使用 file_content_read 工具（file_id=" + fileId + "）。";
            }

            // 按分数降序排列，取 top-N
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            int injectCount = Math.min(MAX_FRAGMENTS_TO_INJECT, scored.size());

            StringBuilder sb = new StringBuilder();
            sb.append("--- 以下为匹配到的文件切片（供参考） ---\n");
            for (int i = 0; i < injectCount; i++) {
                ScoredFragment sf = scored.get(i);
                int fragIndex = sf.index + 1; // 1-based 展示
                sb.append("【FILE ").append(fileName).append(" · part ").append(fragIndex).append("】\n");
                sb.append(sf.content).append("\n");
                if (i < injectCount - 1) {
                    sb.append("\n");
                }
            }

            log.info("[FileQuoteResolver] Injected {} fragment(s) for fileId={}, topScore={}",
                    injectCount, fileId, scored.getFirst().score);
            return sb.toString();

        } catch (Exception e) {
            log.warn("[FileQuoteResolver] Fragment matching failed for fileId={}: {}", fileId, e.getMessage());
            return "如需查看文件内容，可使用 file_content_read 工具（file_id=" + fileId + "）。";
        }
    }

    // ==================== 匹配算法 ====================

    /**
     * 计算 fragment 与选中文本的匹配分数。
     * <p>
     * 综合使用三种策略：
     * <ol>
     *   <li>子串包含（权重最高）</li>
     *   <li>关键词重叠率</li>
     *   <li>最长公共子串比率</li>
     * </ol>
     *
     * @return 0.0 ~ 1.0 的匹配分数
     */
    static double computeMatchScore(String fragment, String selectedText) {
        if (fragment == null || fragment.isEmpty() || selectedText == null || selectedText.isEmpty()) {
            return 0.0;
        }

        String fragLower = fragment.toLowerCase();
        String selLower = selectedText.toLowerCase();

        // 策略 1：子串包含（最强信号）
        if (fragLower.contains(selLower)) {
            return 1.0;
        }
        if (selLower.contains(fragLower)) {
            return 0.9;
        }

        // 策略 2：关键词重叠率
        double keywordScore = computeKeywordOverlap(fragLower, selLower);

        // 策略 3：最长公共子串比率（仅在选中文本较短时使用，避免 O(n*m) 过大）
        double lcsScore = 0.0;
        if (selLower.length() <= 500 && fragLower.length() <= 8000) {
            int lcsLen = longestCommonSubstringLength(fragLower, selLower);
            lcsScore = (double) lcsLen / selLower.length();
        }

        // 加权组合
        return Math.max(keywordScore * 0.7, lcsScore * 0.8);
    }

    /**
     * 关键词重叠率：将选中文本按空白/标点分词，计算 fragment 包含了多少关键词。
     */
    private static double computeKeywordOverlap(String fragLower, String selLower) {
        // 按非字母数字字符（含中文标点）分词，过滤短词
        String[] keywords = selLower.split("[\\s\\p{Punct}\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B]+");
        if (keywords.length == 0) {
            return 0.0;
        }

        int matched = 0;
        int total = 0;
        for (String kw : keywords) {
            if (kw.length() < 2) {
                continue; // 忽略单字符词
            }
            total++;
            if (fragLower.contains(kw)) {
                matched++;
            }
        }

        return total == 0 ? 0.0 : (double) matched / total;
    }

    /**
     * 最长公共子串长度（空间优化版，O(min(n,m)) 空间）。
     */
    private static int longestCommonSubstringLength(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        // 确保 b 是较短的串（节省空间）
        if (a.length() < b.length()) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        int maxLen = 0;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                    if (curr[j] > maxLen) {
                        maxLen = curr[j];
                    }
                } else {
                    curr[j] = 0;
                }
            }
            // swap
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
            // reset curr
            java.util.Arrays.fill(curr, 0);
        }
        return maxLen;
    }

    // ==================== 未知类型 ====================

    private String resolveUnknownQuote(FileQuote quote) {
        StringBuilder sb = new StringBuilder();
        sb.append("[用户引用了文件「").append(quote.fileName()).append("」");
        if (quote.fileId() != null) {
            sb.append("(file_id=").append(quote.fileId()).append(")");
        }
        sb.append("]");
        if (quote.hasSelectedText()) {
            sb.append("\n");
            for (String line : quote.selectedText().split("\n")) {
                sb.append("> ").append(line).append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== 内部数据结构 ====================

    private record ScoredFragment(int index, String content, double score) {
    }
}
