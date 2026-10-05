package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull Boolean enabled) { }
