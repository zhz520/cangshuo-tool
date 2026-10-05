package com.cangshuo.toolbox.admin.service;

import com.cangshuo.toolbox.admin.repository.AdminOperationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes administrator audit rows in their own transaction so failed logins survive a rollback. */
@Service
public class AdminAuditService {
    private final AdminOperationLogRepository logs;
    public AdminAuditService(AdminOperationLogRepository logs) { this.logs = logs; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long adminId, String module, String operation, String uri, String method, String ip, String result) {
        logs.record(adminId, module, operation, uri, method, ip, result);
    }
}
