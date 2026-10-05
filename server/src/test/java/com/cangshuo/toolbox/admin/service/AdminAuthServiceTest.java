package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAccount;
import com.cangshuo.toolbox.admin.model.AdminLoginRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminAccountRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {
    private final AdminAccountRepository admins = mock(AdminAccountRepository.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminTokenService tokens = mock(AdminTokenService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final AdminRequestContext context = new AdminRequestContext("203.0.113.7", "/api/v1/admin/auth/login", "POST");
    private AdminAuthService service;

    @BeforeEach void setup() {
        service = new AdminAuthService(admins, audit, new AdminLoginGuard(Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ZoneOffset.UTC)),
                tokens, passwords);
    }

    @Test void validLoginRecordsTimestampAuditAndIssuesToken() {
        var account = new AdminAccount(5, "root", passwords.encode("password12"), "管理员", "SUPER_ADMIN", 1);
        when(admins.findByUsername("root")).thenReturn(Optional.of(account));
        service.login(new AdminLoginRequest(" ROOT ", "password12"), context);
        verify(admins).recordLogin(5);
        verify(audit).record(5L, "admin", "LOGIN", context.uri(), context.method(), context.ip(), "SUCCESS");
        verify(tokens).issue(account);
    }

    @Test void wrongUnknownAndDisabledAccountsShareOneFailureCode() {
        var request = new AdminLoginRequest("root", "password12");
        when(admins.findByUsername("root")).thenReturn(Optional.empty());
        assertEquals(ApiError.ADMIN_INVALID_CREDENTIALS,
                assertThrows(ApiException.class, () -> service.login(request, context)).error());
        verify(audit).record(isNull(), eq("admin"), eq("LOGIN"), eq(context.uri()), eq(context.method()), eq(context.ip()), eq("FAILED"));
        when(admins.findByUsername("root")).thenReturn(Optional.of(
                new AdminAccount(5, "root", passwords.encode("different12"), "管理员", "ADMIN", 1)));
        assertEquals(ApiError.ADMIN_INVALID_CREDENTIALS,
                assertThrows(ApiException.class, () -> service.login(request, context)).error());
        verify(audit).record(5L, "admin", "LOGIN", context.uri(), context.method(), context.ip(), "FAILED");
        when(admins.findByUsername("root")).thenReturn(Optional.of(
                new AdminAccount(5, "root", passwords.encode("password12"), "管理员", "ADMIN", 0)));
        assertEquals(ApiError.ADMIN_INVALID_CREDENTIALS,
                assertThrows(ApiException.class, () -> service.login(request, context)).error());
        verifyNoInteractions(tokens);
        verify(admins, never()).recordLogin(anyLong());
    }

    @Test void fifthFailureLocksAccountAndAddressBeforeLookup() {
        when(admins.findByUsername("root")).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertEquals(ApiError.ADMIN_INVALID_CREDENTIALS,
                    assertThrows(ApiException.class, () -> service.login(new AdminLoginRequest("root", "password12"), context)).error());
        }
        assertEquals(ApiError.TOO_MANY_REQUESTS,
                assertThrows(ApiException.class, () -> service.login(new AdminLoginRequest("root", "password12"), context)).error());
        verify(admins, times(5)).findByUsername("root");
        verify(audit, times(5)).record(isNull(), eq("admin"), anyString(), anyString(), anyString(), anyString(), eq("FAILED"));
    }

    @Test void successfulLoginClearsFailureCounters() {
        var account = new AdminAccount(5, "root", passwords.encode("password12"), "管理员", "SUPER_ADMIN", 1);
        when(admins.findByUsername("root")).thenReturn(Optional.of(account));
        var wrong = new AdminLoginRequest("root", "wrongpassword");
        assertThrows(ApiException.class, () -> service.login(wrong, context));
        assertThrows(ApiException.class, () -> service.login(wrong, context));
        service.login(new AdminLoginRequest("root", "password12"), context);
        when(admins.findByUsername("root")).thenReturn(Optional.empty());
        for (int i = 0; i < 4; i++) {
            assertEquals(ApiError.ADMIN_INVALID_CREDENTIALS,
                    assertThrows(ApiException.class, () -> service.login(wrong, context)).error());
        }
        assertThrows(ApiException.class, () -> service.login(wrong, context));
        assertEquals(ApiError.TOO_MANY_REQUESTS,
                assertThrows(ApiException.class, () -> service.login(wrong, context)).error());
    }

    @Test void currentRequiresActiveAdministratorAndRedactsSensitiveRecords() {
        when(admins.findById(9)).thenReturn(Optional.of(new AdminAccount(9, "root", "h", "管理员", "ADMIN", 1)));
        assertEquals("root", service.current("9").username());
        assertThrows(ApiException.class, () -> service.current("not-a-number"));
        when(admins.findById(9)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.current("9"));
        when(admins.findById(9)).thenReturn(Optional.of(new AdminAccount(9, "root", "h", "管理员", "ADMIN", 0)));
        assertThrows(ApiException.class, () -> service.current("9"));
        assertFalse(new AdminLoginRequest("root", "secret12").toString().contains("secret12"));
    }

    @Test void logoutAuditsKnownAndUnknownSubjectsWithoutThrowing() {
        when(admins.findById(9)).thenReturn(Optional.of(new AdminAccount(9, "root", "h", "管理员", "ADMIN", 1)));
        service.logout("9", context);
        verify(audit).record(9L, "admin", "LOGOUT", context.uri(), context.method(), context.ip(), "SUCCESS");
        service.logout("broken", context);
        verify(audit).record(isNull(), eq("admin"), eq("LOGOUT"), anyString(), anyString(), anyString(), eq("UNKNOWN"));
    }
}
