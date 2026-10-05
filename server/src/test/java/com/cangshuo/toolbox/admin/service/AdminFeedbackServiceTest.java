package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminFeedbackUpdateRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.feedback.repository.FeedbackRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminFeedbackServiceTest {
    private final FeedbackRepository feedback = mock(FeedbackRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.14", "/api/v1/admin/feedback/3", "PATCH");
    private AdminFeedbackService service;

    @BeforeEach void setup() {
        service = new AdminFeedbackService(feedback, audit);
    }

    @Test void listValidatesFiltersAndReturnsEmptyPages() {
        when(feedback.countForAdmin(null, null)).thenReturn(0L);
        assertEquals(0, service.list(1, 20, null, null).records().size());
        assertEquals(0, service.list(5, 20, null, null).records().size());
        assertThrows(ApiException.class, () -> service.list(1, 20, "x".repeat(129), null));
        assertThrows(ApiException.class, () -> service.list(1, 20, null, "DONE"));
    }

    @Test void updateValidatesStatusAndReplyThenAudits() {
        when(feedback.findOwner(3L)).thenReturn(Optional.of(9L));
        when(feedback.update(3L, "PROCESSING", null)).thenReturn(true);
        when(feedback.findForAdminById(3L)).thenReturn(Optional.of(view("PROCESSING", null)));
        var statusOnly = service.update(3L, new AdminFeedbackUpdateRequest("processing", null), context, 7L);
        assertEquals("PROCESSING", statusOnly.status());
        verify(audit).record(7L, "feedback", "STATUS_PROCESSING", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(feedback.update(3L, "RESOLVED", "已修复，请更新版本")).thenReturn(true);
        when(feedback.findForAdminById(3L)).thenReturn(Optional.of(view("RESOLVED", "已修复，请更新版本")));
        var replied = service.update(3L, new AdminFeedbackUpdateRequest("RESOLVED", " 已修复，请更新版本 "), context, 7L);
        assertEquals("已修复，请更新版本", replied.reply());
        verify(audit).record(7L, "feedback", "REPLY", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void updateRejectsUnknownStatusReplyNoiseAndMissingRows() {
        assertThrows(ApiException.class,
                () -> service.update(3L, new AdminFeedbackUpdateRequest("DONE", null), context, 7L));
        assertThrows(ApiException.class,
                () -> service.update(3L, new AdminFeedbackUpdateRequest("RESOLVED", "x\u0000y"), context, 7L));
        assertThrows(ApiException.class,
                () -> service.update(3L, new AdminFeedbackUpdateRequest("RESOLVED", "x".repeat(2001)), context, 7L));
        when(feedback.findOwner(3L)).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.update(3L, new AdminFeedbackUpdateRequest("RESOLVED", null), context, 7L)).error());
        verifyNoInteractions(audit);
    }

    private static FeedbackRepository.AdminFeedbackRowView view(String status, String reply) {
        return new FeedbackRepository.AdminFeedbackRowView(3L, 9L, "user@example.com", "BUG", "内容", null, status,
                reply, reply == null ? null : Instant.parse("2026-10-05T09:00:00Z"),
                Instant.parse("2026-10-05T08:00:00Z"), Instant.parse("2026-10-05T09:00:00Z"));
    }
}
