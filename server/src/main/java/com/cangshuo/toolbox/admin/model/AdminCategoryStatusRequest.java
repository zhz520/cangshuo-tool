package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotNull;

public record AdminCategoryStatusRequest(@NotNull Boolean enabled) { }
