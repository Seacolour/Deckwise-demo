package com.xiaoyuan.paperpuppybackendintl.service.skill;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PromptSkill 文件导入服务：解析 .md / .zip，提取 YAML frontmatter + Markdown body。
 */
@Slf4j
@Service
public class SkillImportService {

    private static final int MAX_CONTENT_BYTES = 64 * 1024;
    private static final int MAX_ZIP_ENTRY_BYTES = 256 * 1024;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-]{0,127}$");
    private static final String FRONTMATTER_DELIMITER = "---";

    /**
     * 从上传文件解析技能：自动识别 .md 或 .zip。
     */
    public ParsedSkill parseFromFile(MultipartFile file) {
        String originalName = Objects.toString(file.getOriginalFilename(), "").trim().toLowerCase();
        try {
            if (originalName.endsWith(".zip")) {
                return parseFromZip(file.getBytes());
            }
            if (originalName.endsWith(".md")) {
                String raw = new String(file.getBytes(), StandardCharsets.UTF_8);
                return parseMdContent(raw);
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件格式，仅接受 .md 或 .zip");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Markdown 文本：分离 YAML frontmatter 和 body。
     */
    public ParsedSkill parseMdContent(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "skill.md 内容为空");
        }

        String trimmed = raw.strip();
        String name = null;
        String displayName = null;
        String description = null;
        String version = null;
        String body;

        if (trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            int secondDelim = trimmed.indexOf("\n" + FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
            if (secondDelim < 0) {
                body = trimmed;
            } else {
                String yamlBlock = trimmed.substring(FRONTMATTER_DELIMITER.length(), secondDelim).trim();
                body = trimmed.substring(secondDelim + 1 + FRONTMATTER_DELIMITER.length()).strip();

                try {
                    Yaml yaml = new Yaml();
                    Map<String, Object> meta = yaml.load(yamlBlock);
                    if (meta != null) {
                        name = str(meta.get("name"));
                        displayName = str(meta.get("display_name"));
                        description = str(meta.get("description"));
                        version = str(meta.get("version"));
                    }
                } catch (Exception e) {
                    log.warn("YAML frontmatter 解析失败，忽略 frontmatter: {}", e.getMessage());
                    body = trimmed;
                }
            }
        } else {
            body = trimmed;
        }

        return new ParsedSkill(name, displayName, description, version, body);
    }

    /**
     * 从 ZIP 中查找 skill.md / SKILL.md，解析后返回。
     */
    public ParsedSkill parseFromZip(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                String fileName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf('/') + 1)
                        : entryName;

                if ("skill.md".equalsIgnoreCase(fileName)) {
                    byte[] data = readZipEntry(zis);
                    String raw = new String(data, StandardCharsets.UTF_8);
                    return parseMdContent(raw);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "ZIP 解压失败: " + e.getMessage());
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "ZIP 中未找到 skill.md 文件");
    }

    /**
     * 校验 ParsedSkill 字段合法性，name/description 可由调用方补填后再校验。
     */
    public void validate(ParsedSkill skill) {
        if (skill.name() == null || skill.name().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能名称(name)不能为空");
        }
        if (!NAME_PATTERN.matcher(skill.name()).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "技能名称格式无效，仅允许小写字母、数字和连字符，且以字母或数字开头，最长 128 字符");
        }
        if (skill.description() == null || skill.description().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能描述(description)不能为空");
        }
        if (skill.description().length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能描述不能超过 1024 字符");
        }
        if (skill.content() == null || skill.content().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能内容(content)不能为空");
        }
        if (skill.content().getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能内容不能超过 64KB");
        }
    }

    private byte[] readZipEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int totalRead = 0;
        int n;
        while ((n = zis.read(buf)) >= 0) {
            totalRead += n;
            if (totalRead > MAX_ZIP_ENTRY_BYTES) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "ZIP 条目过大（超过 256KB）");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static String str(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = obj.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
