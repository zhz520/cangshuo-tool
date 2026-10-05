package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminUserRow;
import com.cangshuo.toolbox.admin.model.AdminUserSessionResponse;
import com.cangshuo.toolbox.admin.model.AdminUserSyncEntryResponse;
import com.cangshuo.toolbox.admin.repository.AdminUserRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    private final AdminUserRepository users = mock(AdminUserRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.12", "/api/v1/admin/users/7/status", "PATCH");
    private AdminUserService service;

    @BeforeEach void setup() {
        service = new AdminUserService(users, audit);
    }

    @Test void listValidatesKeywordAndReturnsEmptyPagePastTheEnd() {
        when(users.count(null, null)).thenReturn(0L);
        assertEquals(0, service.list(1, 20, null, null).records().size());
        assertEquals(0, service.list(3, 20, null, null).records().size());
        assertThrows(ApiException.class, () -> service.list(1, 20, "x".repeat(129), null));
    }

    @Test void detailRejectsMalformedIdsAndHidesMissingUsers() {
        when(users.findById(7)).thenReturn(Optional.of(row(true)));
        assertEquals("user@example.com", service.detail("7").email());
        for (String bad : List.of("abc", "0", "-3", "")) {
            assertEquals(ApiError.INVALID_ARGUMENT, assertThrows(ApiException.class, () -> service.detail(bad)).error());
        }
        when(users.findById(8)).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND, assertThrows(ApiException.class, () -> service.detail("8")).error());
    }

    @Test void disablingRevokesSessionsAndAuditsWhileEnablingDoesNot() {
        when(users.findById(7)).thenReturn(Optional.of(row(true)));
        when(users.setEnabled(7, false)).thenReturn(true);
        service.updateStatus("7", false, context, 5L);
        verify(users).revokeSessions(7);
        verify(audit).record(5L, "user", "STATUS_DISABLED", context.uri(), context.method(), context.ip(), "SUCCESS");
        when(users.findById(7)).thenReturn(Optional.of(row(false)));
        when(users.setEnabled(7, true)).thenReturn(true);
        service.updateStatus("7", true, context, 5L);
        verify(users, times(1)).revokeSessions(7);
        verify(audit).record(5L, "user", "STATUS_ENABLED", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    @Test void repeatedStatusCallsAreIdempotentAndSkipAudit() {
        when(users.findById(7)).thenReturn(Optional.of(row(true)));
        service.updateStatus("7", true, context, 5L);
        verify(users, never()).setEnabled(anyLong(), anyBoolean());
        verify(users, never()).revokeSessions(anyLong());
        verifyNoInteractions(audit);
    }

    @Test void sessionsAndSyncEntriesAreBoundedAndRequireAnExistingUser() {
        when(users.findById(7)).thenReturn(Optional.of(row(true)));
        when(users.sessions(7)).thenReturn(List.of(new AdminUserSessionResponse(3, "abcd1234…",
                Instant.parse("2026-10-05T08:00:00Z"), Instant.parse("2026-11-04T08:00:00Z"), null,
                Instant.parse("2026-10-05T08:00:00Z"))));
        when(users.syncEntries(7, 20)).thenReturn(List.of(new AdminUserSyncEntryResponse("FAVORITE", "calculator",
                1791187200000L, false)));
        assertEquals(1, service.sessions("7").size());
        assertEquals("abcd1234…", service.sessions("7").get(0).sessionCodeMasked());
        assertEquals("calculator", service.syncEntries("7", 20).get(0).entityKey());
        assertThrows(ApiException.class, () -> service.syncEntries("7", 0));
        assertThrows(ApiException.class, () -> service.syncEntries("7", 51));
        when(users.findById(8)).thenReturn(Optional.empty());
        assertEquals(ApiError.NOT_FOUND, assertThrows(ApiException.class, () -> service.sessions("8")).error());
        assertEquals(ApiError.NOT_FOUND, assertThrows(ApiException.class, () -> service.syncEntries("8", 10)).error());
    }

    private static AdminUserRow row(boolean enabled) {
        return new AdminUserRow(7, "user@example.com", "用户", enabled, Instant.parse("2026-10-05T07:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-05T07:00:00Z"), 2, 3);
    }
}
