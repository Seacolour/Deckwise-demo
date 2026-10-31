package com.xiaoyuan.paperpuppybackendintl.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiOperationIdStartupCheckerTest {

    @Test
    void findsUniqueOperationIdsWithGeneratedNumericSuffixes() {
        OpenApiOperationIdStartupChecker checker = new OpenApiOperationIdStartupChecker(new MockEnvironment());

        List<String> operationIds = checker.findSuffixedOperationIds("""
                {
                  "paths": {
                    "/projects": {
                      "get": {"operationId": "listProjects_1"},
                      "post": {"operationId": "createProject"}
                    },
                    "/sessions/{sessionId}/abort": {
                      "post": {"operationId": "abort_1"}
                    },
                    "/duplicate": {
                      "get": {"operationId": "abort_1"}
                    }
                  }
                }
                """);

        assertEquals(List.of("listProjects_1", "abort_1"), operationIds);
    }

    @Test
    void buildsLocalApiDocsUriFromServerAndSpringdocProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("local.server.port", "8104")
                .withProperty("server.servlet.context-path", "/api/")
                .withProperty("springdoc.api-docs.path", "v3/api-docs");
        OpenApiOperationIdStartupChecker checker = new OpenApiOperationIdStartupChecker(environment);

        URI uri = checker.localApiDocsUri();

        assertEquals(URI.create("http://127.0.0.1:8104/api/v3/api-docs"), uri);
    }

    @Test
    void usesDefaultApiDocsPathWhenSpringdocPathIsMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "9000");
        OpenApiOperationIdStartupChecker checker = new OpenApiOperationIdStartupChecker(environment);

        URI uri = checker.localApiDocsUri();

        assertEquals(URI.create("http://127.0.0.1:9000/v3/api-docs"), uri);
    }
}
