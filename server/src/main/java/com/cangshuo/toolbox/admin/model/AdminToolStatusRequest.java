package com.cangshuo.toolbox.admin.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminToolStatusRequest(@NotBlank @Size(max = 16) String status) { }
