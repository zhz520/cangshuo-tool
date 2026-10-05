package com.cangshuo.toolbox.admin.model;

public record AdminStoredObjectResponse(String key, long size, String sha256, String contentType) { }
