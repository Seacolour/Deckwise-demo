package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keep generated OpenAPI friendly to frontend code generation.
 *
 * Current fixes:
 * - hide framework-injected {@link CurrentUserId} parameters from the spec
 * - remove framework-injected user IDs that Springdoc folds into multipart request bodies
 * - normalize generic wildcard JSON responses to "application/json" so code generators
 *   don't degrade standard API payloads into Blob
 */
@Configuration
public class OpenApiGenerationConfig {

    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String WILDCARD_MEDIA_TYPE = "*/*";

    @Bean
    public OperationCustomizer frontendOpenApiOperationCustomizer() {
        return (operation, handlerMethod) -> {
            hideCurrentUserId(operation, handlerMethod.getMethodParameters());
            normalizeJsonResponses(operation);
            return operation;
        };
    }

    private static void hideCurrentUserId(Operation operation, MethodParameter[] parameters) {
        var currentUserIdParameters = Arrays.stream(parameters)
                .filter(parameter -> parameter.hasParameterAnnotation(CurrentUserId.class))
                .toList();

        if (currentUserIdParameters.isEmpty()) {
            return;
        }

        Set<String> hiddenParameterNames = currentUserIdParameters.stream()
                .map(MethodParameter::getParameterName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        hiddenParameterNames.add("userId");

        if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
            operation.setParameters(
                    operation.getParameters().stream()
                            .filter(parameter -> !(
                                    "query".equals(parameter.getIn()) &&
                                            hiddenParameterNames.contains(parameter.getName())
                            ))
                            .toList()
            );
        }

        removeHiddenProperties(operation.getRequestBody() == null
                ? null
                : operation.getRequestBody().getContent(), hiddenParameterNames);
    }

    private static void removeHiddenProperties(Content content, Set<String> hiddenPropertyNames) {
        if (content == null || content.isEmpty()) {
            return;
        }
        content.values().forEach(mediaType -> {
            if (mediaType == null) {
                return;
            }
            Schema<?> schema = mediaType.getSchema();
            if (schema == null || schema.getProperties() == null || schema.getProperties().isEmpty()) {
                return;
            }
            hiddenPropertyNames.forEach(schema.getProperties()::remove);
            if (schema.getRequired() != null) {
                schema.getRequired().removeIf(hiddenPropertyNames::contains);
            }
        });
    }

    private static void normalizeJsonResponses(Operation operation) {
        if (operation.getResponses() == null || operation.getResponses().isEmpty()) {
            return;
        }

        operation.getResponses().values().forEach(OpenApiGenerationConfig::normalizeJsonResponse);
    }

    private static void normalizeJsonResponse(ApiResponse response) {
        Content content = response.getContent();
        if (content == null || content.containsKey(JSON_MEDIA_TYPE) || !content.containsKey(WILDCARD_MEDIA_TYPE)) {
            return;
        }

        var wildcardMediaType = content.get(WILDCARD_MEDIA_TYPE);
        var schema = wildcardMediaType != null ? wildcardMediaType.getSchema() : null;
        if (schema != null && "string".equals(schema.getType()) && "binary".equals(schema.getFormat())) {
            return;
        }

        content.addMediaType(JSON_MEDIA_TYPE, wildcardMediaType);
        content.remove(WILDCARD_MEDIA_TYPE);
    }
}
