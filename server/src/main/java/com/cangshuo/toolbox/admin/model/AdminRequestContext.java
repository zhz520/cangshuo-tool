package com.cangshuo.toolbox.admin.model;

/** Fixed audit metadata only; never carries credentials or request bodies. */
public record AdminRequestContext(String ip, String uri, String method) { }
