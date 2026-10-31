package com.xiaoyuan.paperpuppybackendintl.utils.file;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PDF 解析工具（基于 PDFBox 3.x）
 *
 * 设计目标：
 * - 不落临时文件（接收 byte[]/InputStream）
 * - 可控的页数/文本长度限制，防止超大 PDF 把服务拖死
 * - 标题提取：优先元信息，其次从正文第一行非空内容兜底
 */
public final class PdfParser {

    private PdfParser() {
    }

    private static final String LEVEL_RAW_TEXT = "RAW_TEXT";
    private static final String PARSER_PDFBOX = "pdfbox";
    private static final String DEFAULT_UNTITLED = "Untitled PDF";

    public static PdfParseResult parse(byte[] pdfBytes, PdfParseOptions options) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 内容为空");
        }
        try (InputStream is = new ByteArrayInputStream(pdfBytes)) {
            return parse(is, options);
        } catch (IOException e) {
            // ByteArrayInputStream 这里不会真的抛 IOE，但保持一致性
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取 PDF 失败");
        }
    }

    public static PdfParseResult parse(InputStream pdfStream, PdfParseOptions options) {
        if (pdfStream == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 输入流为空");
        }
        PdfParseOptions opt = (options == null) ? PdfParseOptions.builder().build() : options;

        byte[] bytes = readAllBytesWithLimit(pdfStream, opt.getMaxBytes());

        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pages = document.getNumberOfPages();
            if (pages <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 为空文档");
            }
            if (pages > opt.getMaxPages()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 页数过多（最大 " + opt.getMaxPages() + " 页）");
            }

            String parserVersion = getParserVersion();

            String title = extractTitle(document);
            // 权威 title：只信元信息；没有就兜底 Untitled PDF
            boolean hasMetaTitle;
            if (title != null && !title.isBlank()) {
                hasMetaTitle = true;
                title = title.trim();
            } else {
                hasMetaTitle = false;
                title = DEFAULT_UNTITLED;
            }

            String rawText = extractText(document, opt);
            String text = normalizeText(rawText);

            if (text.length() > opt.getMaxTextChars()) {
                text = text.substring(0, opt.getMaxTextChars());
            }

            // 弱猜标题：只作为 UI 候选，不覆盖权威 title
            String titleCandidate = hasMetaTitle ? null : guessTitleFromText(text);

            return new PdfParseResult(
                    LEVEL_RAW_TEXT,
                    PARSER_PDFBOX,
                    parserVersion,
                    title,
                    titleCandidate,
                    text,
                    pages
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            // PDFBox 常见：加密 PDF / 非法格式 / 损坏文件 都是 IOException
            if (isInvalidPasswordException(e)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 已加密，暂不支持解析");
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 格式非法或已损坏，解析失败");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "PDF 解析异常");
        }
    }

    /**
     * 提取预览：从前 N 个字符中裁剪到相对自然的断点。
     */
    public static String preview(String text, int previewChars) {
        if (text == null || text.isBlank() || previewChars <= 0) {
            return "";
        }
        String t = normalizeText(text);
        if (t.length() <= previewChars) {
            return t;
        }
        // 向后找一个相对自然的断点，避免截断单词/句子
        int limit = Math.min(previewChars + 40, t.length());
        for (int i = previewChars; i < limit; i++) {
            char c = t.charAt(i);
            if (c == ' ' || c == '.' || c == ',' || c == ';' || c == ':' ||
                    c == '。' || c == '，' || c == '；' || c == '：' || c == '！' || c == '？') {
                return t.substring(0, i + 1).trim();
            }
        }
        return t.substring(0, previewChars).trim();
    }

    private static String extractTitle(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        if (info == null) {
            return null;
        }
        String title = info.getTitle();
        if (title != null) {
            title = title.trim();
        }
        return title;
    }

    private static String extractText(PDDocument document, PdfParseOptions opt) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(opt.isSortByPosition());
        int start = Math.max(1, opt.getStartPage());
        int end = Math.min(document.getNumberOfPages(), opt.getEndPage());
        if (start > end) {
            start = 1;
            end = document.getNumberOfPages();
        }
        if (!opt.isIncludePageMarkers()) {
            stripper.setStartPage(start);
            stripper.setEndPage(end);
            return stripper.getText(document);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            sb.append("--- PAGE ").append(i).append(" ---").append('\n');
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            sb.append(stripper.getText(document)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 文本清洗：保留换行（便于页边界/分片/定位），合并多余空格与空行。
     */
    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        // 1️⃣ 移除 PostgreSQL / JSON 不允许的 NUL 字符
        // （PDFBox 抽文本时非常常见）
        String t = text.replace("\u0000", "");

        // 2️⃣ 可选：移除其他 ASCII 控制字符（保留 \n）
        // t = t.replaceAll("[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 3️⃣ 统一换行
        t = t.replace("\r\n", "\n").replace('\r', '\n');

        // 4️⃣ tab -> space
        t = t.replace('\t', ' ');

        // 5️⃣ 合并多空格（不跨行）
        t = t.replaceAll("[ ]{2,}", " ");

        // 6️⃣ 合并多空行
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.trim();
    }

    private static String guessTitleFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 更保守：只取“第一页标记后”的第一行非空内容；过长则截断
        String t = normalizeText(text);
        String[] lines = t.split("\\n", 20);
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            // 跳过页边界行
            if (s.startsWith("--- PAGE ") && s.endsWith(" ---")) {
                continue;
            }
            // 跳过明显像页眉/页脚的极短行（可选：暂不做太激进规则）
            return s.length() > 120 ? s.substring(0, 120) : s;
        }
        return null;
    }

    private static byte[] readAllBytesWithLimit(InputStream is, long maxBytes) {
        long limit = maxBytes <= 0 ? (50L * 1024 * 1024) : maxBytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = is.read(buf)) >= 0) {
                if (n == 0) {
                    continue;
                }
                total += n;
                if (total > limit) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "PDF 文件过大（最大 " + limit + " bytes）");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取 PDF 输入流失败");
        }
    }

    private static boolean isInvalidPasswordException(IOException e) {
        // 避免强依赖具体异常类名（不同版本包名可能变化）
        return e != null && e.getClass().getName().endsWith("InvalidPasswordException");
    }

    private static String getParserVersion() {
        try {
            Package p = Loader.class.getPackage();
            String v = (p == null) ? null : p.getImplementationVersion();
            if (v == null || v.isBlank()) {
                // 部分构建环境拿不到 Implementation-Version
                return "3.x";
            }
            return v;
        } catch (Exception ignored) {
            return "3.x";
        }
    }
}


