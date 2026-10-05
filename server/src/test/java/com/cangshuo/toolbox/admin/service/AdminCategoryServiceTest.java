package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminCategoryRequest;
import com.cangshuo.toolbox.admin.model.AdminCategoryRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminCategoryRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminCategoryServiceTest {
    private final AdminCategoryRepository categories = mock(AdminCategoryRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.10", "/api/v1/admin/categories", "POST");
    private AdminCategoryService service;

    @BeforeEach void setup() {
        service = new AdminCategoryService(categories, audit, new AdminCategoryInput());
    }

    @Test void createRejectsDuplicateCodesWithoutAuditing() {
        when(categories.existsByCode("DEV")).thenReturn(true);
        assertEquals(ApiError.CATEGORY_CODE_EXISTS,
                assertThrows(ApiException.class, () -> service.create(request("dev"), context, 7L)).error());
        verify(categories, never()).insert(anyString(), anyString(), any(), anyString(), anyInt(), anyBoolean());
        verifyNoInteractions(audit);
    }

    @Test void createNormalizesAndAudits() {
        when(categories.existsByCode("DEV")).thenReturn(false);
        when(categories.findByCode("DEV")).thenReturn(Optional.of(row(0)));
        var created = service.create(request("dev"), context, 7L);
        assertEquals("DEV", created.code());
        verify(categories).insert("DEV", "开发工具", "code", "说明", 30, true);
        verify(audit).record(7L, "category", "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void updateRequiresExistingCategoryAndAudits() {
        when(categories.findByCode("GONE")).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.update("gone", request(null), context, 7L)).error());
        when(categories.findByCode("DEV")).thenReturn(Optional.of(row(0)));
        when(categories.update("DEV", "开发工具", "code", "说明", 30, true)).thenReturn(true);
        service.update("dev", request(null), context, 7L);
        verify(audit).record(7L, "category", "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void statusAndDeleteGuardCategoriesWithTools() {
        when(categories.updateStatus("DEV", false)).thenReturn(true);
        when(categories.findByCode("DEV")).thenReturn(Optional.of(row(3)));
        service.updateStatus("dev", false, context, 7L);
        verify(audit).record(7L, "category", "STATUS_DISABLED", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(categories.countTools("DEV")).thenReturn(3L);
        assertEquals(ApiError.CATEGORY_IN_USE,
                assertThrows(ApiException.class, () -> service.delete("dev", context, 7L)).error());
        verify(categories, never()).softDelete(anyString());
        when(categories.findByCode("EMPTY")).thenReturn(Optional.of(row(0)));
        when(categories.countTools("EMPTY")).thenReturn(0L);
        when(categories.softDelete("EMPTY")).thenReturn(true);
        service.delete("empty", context, 7L);
        verify(audit).record(7L, "category", "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void missingCategoriesReturnNotFoundOnStatusAndDelete() {
        when(categories.updateStatus("GONE", true)).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.updateStatus("gone", true, context, 7L)).error());
        when(categories.findByCode("GONE")).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.delete("gone", context, 7L)).error());
        assertEquals(ApiError.INVALID_ARGUMENT,
                assertThrows(ApiException.class, () -> service.delete("bad code", context, 7L)).error());
    }

    private static AdminCategoryRequest request(String code) {
        return new AdminCategoryRequest(code, " 开发工具 ", " 说明 ", "code", 30, true);
    }

    private static AdminCategoryRow row(int toolCount) {
        return new AdminCategoryRow(2, "DEV", "开发工具", "code", "说明", 30, true, toolCount,
                Instant.parse("2026-10-05T08:00:00Z"));
    }
}
