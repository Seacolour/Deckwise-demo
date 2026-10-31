package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiGenerationConfigTest {

    @Test
    void currentUserIdIsMarkedHiddenForSpringdoc() {
        Parameter parameter = CurrentUserId.class.getAnnotation(Parameter.class);

        assertNotNull(parameter);
        assertTrue(parameter.hidden());
    }

    @Test
    void customizerRemovesCurrentUserIdFromParametersAndMultipartBody() throws Exception {
        ObjectSchema multipartSchema = new ObjectSchema();
        multipartSchema.addProperty("file", new StringSchema().format("binary"));
        multipartSchema.addProperty("userId", new io.swagger.v3.oas.models.media.IntegerSchema().format("int64"));
        multipartSchema.setRequired(new ArrayList<>(List.of("file", "userId")));

        Operation operation = new Operation()
                .addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter()
                        .name("userId")
                        .in("query"))
                .requestBody(new RequestBody().content(new Content().addMediaType(
                        org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE,
                        new MediaType().schema(multipartSchema)
                )));

        Method method = MultipartFixture.class.getDeclaredMethod("upload", Long.class, MultipartFile.class);
        HandlerMethod handlerMethod = new HandlerMethod(new MultipartFixture(), method);

        new OpenApiGenerationConfig()
                .frontendOpenApiOperationCustomizer()
                .customize(operation, handlerMethod);

        assertTrue(operation.getParameters().isEmpty());
        assertFalse(multipartSchema.getProperties().containsKey("userId"));
        assertFalse(multipartSchema.getRequired().contains("userId"));
        assertTrue(multipartSchema.getProperties().containsKey("file"));
        assertTrue(multipartSchema.getRequired().contains("file"));
    }

    private static final class MultipartFixture {
        @SuppressWarnings("unused")
        void upload(@CurrentUserId Long userId, MultipartFile file) {
        }
    }
}
