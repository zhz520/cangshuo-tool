package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminCategoryRequest;
import com.cangshuo.toolbox.admin.model.AdminCategoryRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminCategoryRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

/** Category management; deleting a category that still owns tools is rejected instead of orphaning them. */
@Service
public class AdminCategoryService {
    private static final String MODULE = "category";
    private final AdminCategoryRepository categories;
    private final AdminAuditService audit;
    private final AdminCategoryInput input;

    public AdminCategoryService(AdminCategoryRepository categories, AdminAuditService audit,
                                AdminCategoryInput input) {
        this.categories = categories; this.audit = audit; this.input = input;
    }

    public List<AdminCategoryRow> list() { return categories.list(); }

    public AdminCategoryRow create(AdminCategoryRequest request, AdminRequestContext context, Long adminId) {
        var normalized = input.normalize(request, true);
        if (categories.existsByCode(normalized.code())) throw new ApiException(ApiError.CATEGORY_CODE_EXISTS);
        categories.insert(normalized.code(), normalized.name(), normalized.icon(), normalized.description(),
                normalized.sortOrder(), normalized.enabled());
        audit.record(adminId, MODULE, "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(normalized.code());
    }

    public AdminCategoryRow update(String rawCode, AdminCategoryRequest request, AdminRequestContext context,
                                   Long adminId) {
        String code = input.code(rawCode);
        if (categories.findByCode(code).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        var normalized = input.normalize(request, false);
        if (!categories.update(code, normalized.name(), normalized.icon(), normalized.description(),
                normalized.sortOrder(), normalized.enabled())) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
        audit.record(adminId, MODULE, "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public AdminCategoryRow updateStatus(String rawCode, boolean enabled, AdminRequestContext context, Long adminId) {
        String code = input.code(rawCode);
        if (!categories.updateStatus(code, enabled)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, enabled ? "STATUS_ENABLED" : "STATUS_DISABLED", context.uri(),
                context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public void delete(String rawCode, AdminRequestContext context, Long adminId) {
        String code = input.code(rawCode);
        if (categories.findByCode(code).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        if (categories.countTools(code) > 0) throw new ApiException(ApiError.CATEGORY_IN_USE);
        if (!categories.softDelete(code)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private AdminCategoryRow detail(String code) {
        return categories.findByCode(code).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }
}
