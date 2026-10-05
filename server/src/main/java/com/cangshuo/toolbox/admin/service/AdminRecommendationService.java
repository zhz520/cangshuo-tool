package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminRecommendationRequest;
import com.cangshuo.toolbox.admin.model.AdminRecommendationRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminRecommendationRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

/** Home recommendation slots: tool references or https links with an optional active window. */
@Service
public class AdminRecommendationService {
    private static final String MODULE = "recommendation";
    private final AdminRecommendationRepository slots;
    private final AdminAuditService audit;
    private final AdminRecommendationInput input;

    public AdminRecommendationService(AdminRecommendationRepository slots, AdminAuditService audit,
                                      AdminRecommendationInput input) {
        this.slots = slots; this.audit = audit; this.input = input;
    }

    public List<AdminRecommendationRow> list() { return slots.list(); }

    public AdminRecommendationRow create(AdminRecommendationRequest request, AdminRequestContext context, Long adminId) {
        var normalized = input.normalize(request, true);
        if (slots.existsByCode(normalized.slotCode())) throw new ApiException(ApiError.RECOMMENDATION_CODE_EXISTS);
        requireTool(normalized.toolCode());
        slots.insert(normalized.slotCode(), normalized.title(), normalized.subtitle(), normalized.toolCode(),
                normalized.linkUrl(), normalized.imageUrl(), normalized.sortOrder(), normalized.enabled(),
                normalized.startAt(), normalized.endAt());
        audit.record(adminId, MODULE, "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(normalized.slotCode());
    }

    public AdminRecommendationRow update(String rawCode, AdminRecommendationRequest request,
            AdminRequestContext context, Long adminId) {
        String code = input.slotCode(rawCode);
        if (slots.findByCode(code).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        var normalized = input.normalize(request, false);
        requireTool(normalized.toolCode());
        if (!slots.update(code, normalized.title(), normalized.subtitle(), normalized.toolCode(),
                normalized.linkUrl(), normalized.imageUrl(), normalized.sortOrder(), normalized.enabled(),
                normalized.startAt(), normalized.endAt())) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
        audit.record(adminId, MODULE, "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public AdminRecommendationRow updateStatus(String rawCode, boolean enabled, AdminRequestContext context,
                                               Long adminId) {
        String code = input.slotCode(rawCode);
        if (!slots.updateStatus(code, enabled)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, enabled ? "STATUS_ENABLED" : "STATUS_DISABLED", context.uri(),
                context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public void delete(String rawCode, AdminRequestContext context, Long adminId) {
        String code = input.slotCode(rawCode);
        if (!slots.softDelete(code)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private void requireTool(String toolCode) {
        if (toolCode != null && !slots.toolExists(toolCode)) {
            throw new ApiException(ApiError.RECOMMENDATION_TOOL_NOT_FOUND);
        }
    }

    private AdminRecommendationRow detail(String code) {
        return slots.findByCode(code).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }
}
