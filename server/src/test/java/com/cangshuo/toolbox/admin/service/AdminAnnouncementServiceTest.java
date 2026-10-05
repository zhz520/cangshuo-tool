package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAnnouncementRequest;
import com.cangshuo.toolbox.admin.model.AdminAnnouncementRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminAnnouncementRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAnnouncementServiceTest {
    private final AdminAnnouncementRepository announcements = mock(AdminAnnouncementRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminAnnouncementInput input = new AdminAnnouncementInput();
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.13", "/api/v1/admin/announcements", "POST");
    private AdminAnnouncementService service;

    @BeforeEach void setup() {
        service = new AdminAnnouncementService(announcements, audit, input);
    }

    @Test void inputAcceptsMultilineBodiesAndRejectsMalformedValues() {
        var normalized = input.normalize(new AdminAnnouncementRequest(" 维护通知 ", " 第一行\n第二行 ", "warning",
                null, "2026-11-05T00:00:00Z", "2026-11-06T00:00:00Z"));
        assertEquals("维护通知", normalized.title());
        assertEquals("第一行\n第二行", normalized.body());
        assertEquals("WARNING", normalized.level());
        assertTrue(normalized.published());
        assertEquals(Instant.parse("2026-11-05T00:00:00Z"), normalized.startAt());
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标题", "正文", "LOUD",
                null, null, null)));
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标\n题", "正文", null,
                null, null, null)));
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标题", "正\u0000文", null,
                null, null, null)));
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标题", "正文", null,
                null, "2026-11-06T00:00:00Z", "2026-11-05T00:00:00Z")));
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标题", "正文", null,
                null, "bad-date", null)));
        assertThrows(ApiException.class, () -> input.normalize(new AdminAnnouncementRequest("标题", " ", null,
                null, null, null)));
        assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, () -> input.id("abc")).error());
        assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, () -> input.id("0")).error());
    }

    @Test void createReturnsTheStoredRowAndAudits() {
        when(announcements.insert("维护通知", "第一行\n第二行", "WARNING", false, null, null)).thenReturn(11L);
        when(announcements.findById(11L)).thenReturn(Optional.of(row(false)));
        var created = service.create(new AdminAnnouncementRequest(" 维护通知 ", " 第一行\n第二行 ", "warning", false,
                null, null), context, 7L);
        assertEquals(11L, created.id());
        assertFalse(created.published());
        verify(audit).record(7L, "announcement", "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void publishUnpublishAreIdempotentAndAudited() {
        when(announcements.findById(11L)).thenReturn(Optional.of(row(false)));
        service.updateStatus("11", false, context, 7L);
        verify(announcements, never()).updateStatus(anyLong(), anyBoolean());
        verify(audit, never()).record(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        when(announcements.updateStatus(11L, true)).thenReturn(true);
        service.updateStatus("11", true, context, 7L);
        verify(audit).record(7L, "announcement", "PUBLISH", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(announcements.findById(11L)).thenReturn(Optional.of(row(true)));
        when(announcements.updateStatus(11L, false)).thenReturn(true);
        service.updateStatus("11", false, context, 7L);
        verify(audit).record(7L, "announcement", "UNPUBLISH", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void updateAndDeleteMapMissingRowsToNotFound() {
        when(announcements.findById(11L)).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.update("11", request(), context, 7L)).error());
        when(announcements.softDelete(11L)).thenReturn(false);
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.delete("11", context, 7L)).error());
        when(announcements.softDelete(11L)).thenReturn(true);
        service.delete("11", context, 7L);
        verify(audit).record(7L, "announcement", "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private static AdminAnnouncementRequest request() {
        return new AdminAnnouncementRequest("维护通知", "第一行\n第二行", "INFO", true, null, null);
    }

    private static AdminAnnouncementRow row(boolean published) {
        return new AdminAnnouncementRow(11L, "维护通知", "第一行\n第二行", "WARNING", published, null, null,
                published, Instant.parse("2026-10-05T08:00:00Z"), Instant.parse("2026-10-05T08:00:00Z"));
    }
}
