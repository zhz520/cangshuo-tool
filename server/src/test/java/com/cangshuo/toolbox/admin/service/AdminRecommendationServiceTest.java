package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRequest;
import com.cangshuo.toolbox.admin.model.AdminRecommendationRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminRecommendationRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminRecommendationServiceTest {
    private final AdminRecommendationRepository slots = mock(AdminRecommendationRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.11", "/api/v1/admin/recommendations", "POST");
    private AdminRecommendationService service;

    @BeforeEach void setup() {
        service = new AdminRecommendationService(slots, audit, new AdminRecommendationInput());
    }

    @Test void createRejectsDuplicateCodesAndUnknownTools() {
        when(slots.existsByCode("demo")).thenReturn(true);
        assertEquals(ApiError.RECOMMENDATION_CODE_EXISTS,
                assertThrows(ApiException.class, () -> service.create(toolRequest("demo"), context, 7L)).error());
        when(slots.existsByCode("demo")).thenReturn(false);
        when(slots.toolExists("calculator")).thenReturn(false);
        assertEquals(ApiError.RECOMMENDATION_TOOL_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.create(toolRequest("demo"), context, 7L)).error());
        verify(slots, never()).insert(anyString(), anyString(), anyString(), any(), any(), any(), anyInt(),
                anyBoolean(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test void createInsertsAndAuditsToolAndLinkSlots() {
        when(slots.existsByCode("demo")).thenReturn(false);
        when(slots.toolExists("calculator")).thenReturn(true);
        when(slots.findByCode("demo")).thenReturn(Optional.of(row()));
        service.create(toolRequest("demo"), context, 7L);
        verify(slots).insert("demo", "推荐标题", "副标题", "calculator", null, null, 10, true, null, null);
        verify(audit).record(7L, "recommendation", "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(slots.existsByCode("linkdemo")).thenReturn(false);
        when(slots.findByCode("linkdemo")).thenReturn(Optional.of(row()));
        service.create(linkRequest("linkdemo"), context, 7L);
        verify(slots).insert("linkdemo", "推荐标题", "副标题", null, "https://example.com/activity", null, 10, true,
                null, null);
    }

    @Test void updateStatusAndDeleteMapMissingSlotsToNotFoundAndAuditSuccess() {
        when(slots.findByCode("demo")).thenReturn(Optional.of(row()));
        when(slots.toolExists("calculator")).thenReturn(true);
        when(slots.update(eq("demo"), anyString(), anyString(), any(), any(), any(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(true);
        service.update("demo", toolRequest(null), context, 7L);
        verify(audit).record(7L, "recommendation", "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(slots.updateStatus("demo", false)).thenReturn(true);
        service.updateStatus("demo", false, context, 7L);
        verify(audit).record(7L, "recommendation", "STATUS_DISABLED", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(slots.softDelete("demo")).thenReturn(true);
        service.delete("demo", context, 7L);
        verify(audit).record(7L, "recommendation", "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(slots.softDelete("gone")).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.delete("gone", context, 7L)).error());
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.delete("BAD CODE", context, 7L)).error());
    }

    private static AdminRecommendationRequest toolRequest(String code) {
        return new AdminRecommendationRequest(code, " 推荐标题 ", " 副标题 ", "calculator", null, null, 10, true, null, null);
    }

    private static AdminRecommendationRequest linkRequest(String code) {
        return new AdminRecommendationRequest(code, " 推荐标题 ", " 副标题 ", null, "https://example.com/activity", null, 10, true, null, null);
    }

    private static AdminRecommendationRow row() {
        return new AdminRecommendationRow(4, "demo", "推荐标题", "副标题", "calculator", null, null, 10, true,
                null, null, true, Instant.parse("2026-10-05T08:00:00Z"));
    }
}
