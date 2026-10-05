package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.model.AdminAnnouncementRequest;
import com.cangshuo.toolbox.admin.model.AdminAnnouncementRow;
import com.cangshuo.toolbox.admin.model.AdminRequestContext;
import com.cangshuo.toolbox.admin.repository.AdminAnnouncementRepository;
import com.cangshuo.toolbox.common.exception.ApiError;
import com.cangshuo.toolbox.common.exception.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

/** Announcements: drafts, publish/unpublish and an optional UTC display window. */
@Service
public class AdminAnnouncementService {
    private static final String MODULE = "announcement";
    private final AdminAnnouncementRepository announcements;
    private final AdminAuditService audit;
    private final AdminAnnouncementInput input;

    public AdminAnnouncementService(AdminAnnouncementRepository announcements, AdminAuditService audit,
                                    AdminAnnouncementInput input) {
        this.announcements = announcements; this.audit = audit; this.input = input;
    }

    public List<AdminAnnouncementRow> list() { return announcements.list(); }

    public AdminAnnouncementRow create(AdminAnnouncementRequest request, AdminRequestContext context, Long adminId) {
        var normalized = input.normalize(request);
        long id = announcements.insert(normalized.title(), normalized.body(), normalized.level(),
                normalized.published(), normalized.startAt(), normalized.endAt());
        audit.record(adminId, MODULE, "CREATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(id);
    }

    public AdminAnnouncementRow update(String rawId, AdminAnnouncementRequest request, AdminRequestContext context,
                                       Long adminId) {
        long id = input.id(rawId);
        if (announcements.findById(id).isEmpty()) throw new ApiException(ApiError.NOT_FOUND);
        var normalized = input.normalize(request);
        if (!announcements.update(id, normalized.title(), normalized.body(), normalized.level(),
                normalized.published(), normalized.startAt(), normalized.endAt())) {
            throw new ApiException(ApiError.NOT_FOUND);
        }
        audit.record(adminId, MODULE, "UPDATE", context.uri(), context.method(), context.ip(), "SUCCESS");
        return detail(id);
    }

    public AdminAnnouncementRow updateStatus(String rawId, boolean published, AdminRequestContext context,
                                             Long adminId) {
        long id = input.id(rawId);
        var current = announcements.findById(id).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
        if (current.published() == published) return current;
        if (!announcements.updateStatus(id, published)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, published ? "PUBLISH" : "UNPUBLISH", context.uri(), context.method(),
                context.ip(), "SUCCESS");
        return detail(id);
    }

    public void delete(String rawId, AdminRequestContext context, Long adminId) {
        long id = input.id(rawId);
        if (!announcements.softDelete(id)) throw new ApiException(ApiError.NOT_FOUND);
        audit.record(adminId, MODULE, "DELETE", context.uri(), context.method(), context.ip(), "SUCCESS");
    }

    private AdminAnnouncementRow detail(long id) {
        return announcements.findById(id).orElseThrow(() -> new ApiException(ApiError.NOT_FOUND));
    }
}
