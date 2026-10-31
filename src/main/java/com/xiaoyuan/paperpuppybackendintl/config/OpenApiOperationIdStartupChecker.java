package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile({"default", "development", "dev", "local"})
@Slf4j
@RequiredArgsConstructor
public class OpenApiOperationIdStartupChecker {

    private static final Pattern SUFFIXED_OPERATION_ID_PATTERN =
            Pattern.compile("\"operationId\"\\s*:\\s*\"([^\"\\s]+_\\d+)\"");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void checkOperationIdsAfterStartup() {
        URI apiDocsUri = localApiDocsUri();
        try {
            String openApiJson = fetchOpenApiJson(apiDocsUri);
            List<String> suffixedOperationIds = findSuffixedOperationIds(openApiJson);
            if (suffixedOperationIds.isEmpty()) {
                log.info("OpenAPI operationId startup check passed: no generated numeric suffixes found.");
                return;
            }
            log.warn(
                    "OpenAPI operationId startup check found generated numeric suffixes. " +
                            "These usually indicate controller method naming conflicts. uri={}, operationIds={}",
                    apiDocsUri,
                    suffixedOperationIds
            );
        } catch (Exception e) {
            log.warn("OpenAPI operationId startup check failed: uri={}, message={}", apiDocsUri, e.getMessage());
        }
    }

    List<String> findSuffixedOperationIds(String openApiJson) {
        if (!StringUtils.hasText(openApiJson)) {
            return List.of();
        }
        Matcher matcher = SUFFIXED_OPERATION_ID_PATTERN.matcher(openApiJson);
        Set<String> operationIds = new LinkedHashSet<>();
        while (matcher.find()) {
            operationIds.add(matcher.group(1));
        }
        return new ArrayList<>(operationIds);
    }

    URI localApiDocsUri() {
        int port = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
        String contextPath = normalizePath(environment.getProperty("server.servlet.context-path", ""));
        String apiDocsPath = normalizePath(environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"));
        return URI.create("http://127.0.0.1:" + port + contextPath + apiDocsPath);
    }

    private String fetchOpenApiJson(URI apiDocsUri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiDocsUri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("unexpected HTTP status " + response.statusCode());
        }
        return response.body();
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
