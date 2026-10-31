package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillBindingCatalogTest {
    private SkillCatalog skillCatalog;
    private SkillBindingValidator validator;
    private SkillBindingCatalog bindingCatalog;

    @BeforeEach
    void setUp() {
        ObjectMapper om = new ObjectMapper();
        skillCatalog = new SkillCatalog(om);
        ReflectionTestUtils.setField(skillCatalog, "manifests", "classpath*:skills/paperpuppy-internal/skill.json");
        ReflectionTestUtils.setField(skillCatalog, "webSearchEnabled", false);
        ReflectionTestUtils.setField(skillCatalog, "webSearchContextSize", "medium");
        ReflectionTestUtils.setField(skillCatalog, "globallyDisabledRaw", "");
        skillCatalog.init();

        validator = new SkillBindingValidator(skillCatalog);
        bindingCatalog = new SkillBindingCatalog(om, validator);
        ReflectionTestUtils.setField(bindingCatalog, "bindingsPattern", "classpath*:skills/paperpuppy-internal/skill.binding.json");
    }

    @Test
    void initLoadsBindingsForAllFunctions() {
        bindingCatalog.init();

        assertEquals(skillCatalog.listFunctions().size(), bindingCatalog.listBindings().size());
        assertEquals(SkillRuntimeType.NATIVE, bindingCatalog.getBinding("agent_ledger_update").runtimeType());
        assertEquals(SkillRuntimeType.NATIVE, bindingCatalog.getBinding("agent_tool_result_open").runtimeType());
        assertNull(bindingCatalog.getBinding("slides_image_search"));
        assertNull(bindingCatalog.getBinding("slides_theme_list"));
        assertNull(bindingCatalog.getBinding("slides_smart_layout_variant_list"));
        assertNull(bindingCatalog.getBinding("slides_theme_apply"));
        assertNull(bindingCatalog.getBinding("slides_outline_generate"));
        assertNull(bindingCatalog.getBinding("slides_blueprint_generate"));
        assertNull(bindingCatalog.getBinding("slides_deck_generate"));
        assertNull(bindingCatalog.getBinding("spreadsheet_workbook_read"));
        assertNull(bindingCatalog.getBinding("slides_card_locate"));
        assertNull(bindingCatalog.getBinding("slides_card_structure_read"));
        assertNull(bindingCatalog.getBinding("slides_deck_text_structure_read"));
        assertNull(bindingCatalog.getBinding("slides_text_search"));
        assertNull(bindingCatalog.getBinding("slides_text_replace"));
        assertNull(bindingCatalog.getBinding("slides_card_delete"));
        assertNull(bindingCatalog.getBinding("slides_card_reorder"));
        assertNull(bindingCatalog.getBinding("slides_json_card_append"));
        assertNull(bindingCatalog.getBinding("slides_workspace_text_patch"));
        assertNull(bindingCatalog.getBinding("slides_authoring_model_read"));
        assertNull(bindingCatalog.getBinding("slides_authoring_model_update"));
        assertNull(bindingCatalog.getBinding("slides_page_debug_read"));
        assertNull(bindingCatalog.getBinding("slides_page_update"));
        assertNull(bindingCatalog.getBinding("slides_projected_markdown_read"));
        assertNull(bindingCatalog.getBinding("slides_build_preview"));
    }

    @Test
    void validatorRejectsUnknownFunction() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                validator.validateFunctionBinding(
                        new SkillFunctionBinding("unknown_function", SkillRuntimeType.NATIVE, null, null, null),
                        "test"));

        assertNotNull(ex.getMessage());
    }

    @Test
    void validatorRejectsMissingCoverage() {
        Map<String, SkillFunctionBinding> partialBindings = new HashMap<>();
        partialBindings.put("file_list",
                new SkillFunctionBinding("file_list", SkillRuntimeType.NATIVE, "file_list", null, null));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                validator.validateCoverage(partialBindings));

        assertNotNull(ex.getMessage());
    }
}
