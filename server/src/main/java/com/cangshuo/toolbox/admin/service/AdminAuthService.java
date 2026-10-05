package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAccount;
import com.cangshuo.toolbox.admin.model.AdminAuthResponse;
import com.cangshuo.toolbox.admin.model.AdminLoginRequest;
import com.cangshuo.toolbox.admin.model.AdminProfileResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminAccountRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final AdminAccountRepository admins;
    private final AdminAuditService audit;
    private final AdminLoginGuard guard;
    private final AdminTokenService tokens;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AdminAuthService(AdminAccountRepository admins, AdminAuditService audit, AdminLoginGuard guard,
                            AdminTokenService tokens, PasswordEncoder passwords) {
        this.admins = admins; this.audit = audit; this.guard = guard; this.tokens = tokens; this.passwords = passwords;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /**
     * Intentionally not transactional: the timestamp update and the independently committed audit row
     * are single statements, and wrapping them in one transaction would make the audit insert's foreign
     * key wait on this transaction's row lock until the JDBC socket times out.
     */
    public AdminAuthResponse login(AdminLoginRequest request, AdminRequestContext context) {
        String username = AdminInput.username(request.username());
        AdminInput.password(request.password());
        String accountKey = "u:" + username;
        String ipKey = "i:" + context.ip();
        if (guard.locked(accountKey) || guard.locked(ipKey)) throw new ApiException(ApiError.TOO_MANY_REQUESTS);
        var account = admins.findByUsername(username);
        boolean matches = passwords.matches(request.password(), account.map(AdminAccount::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches || account.get().status() != 1) {
            guard.recordFailure(accountKey);
            guard.recordFailure(ipKey);
            audit.record(account.map(AdminAccount::id).orElse(null), "admin", "LOGIN", context.uri(), context.method(),
                    context.ip(), "FAILED");
            throw new ApiException(ApiError.ADMIN_INVALID_CREDENTIALS);
        }
        guard.clear(accountKey);
        guard.clear(ipKey);
        admins.recordLogin(account.get().id());
        audit.record(account.get().id(), "admin", "LOGIN", context.uri(), context.method(), context.ip(), "SUCCESS");
        return tokens.issue(account.get());
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse current(String subject) {
        return admins.findById(subjectId(subject)).filter(account -> account.status() == 1)
                .orElseThrow(() -> new ApiException(ApiError.UNAUTHENTICATED)).publicProfile();
    }

    public void logout(String subject, AdminRequestContext context) {
        Long id = null;
        try { id = Long.parseLong(subject); } catch (NumberFormatException ignored) { }
        var account = id == null ? java.util.Optional.<AdminAccount>empty() : admins.findById(id);
        audit.record(account.map(AdminAccount::id).orElse(null), "admin", "LOGOUT", context.uri(), context.method(),
                context.ip(), account.isPresent() ? "SUCCESS" : "UNKNOWN");
    }

    private static long subjectId(String subject) {
        try { return Long.parseLong(subject); }
        catch (NumberFormatException exception) { throw new ApiException(ApiError.UNAUTHENTICATED); }
    }
}
