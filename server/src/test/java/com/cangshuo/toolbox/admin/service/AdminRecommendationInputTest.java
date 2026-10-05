package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRequest;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminRecommendationInputTest {
    private final AdminRecommendationInput input = new AdminRecommendationInput();

    @Test void acceptsToolOrLinkSlotsAndParsesTheWindow() {
        var toolSlot = normalized(request(null, " calculator ", null, null, "2026-10-05T00:00:00Z", "2026-11-05T00:00:00Z"));
        assertEquals("CALC_DEMO", toolSlot.slotCode().toUpperCase());
        assertEquals("calculator", toolSlot.toolCode());
        assertNull(toolSlot.linkUrl());
        assertEquals("2026-10-05T00:00:00Z", toolSlot.startAt().toString());
        assertEquals("2026-11-05T00:00:00Z", toolSlot.endAt().toString());
        var linkSlot = normalized(request("https://example.com/activity", null, null, null, null, null));
        assertEquals("https://example.com/activity", linkSlot.linkUrl());
        assertNull(linkSlot.toolCode());
        assertTrue(linkSlot.enabled());
    }

    @Test void rejectsBothOrNeitherTargetsInvalidLinksAndBadWindows() {
        assertInvalid(() -> normalized(request(null, null, null, null, null, null)));
        assertInvalid(() -> normalized(request("https://example.com", "calculator", null, null, null, null)));
        assertInvalid(() -> normalized(request("http://example.com", null, null, null, null, null)));
        assertInvalid(() -> normalized(request("https://user:pass@example.com", null, null, null, null, null)));
        assertInvalid(() -> normalized(request("https://example.com", null, "https://bad image", null, null, null)));
        assertInvalid(() -> normalized(request(null, "calculator", null, null, "2026-11-05T00:00:00Z", "2026-10-05T00:00:00Z")));
        assertInvalid(() -> normalized(request(null, "calculator", null, null, "not-a-date", null)));
        assertInvalid(() -> normalized(request(null, "calculator", null, -1, null, null)));
        assertInvalid(() -> normalized(request(null, " ", null, null, null, null)));
    }

    private AdminRecommendationInput.Normalized normalized(AdminRecommendationRequest request) {
        return input.normalize(request, true);
    }

    private static AdminRecommendationRequest request(String linkUrl, String toolCode, String imageUrl,
            Integer sortOrder, String startAt, String endAt) {
        return new AdminRecommendationRequest("calC_demo", "推荐标题", "副标题", toolCode, linkUrl, imageUrl,
                sortOrder, null, startAt, endAt);
    }

    private static void assertInvalid(Runnable action) {
        assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, action::run).error());
    }
}
