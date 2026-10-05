package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminOperationLogRow;
import com.cangshuo.toolbox.admin.repository.AdminOperationLogRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.response.PageResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Read-only view over the administrator audit trail written by AdminAuditService. */
@Service
public class AdminOperationLogService {
    private final AdminOperationLogRepository logs;
    public AdminOperationLogService(AdminOperationLogRepository logs) { this.logs = logs; }

    public PageResponse<AdminOperationLogRow> list(int page, int pageSize, String module, String result,
                                                   String keyword) {
        String normalizedModule = token(module, "module");
        String normalizedResult = token(result, "result");
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalizedKeyword != null && (normalizedKeyword.length() > 128
                || normalizedKeyword.chars().anyMatch(Character::isISOControl))) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        long offset = ((long) page - 1L) * pageSize;
        long total = logs.count(normalizedModule, normalizedResult, normalizedKeyword);
        List<AdminOperationLogRow> records = offset >= total ? List.of()
                : logs.find(normalizedModule, normalizedResult, normalizedKeyword, pageSize, offset);
        return new PageResponse<>(records, page, pageSize, total);
    }

    private static String token(String value, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32 || !normalized.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        return normalized;
    }
}
