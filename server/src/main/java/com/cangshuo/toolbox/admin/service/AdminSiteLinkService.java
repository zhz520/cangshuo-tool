package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminSiteLinkRow;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.response.PageResponse;
import com.cangshuo.toolbox.sitelink.model.SiteLinkRow;
import com.cangshuo.toolbox.sitelink.repository.SiteLinkRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSiteLinkService {

    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final int MAX_KEYWORD = 100;

    private final SiteLinkRepository repository;

    public AdminSiteLinkService(SiteLinkRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSiteLinkRow> list(int page, int pageSize, String status, String keyword) {
        String normalizedStatus = normalizeStatus(status, true);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && (normalizedKeyword.isEmpty() || normalizedKeyword.length() > MAX_KEYWORD)) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        long total = repository.count(normalizedStatus, normalizedKeyword);
        long offset = ((long) page - 1) * pageSize;
        List<AdminSiteLinkRow> records = offset >= total ? List.of()
                : repository.page(normalizedStatus, normalizedKeyword, pageSize, offset).stream()
                        .map(AdminSiteLinkService::toRow).toList();
        return new PageResponse<>(records, page, pageSize, total);
    }

    @Transactional
    public AdminSiteLinkRow update(long id, String status) {
        String normalized = normalizeStatus(status, false);
        if (!repository.updateStatus(id, normalized)) throw new ApiException(ApiError.NOT_FOUND);
        SiteLinkRow row = repository.find(id);
        if (row == null) throw new ApiException(ApiError.NOT_FOUND);
        return toRow(row);
    }

    private static String normalizeStatus(String status, boolean allowNull) {
        if (status == null || status.isBlank()) {
            if (allowNull) return null;
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new ApiException(ApiError.INVALID_ARGUMENT);
        return normalized;
    }

    private static AdminSiteLinkRow toRow(SiteLinkRow row) {
        return new AdminSiteLinkRow(row.id(), row.title(), row.url(), row.description(), row.status(),
                row.submittedBy(), row.createdAt().toString(), row.updatedAt().toString());
    }
}
