package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminCategoryRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminCategoryInputTest {
    private final AdminCategoryInput input = new AdminCategoryInput();

    @Test void normalizesCodesTextAndDefaults() {
        var normalized = input.normalize(new AdminCategoryRequest(" dev ", " 开发工具 ", " 说明 ", " code ",
                null, null), true);
        assertEquals("DEV", normalized.code());
        assertEquals("开发工具", normalized.name());
        assertEquals("说明", normalized.description());
        assertEquals("code", normalized.icon());
        assertEquals(0, normalized.sortOrder());
        assertTrue(normalized.enabled());
        assertFalse(input.normalize(new AdminCategoryRequest("DEV", "n", "", null, 5, false), true).enabled());
        assertEquals(5, input.normalize(new AdminCategoryRequest("DEV", "n", "", null, 5, false), true).sortOrder());
    }

    @Test void rejectsMalformedCodesIconsNamesAndOrder() {
        assertInvalid(() -> input.code("1DEV"));
        assertInvalid(() -> input.code("D E V"));
        assertInvalid(() -> input.code("D".repeat(33)));
        assertInvalid(() -> input.normalize(new AdminCategoryRequest("DEV", " ", "", null, null, null), true));
        assertInvalid(() -> input.normalize(new AdminCategoryRequest("DEV", "n", "", "Bad-Icon", null, null), true));
        assertInvalid(() -> input.normalize(new AdminCategoryRequest("DEV", "n", "", null, -1, null), true));
        assertInvalid(() -> input.normalize(new AdminCategoryRequest("DEV", "n", "", null, 100_001, null), true));
        assertInvalid(() -> input.normalize(new AdminCategoryRequest(null, "n", "", null, null, null), true));
    }

    private static void assertInvalid(Runnable action) {
        assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, action::run).error());
    }
}
