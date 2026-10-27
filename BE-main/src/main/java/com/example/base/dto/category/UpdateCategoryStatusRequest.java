package com.example.base.dto.category;

import jakarta.validation.constraints.NotNull;

public record UpdateCategoryStatusRequest(
        @NotNull(message = "Trạng thái hoạt động là bắt buộc") Boolean active) {}
