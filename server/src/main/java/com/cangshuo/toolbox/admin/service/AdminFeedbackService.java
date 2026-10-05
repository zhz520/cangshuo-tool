package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminFeedbackRow;
import com.cangshuo.toolbox.admin.model.AdminFeedbackUpdateRequest;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.response.PageResponse;
import com.cangshuo.toolbox.feedback.repository.FeedbackRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Feedback triage: status filters, keyword search and one bounded reply per item. */
@Service
public class AdminFeedbackService {
    public static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "RESOLVED");
    private static final String MODULE = "feedback";
    private final FeedbackRepository feedback;
    private final AdminAuditService audit;

    public AdminFeedbackService(FeedbackRepository feedback, AdminAuditService audit) {
        this.feedback = feedback; this.audit = audit;
    }

    public PageResponse<AdminFeedbackRow> list(int page, int pageSize, String keyword, String status) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalizedKeyword != null && (normalizedKeyword.length() > 128
                || normalizedKeyword.chars().anyMatch(Character::isISOControl))) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        String normalizedStatus = status == null || status.isBlank() ? null : status(status);
        long offset = ((long) page - 1L) * pageSize;
        long total = feedback.countForAdmin(normalizedKeyword, normalizedStatus);
        List<AdminFeedbackRow> records = offset >= total ? List.of()
                : feedback.findForAdmin(normalizedKeyword, normalizedStatus, pageSize, offset).stream()
                        .map(AdminFeedbackService::toResponse).toList();
        return new PageResponse<>(records, page, pageSize, total);
    }

    public AdminFeedbackRow update(long id, AdminFeedbackUpdateRequest request, AdminRequestContext context,
                                   Long adminId) {
        String status = status(request.status());
        String reply = reply(request.reply());
        if (feedback.findOwner(id).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        if (!feedback.update(id, status, reply)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, reply == null ? "STATUS_" + status : "REPLY", context.uri(), context.method(),
                context.ip(), "SUCCESS");
        return feedback.findForAdminById(id).map(AdminFeedbackService::toResponse)
                .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }

    private static String status(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new ApiException(ApiError.INVALID_ARGUMENT);
        return normalized;
    }

    private static String reply(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > 2000) throw new ApiException(ApiError.INVALID_ARGUMENT);
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\n') continue;
            if (Character.isISOControl(character)) throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        return normalized;
    }

    private static AdminFeedbackRow toResponse(FeedbackRepository.AdminFeedbackRowView view) {
        return new AdminFeedbackRow(view.id(), view.userId(), view.userEmail(), view.type(), view.content(),
                view.contact(), view.status(), view.reply(), view.repliedAt(), view.createdAt(), view.updatedAt());
    }
}
