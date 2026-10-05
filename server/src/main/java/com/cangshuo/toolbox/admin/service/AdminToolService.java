package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminCategoryResponse;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.model.AdminToolRequest;
import com.cangshuo.toolbox.admin.model.AdminToolResponse;
import com.cangshuo.toolbox.admin.model.AdminToolRow;
import com.cangshuo.toolbox.admin.repository.AdminToolRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import com.cangshuo.toolbox.common.response.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Administrator catalog management. Mutations are single statements plus an independently committed audit
 * row, so no surrounding transaction is used (the same reason as the administrator login flow).
 */
@Service
public class AdminToolService {
    private static final String MODULE = "tool";
    private final AdminToolRepository tools;
    private final AdminAuditService audit;
    private final AdminToolInput input;
    private final ObjectMapper mapper;

    public AdminToolService(AdminToolRepository tools, AdminAuditService audit, AdminToolInput input,
                            ObjectMapper mapper) {
        this.tools = tools; this.audit = audit; this.input = input; this.mapper = mapper;
    }

    public PageResponse<AdminToolResponse> list(int page, int pageSize, String keyword, String categoryCode,
                                                String status) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalizedKeyword != null && (normalizedKeyword.length() > 64
                || normalizedKeyword.chars().anyMatch(Character::isISOControl))) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        String normalizedCategory = categoryCode == null || categoryCode.isBlank() ? null : categoryCode.strip();
        if (normalizedCategory != null && !normalizedCategory.matches("^[A-Z][A-Z0-9_]{0,31}$")) {
            throw new ApiException(ApiError.INVALID_ARGUMENT);
        }
        String normalizedStatus = status == null || status.isBlank() ? null : input.status(status);
        long offset = ((long) page - 1L) * pageSize;
        long total = tools.count(normalizedKeyword, normalizedCategory, normalizedStatus);
        List<AdminToolResponse> records = offset >= total ? List.of()
                : tools.find(normalizedKeyword, normalizedCategory, normalizedStatus, pageSize, offset).stream()
                        .map(this::toResponse).toList();
        return new PageResponse<>(records, page, pageSize, total);
    }

    public AdminToolResponse detail(String rawCode) {
        String code = input.code(rawCode);
        return tools.findByCode(code).map(this::toResponse)
                .orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }

    public List<AdminCategoryResponse> categories() {
        return tools.categories();
    }

    public AdminToolResponse create(AdminToolRequest request, AdminRequestContext context, Long adminId) {
        var normalized = input.normalize(request, true);
        if (tools.existsByCode(normalized.code())) throw new ApiException(ApiError.TOOL_CODE_EXISTS);
        if (!tools.categoryUsable(normalized.categoryCode())) throw new ApiException(ApiError.CATEGORY_NOT_FOUND);
        tools.insert(normalized.code(), normalized.name(), normalized.description(), normalized.categoryCode(),
                normalized.icon(), normalized.keywordsJson(), normalized.mode(), normalized.requiresLogin(),
                normalized.status(), normalized.version(), normalized.sortOrder(), normalized.featured(),
                normalized.configJson());
        audit.record(adminId, MODULE, "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(normalized.code());
    }

    public AdminToolResponse update(String rawCode, AdminToolRequest request, AdminRequestContext context, Long adminId) {
        String code = input.code(rawCode);
        if (tools.findByCode(code).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        var normalized = input.normalize(request, false);
        if (!tools.categoryUsable(normalized.categoryCode())) throw new ApiException(ApiError.CATEGORY_NOT_FOUND);
        if (!tools.update(code, normalized.name(), normalized.description(), normalized.categoryCode(),
                normalized.icon(), normalized.keywordsJson(), normalized.mode(), normalized.requiresLogin(),
                normalized.status(), normalized.version(), normalized.sortOrder(), normalized.featured(),
                normalized.configJson())) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
        audit.record(adminId, MODULE, "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public AdminToolResponse updateStatus(String rawCode, String rawStatus, AdminRequestContext context, Long adminId) {
        String code = input.code(rawCode);
        String status = input.status(rawStatus);
        if (!tools.updateStatus(code, status)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, "STATUS_" + status, context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(code);
    }

    public void delete(String rawCode, AdminRequestContext context, Long adminId) {
        String code = input.code(rawCode);
        if (!tools.softDelete(code)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private AdminToolResponse toResponse(AdminToolRow row) {
        return new AdminToolResponse(row.id(), row.code(), row.name(), row.description(), row.categoryCode(),
                row.categoryName(), row.icon(), readKeywords(row.keywordsJson()), row.mode(), row.requiresLogin(),
                row.status(), row.version(), row.sortOrder(), row.featured(), row.configJson(), row.updatedAt());
    }

    private List<String> readKeywords(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isArray()) throw new IllegalStateException("Invalid keyword data");
            List<String> keywords = new ArrayList<>();
            for (JsonNode keyword : node) {
                if (!keyword.isTextual() || keyword.textValue().isBlank()) {
                    throw new IllegalStateException("Invalid keyword data");
                }
                keywords.add(keyword.textValue());
            }
            return List.copyOf(keywords);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid keyword data");
        }
    }
}
