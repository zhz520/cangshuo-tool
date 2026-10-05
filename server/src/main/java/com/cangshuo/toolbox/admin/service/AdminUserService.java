package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminUserRow;
import com.cangshuo.toolbox.admin.model.AdminUserSessionResponse;
import com.cangshuo.toolbox.admin.model.AdminUserSyncEntryResponse;
import com.cangshuo.toolbox.admin.repository.AdminUserRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.response.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/** User administration: search, profile, enable/disable with session revocation, sessions and sync keys. */
@Service
public class AdminUserService {
    private static final String MODULE = "user";
    private final AdminUserRepository users;
    private final AdminAuditService audit;

    public AdminUserService(AdminUserRepository users, AdminAuditService audit) {
        this.users = users; this.audit = audit;
    }

    public PageResponse<AdminUserRow> list(int page, int pageSize, String keyword, Boolean enabled) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalizedKeyword != null && (normalizedKeyword.length() > 128
                || normalizedKeyword.chars().anyMatch(Character::isISOControl))) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        long offset = ((long) page - 1L) * pageSize;
        long total = users.count(normalizedKeyword, enabled);
        List<AdminUserRow> records = offset >= total ? List.of()
                : users.find(normalizedKeyword, enabled, pageSize, offset);
        return new PageResponse<>(records, page, pageSize, total);
    }

    public AdminUserRow detail(String rawId) {
        return users.findById(id(rawId)).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }

    /** Disabling revokes refresh sessions, which also invalidates their access tokens via the sid check. */
    public AdminUserRow updateStatus(String rawId, boolean enabled, AdminRequestContext context, Long adminId) {
        long id = id(rawId);
        AdminUserRow current = users.findById(id).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
        if (current.enabled() == enabled) return current;
        if (!users.setEnabled(id, enabled)) throw new ApiException(ApiError.NOT_FOUND);
        if (!enabled) users.revokeSessions(id);
        audit.record(adminId, MODULE, enabled ? "STATUS_ENABLED" : "STATUS_DISABLED", context.uri(),
                context.method(), context.ip(), "SUCCESS");
        return detail(rawId);
    }

    public List<AdminUserSessionResponse> sessions(String rawId) {
        long id = id(rawId);
        if (users.findById(id).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        return users.sessions(id);
    }

    public List<AdminUserSyncEntryResponse> syncEntries(String rawId, int limit) {
        if (limit < 1 || limit > 50) throw new ApiException(ApiError.INVALID_ARGUMENT);
        long id = id(rawId);
        if (users.findById(id).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        return users.syncEntries(id, limit);
    }

    private static long id(String rawId) {
        try {
            long value = Long.parseLong(rawId);
            if (value <= 0) throw new ApiException(ApiError.INVALID_ARGUMENT);
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
    }
}
