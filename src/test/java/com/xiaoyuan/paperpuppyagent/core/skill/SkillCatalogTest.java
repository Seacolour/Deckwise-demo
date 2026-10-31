package com.xiaoyuan.paperpuppyagent.core.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillCatalogTest {

    @Test
    void initLoadsCurrentSkillManifest() {
        SkillCatalog skillCatalog = new SkillCatalog(new ObjectMapper());
        ReflectionTestUtils.setField(skillCatalog, "manifests", "classpath*:skills/paperpuppy-internal/skill.json");
        ReflectionTestUtils.setField(skillCatalog, "webSearchEnabled", false);
        ReflectionTestUtils.setField(skillCatalog, "webSearchContextSize", "medium");
        ReflectionTestUtils.setField(skillCatalog, "globallyDisabledRaw", "");

        skillCatalog.init();

        assertNotNull(skillCatalog.getFunction("json_structure_read"));
        assertNotNull(skillCatalog.getFunction("json_subtree_read"));
        assertNull(skillCatalog.getFunction("json_patch"));
        assertNotNull(skillCatalog.getFunction("agent_ledger_update"));
        assertNotNull(skillCatalog.getFunction("agent_tool_result_open"));
        assertNull(skillCatalog.getFunction("slides_image_search"));
        assertNull(skillCatalog.getFunction("slides_theme_list"));
        assertNull(skillCatalog.getFunction("slides_smart_layout_variant_list"));
        assertNull(skillCatalog.getFunction("slides_theme_apply"));
        assertNull(skillCatalog.getFunction("slides_outline_generate"));
        assertNull(skillCatalog.getFunction("slides_blueprint_generate"));
        assertNull(skillCatalog.getFunction("slides_deck_generate"));
        assertNull(skillCatalog.getFunction("slides_card_locate"));
        assertNull(skillCatalog.getFunction("slides_card_structure_read"));
        assertNull(skillCatalog.getFunction("slides_deck_text_structure_read"));
        assertNull(skillCatalog.getFunction("slides_text_search"));
        assertNull(skillCatalog.getFunction("slides_rollback_list"));
        assertNull(skillCatalog.getFunction("slides_revision_events_list"));
        assertNull(skillCatalog.getFunction("slides_revision_event_restore"));
        assertNull(skillCatalog.getFunction("slides_rollback_restore"));
        assertNull(skillCatalog.getFunction("slides_text_replace"));
        assertNull(skillCatalog.getFunction("slides_card_delete"));
        assertNull(skillCatalog.getFunction("slides_card_reorder"));
        assertNull(skillCatalog.getFunction("slides_json_card_append"));
        assertNull(skillCatalog.getFunction("slides_workspace_text_patch"));
        assertNull(skillCatalog.getFunction("json_document_read"));
        assertNull(skillCatalog.getFunction("json_document_replace"));
        assertNull(skillCatalog.getFunction("json_document_patch"));
        assertFalse(skillCatalog.getFunction("json_structure_read").strict());
        assertFalse(skillCatalog.getFunction("json_subtree_read").strict());
        assertNull(skillCatalog.getFunction("spreadsheet_workbook_read"));
        assertNull(skillCatalog.getFunction("spreadsheet_locate"));
        assertNull(skillCatalog.getFunction("spreadsheet_resolve_range"));
        for (SkillFunction function : skillCatalog.listFunctions()) {
            assertEquals("object", function.inputSchema().path("type").asText(),
                    "OpenAI function parameters must declare top-level type=object: " + function.name());
            assertNoTopLevelCompositionKeywords(function);
            assertArraySchemasDeclareItems(function.name(), function.inputSchema(), "$");
        }
        assertNull(skillCatalog.getFunction("slides_authoring_model_read"));
        assertNull(skillCatalog.getFunction("slides_authoring_model_update"));
        assertNull(skillCatalog.getFunction("slides_page_debug_read"));
        assertNull(skillCatalog.getFunction("slides_page_update"));
        assertNull(skillCatalog.getFunction("slides_projected_markdown_read"));
        assertNull(skillCatalog.getFunction("slides_build_preview"));
    }

    private static void assertNoTopLevelCompositionKeywords(SkillFunction function) {
        for (String keyword : new String[]{"oneOf", "anyOf", "allOf", "enum", "not"}) {
            if (function.inputSchema().has(keyword)) {
                throw new AssertionError("OpenAI function parameters must not declare top-level "
                        + keyword + ": " + function.name());
            }
        }
    }

    private static void assertArraySchemasDeclareItems(String functionName, JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (declaresArrayType(node.path("type")) && !node.has("items")) {
                throw new AssertionError("OpenAI function array schema must declare items: "
                        + functionName + " path=" + path);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                assertArraySchemasDeclareItems(functionName, field.getValue(), path + "." + field.getKey());
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertArraySchemasDeclareItems(functionName, node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static boolean declaresArrayType(JsonNode type) {
        if (type == null || type.isMissingNode() || type.isNull()) {
            return false;
        }
        if (type.isTextual()) {
            return "array".equals(type.asText());
        }
        if (type.isArray()) {
            for (JsonNode item : type) {
                if ("array".equals(item.asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
