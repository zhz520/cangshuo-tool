package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminToolRequest;
import com.cangshuo.toolbox.admin.model.AdminToolRow;
import com.cangshuo.toolbox.admin.repository.AdminToolRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminToolServiceTest {
    private final AdminToolRepository tools = mock(AdminToolRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.9", "/api/v1/admin/tools", "POST");
    private AdminToolService service;

    @BeforeEach void setup() {
        service = new AdminToolService(tools, audit, new AdminToolInput(mapper), mapper);
    }

    @Test void createRejectsDuplicateCodesAndUnknownCategoriesWithoutAuditing() {
        when(tools.existsByCode("demo")).thenReturn(true);
        assertEquals(ApiError.TOOL_CODE_EXISTS,
                assertThrows(ApiException.class, () -> service.create(request("demo"), context, 7L)).error());
        when(tools.existsByCode("demo")).thenReturn(false);
        when(tools.categoryUsable("DEV")).thenReturn(false);
        assertEquals(ApiError.CATEGORY_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.create(request("demo"), context, 7L)).error());
        verify(tools, never()).insert(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyBoolean(), anyString(), anyInt(), anyInt(), anyBoolean(), anyString());
        verifyNoInteractions(audit);
    }

    @Test void createNormalizesPayloadAndAudits() {
        when(tools.existsByCode("demo")).thenReturn(false);
        when(tools.categoryUsable("DEV")).thenReturn(true);
        when(tools.findByCode("demo")).thenReturn(Optional.of(row()));
        var response = service.create(request(" demo "), context, 7L);
        assertEquals("demo", response.code());
        assertEquals(List.of("a", "b"), response.keywords());
        verify(tools).insert(eq("demo"), eq("测试工具"), eq("说明"), eq("DEV"), eq("tools"), eq("[\"a\",\"b\"]"),
                eq("LOCAL"), eq(false), eq("ENABLED"), eq(1), eq(10), eq(false), eq("{\"k\":1}"));
        verify(audit).record(7L, "tool", "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void updateRequiresExistingCodeAndUsableCategory() {
        when(tools.findByCode("gone")).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.update("gone", request(null), context, 7L)).error());
        when(tools.findByCode("demo")).thenReturn(Optional.of(row()));
        when(tools.categoryUsable("DEV")).thenReturn(false);
        assertEquals(ApiError.CATEGORY_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.update("demo", request(null), context, 7L)).error());
        verify(tools, never()).update(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyBoolean(), anyString(), anyInt(), anyInt(), anyBoolean(), anyString());
        verify(tools, never()).insert(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyBoolean(), anyString(), anyInt(), anyInt(), anyBoolean(), anyString());
    }

    @Test void statusAndDeleteAuditAndMapMissingRowsToNotFound() {
        when(tools.updateStatus("demo", "DISABLED")).thenReturn(true);
        when(tools.findByCode("demo")).thenReturn(Optional.of(row()));
        service.updateStatus("demo", "disabled", context, 7L);
        verify(audit).record(7L, "tool", "STATUS_DISABLED", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(tools.updateStatus("gone", "ENABLED")).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.updateStatus("gone", "ENABLED", context, 7L)).error());
        when(tools.softDelete("demo")).thenReturn(true);
        service.delete("demo", context, 7L);
        verify(audit).record(7L, "tool", "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(tools.softDelete("gone")).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.delete("gone", context, 7L)).error());
    }

    @Test void listValidatesFiltersAndReturnsEmptyPages() {
        when(tools.count(null, null, null)).thenReturn(0L);
        assertEquals(0, service.list(1, 20, null, null, null).records().size());
        assertThrows(ApiException.class, () -> service.list(1, 20, "x".repeat(65), null, null));
        assertThrows(ApiException.class, () -> service.list(1, 20, null, "bad", null));
        assertThrows(ApiException.class, () -> service.list(1, 20, null, null, "RUNNING"));
    }

    @Test void detailHidesMissingToolsAndRejectsMalformedCodes() {
        when(tools.findByCode("gone")).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND, assertThrows(ApiException.class, () -> service.detail("gone")).error());
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.detail("BAD CODE")).error());
    }

    private static AdminToolRequest request(String code) {
        return new AdminToolRequest(code, "测试工具", "说明", "DEV", "tools", List.of("a", "a", "b"), "LOCAL",
                false, "ENABLED", 1, 10, false, "{\"k\":1}");
    }

    private static AdminToolRow row() {
        return new AdminToolRow(5, "demo", "测试工具", "说明", "DEV", "开发工具", "tools",
                "[\"a\",\"b\"]", "LOCAL", false, "ENABLED", 1, 10, false, "{\"k\":1}",
                Instant.parse("2026-10-05T08:00:00Z"));
    }
}
