package com.xiaoyuan.paperpuppybackendintl.controller;

import com.xiaoyuan.paperpuppybackendintl.controller.admin.AdminCacheController;
import com.xiaoyuan.paperpuppybackendintl.controller.user.UserCustomSkillController;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiOperationIdContractTest {

    @Test
    void previouslySuffixedOperationsUseStableUniqueOperationIds() {
        List<OperationMethod> operations = List.of(
                operation(UserCustomSkillController.class, "listSkills", "listUserCustomSkills"),
                operation(AdminCacheController.class, "refresh", "refreshAdminCache"),
                operation(UserCustomSkillController.class, "toggleSkill", "toggleUserCustomSkill")
        );

        Set<String> operationIds = new LinkedHashSet<>();
        for (OperationMethod operation : operations) {
            String operationId = operation.annotation().operationId();
            assertEquals(operation.expectedOperationId(), operationId);
            assertFalse(operationId.matches(".*\\d+$"), "operationId must not use suffix fallback: " + operationId);
            assertTrue(operationIds.add(operationId), "duplicate operationId: " + operationId);
        }
    }

    private static OperationMethod operation(Class<?> controller, String methodName, String operationId) {
        Operation annotation = method(controller, methodName).getAnnotation(Operation.class);
        assertNotNull(annotation, controller.getSimpleName() + "." + methodName + " must declare @Operation");
        return new OperationMethod(annotation, operationId);
    }

    private static Method method(Class<?> controller, String methodName) {
        return List.of(controller.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing method " + controller.getSimpleName() + "." + methodName));
    }

    private record OperationMethod(Operation annotation, String expectedOperationId) {
    }
}
