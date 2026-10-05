package com.cangshuo.toolbox.admin.model;

public record AdminAccount(long id, String username, String passwordHash, String nickname, String roleCode, int status) {
    @Override public String toString() { return "AdminAccount[redacted]"; }
    public AdminProfileResponse publicProfile() { return new AdminProfileResponse(id, username, nickname, roleCode); }
}
