package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotNull;

public record AdminAnnouncementStatusRequest(@NotNull Boolean enabled) { }
