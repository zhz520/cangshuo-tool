package com.cangshuo.toolbox.auth.model;

public record UserAccount(long id, String email, String passwordHash, String nickname, int status) {
    @Override public String toString() { return "UserAccount[redacted]"; }
    public UserResponse publicProfile() { return new UserResponse(id, email, nickname); }
}
