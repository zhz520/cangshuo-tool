package com.cangshuo.toolbox.admin.model;

import java.time.Instant;

/** Read model for administrator audit rows; the account name is null after the admin row is deleted. */
public record AdminOperationLogRow(long id, Long adminId, String adminUsername, String module, String operation,
    String requestUri, String requestMethod, String ip, String result, Instant createdAt) { }
