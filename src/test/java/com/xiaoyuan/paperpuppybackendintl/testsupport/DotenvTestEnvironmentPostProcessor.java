package com.xiaoyuan.paperpuppybackendintl.testsupport;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes the repository-local .env available to Spring Boot tests without copying secrets into
 * JVM system properties or Surefire reports. Real environment variables and -D properties keep
 * their normal higher precedence.
 */
public final class DotenvTestEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "paperpuppyTestDotenv";
    private static final String ENABLED_PROPERTY = "paperpuppy.test.dotenv.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, true)) {
            return;
        }

        Path dotenv = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath()
                .normalize()
                .resolve(".env");
        if (!Files.isRegularFile(dotenv)) {
            return;
        }

        Map<String, Object> values = load(dotenv);
        if (values.isEmpty()) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            sources.remove(PROPERTY_SOURCE_NAME);
        }
        MapPropertySource dotenvSource = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, dotenvSource);
        } else {
            sources.addLast(dotenvSource);
        }
    }

    static Map<String, Object> load(Path dotenv) {
        try {
            List<String> lines = Files.readAllLines(dotenv, StandardCharsets.UTF_8);
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (index == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                parseLine(line, index + 1, values);
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test .env file", e);
        }
    }

    private static void parseLine(String rawLine, int lineNumber, Map<String, Object> values) {
        String line = rawLine.strip();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        if (line.startsWith("export ")) {
            line = line.substring("export ".length()).stripLeading();
        }

        int separator = line.indexOf('=');
        if (separator <= 0) {
            throw invalidLine(lineNumber);
        }
        String key = line.substring(0, separator).strip();
        if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw invalidLine(lineNumber);
        }

        String value = decodeValue(line.substring(separator + 1).strip(), lineNumber);
        values.put(key, value);
    }

    private static String decodeValue(String value, int lineNumber) {
        if (value.isEmpty()) {
            return "";
        }
        char quote = value.charAt(0);
        if (quote != '\'' && quote != '"') {
            return value;
        }
        if (value.length() < 2 || value.charAt(value.length() - 1) != quote) {
            throw invalidLine(lineNumber);
        }

        String content = value.substring(1, value.length() - 1);
        if (quote == '\'') {
            return content;
        }
        return decodeDoubleQuoted(content);
    }

    private static String decodeDoubleQuoted(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                decoded.append(current);
                continue;
            }
            char escaped = value.charAt(++i);
            decoded.append(switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> escaped;
            });
        }
        return decoded.toString();
    }

    private static IllegalStateException invalidLine(int lineNumber) {
        return new IllegalStateException("Invalid test .env entry at line " + lineNumber);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
