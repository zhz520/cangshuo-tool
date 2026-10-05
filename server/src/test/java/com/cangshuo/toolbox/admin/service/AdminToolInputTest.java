package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminToolRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminToolInputTest {
    private final AdminToolInput input = new AdminToolInput(new ObjectMapper());

    @Test void normalizesCodesTextKeywordsAndDefaults() {
        var normalized = input.normalize(new AdminToolRequest(" demo ", " 名称 ", " 说明 ", "DEV", " tools ",
                List.of(" a ", "a", "b"), "local", null, null, null, null, null, null), true);
        assertEquals("demo", normalized.code());
        assertEquals("名称", normalized.name());
        assertEquals("说明", normalized.description());
        assertEquals("DEV", normalized.categoryCode());
        assertEquals("tools", normalized.icon());
        assertEquals("[\"a\",\"b\"]", normalized.keywordsJson());
        assertEquals("LOCAL", normalized.mode());
        assertEquals("ENABLED", normalized.status());
        assertEquals(1, normalized.version());
        assertEquals(0, normalized.sortOrder());
        assertFalse(normalized.requiresLogin());
        assertFalse(normalized.featured());
        assertEquals("{}", normalized.configJson());
    }

    @Test void acceptsObjectConfigurationAndFlags() {
        var normalized = input.normalize(new AdminToolRequest("demo", "n", "", "DEV", null, null, "WEB", true,
                null, 2, 5, true, "{\"path\":\"/tools/demo/\"}"), true);
        assertEquals("WEB", normalized.mode());
        assertEquals("{\"path\":\"/tools/demo/\"}", normalized.configJson());
        assertTrue(normalized.requiresLogin());
        assertTrue(normalized.featured());
        assertEquals(2, normalized.version());
        assertEquals(5, normalized.sortOrder());
    }

    @Test void rejectsInvalidCodesModesStatusesAndLimits() {
        assertInvalid(() -> input.code("Bad"));
        assertInvalid(() -> input.code("-x"));
        assertInvalid(() -> input.code("a".repeat(65)));
        assertInvalid(() -> normalized("demo", "n", "", "dev", null, null, "LOCAL", null, null, null, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "REMOTE", null, null, null, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, "RUNNING", null, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, null, 0, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, null, null, -1, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null,
                IntStream.range(0, 21).mapToObj(index -> "k" + index).toList(), "LOCAL", null, null, null, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, List.of("k".repeat(33)), "LOCAL", null, null, null, null, null, null));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, null, null, null, null, "[1]"));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, null, null, null, null, "{bad"));
        assertInvalid(() -> normalized("demo", " ", "", "DEV", null, null, "LOCAL", null, null, null, null, null, null));
        assertInvalid(() -> normalized(null, "n", "", "DEV", null, null, "LOCAL", null, null, null, null, null, null));
        assertInvalid(() -> input.status("RUNNING"));
        assertInvalid(() -> normalized("demo", "n", "", "DEV", null, null, "LOCAL", null, null, null, null, null,
                "{\"a\":\"" + "x".repeat(11_000) + "\"}"));
    }

    @Test void statusNormalizationIsCaseInsensitive() {
        assertEquals("MAINTENANCE", input.status(" maintenance "));
        assertEquals("DISABLED", input.status("Disabled"));
    }

    private AdminToolInput.Normalized normalized(String code, String name, String description, String category,
            String icon, List<String> keywords, String mode, Boolean requiresLogin, String status, Integer version,
            Integer sortOrder, Boolean featured, String config) {
        return input.normalize(new AdminToolRequest(code, name, description, category, icon, keywords, mode,
                requiresLogin, status, version, sortOrder, featured, config), true);
    }

    private static void assertInvalid(Runnable action) {
        assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, action::run).error());
    }
}
