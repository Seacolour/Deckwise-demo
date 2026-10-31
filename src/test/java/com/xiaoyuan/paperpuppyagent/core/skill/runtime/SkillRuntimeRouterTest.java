package com.xiaoyuan.paperpuppyagent.core.skill.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillFunction;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillSafety;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillScope;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillRuntimeType;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeRouterTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    void routesByBindingRuntimeType() throws Exception {
        StubRuntime nativeRuntime = new StubRuntime(SkillRuntimeType.NATIVE, "native");
        SkillRuntimeRouter router = new SkillRuntimeRouter(List.of(nativeRuntime));

        SkillExecutionResult result = router.execute(
                sampleFunction("file_list"),
                new SkillFunctionBinding("file_list", SkillRuntimeType.NATIVE, "file_list", null, null),
                om.createObjectNode(),
                ToolExecutionContext.empty());

        assertTrue(nativeRuntime.invoked);
        assertEquals("native", result.payload().path("runtime").asText());
    }

    @Test
    void throwsWhenRuntimeIsMissing() {
        SkillRuntimeRouter router = new SkillRuntimeRouter(List.of());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> router.execute(
                sampleFunction("file_list"),
                new SkillFunctionBinding("file_list", SkillRuntimeType.NATIVE, "file_list", null, null),
                om.createObjectNode(),
                ToolExecutionContext.empty()));

        assertTrue(ex.getMessage().contains("NATIVE"));
    }

    private SkillFunction sampleFunction(String name) {
        return new SkillFunction(
                "test.skill",
                "1.0.0",
                name,
                "desc",
                true,
                om.createObjectNode(),
                SkillScope.none(),
                SkillSafety.defaultReadOnly());
    }

    private static final class StubRuntime implements SkillRuntime {
        private final SkillRuntimeType type;
        private final String marker;
        private boolean invoked;

        private StubRuntime(SkillRuntimeType type, String marker) {
            this.type = type;
            this.marker = marker;
        }

        @Override
        public SkillRuntimeType type() {
            return type;
        }

        @Override
        public SkillExecutionResult execute(SkillFunction function,
                                            SkillFunctionBinding binding,
                                            com.fasterxml.jackson.databind.JsonNode arguments,
                                            ToolExecutionContext context) {
            invoked = true;
            ObjectNode out = new ObjectMapper().createObjectNode();
            out.put("runtime", marker);
            return SkillExecutionResult.toolResult(out);
        }
    }
}
