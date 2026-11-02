package com.example.base.dto.claim;

import jakarta.validation.constraints.*;

public record ReviewClaimRequest(
        @NotNull @Pattern(regexp = "APPROVED|REJECTED", message = "Chỉ được chấp thuận hoặc từ chối") String decision,
        @NotBlank(message = "Vui lòng nhập lý do xét duyệt")
        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String reason,
        @NotNull @PositiveOrZero Long expectedVersion) {}
