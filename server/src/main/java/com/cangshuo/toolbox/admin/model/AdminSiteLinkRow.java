package com.cangshuo.toolbox.admin.model;

public record AdminSiteLinkRow(long id, String title, String url, String description, String status,
        Long submittedBy, String createdAt, String updatedAt) {
}
