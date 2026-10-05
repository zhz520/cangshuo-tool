package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotNull;

public record AdminRecommendationStatusRequest(@NotNull Boolean enabled) { }
