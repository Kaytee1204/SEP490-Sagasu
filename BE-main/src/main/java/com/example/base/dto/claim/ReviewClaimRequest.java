package com.example.base.dto.claim;

import jakarta.validation.constraints.*;

public record ReviewClaimRequest(
        @NotNull @Pattern(regexp = "APPROVED|REJECTED", message = "Chỉ được chấp thuận hoặc từ chối") String decision,
        @Size(max = 2000, message = "Ghi chú hoặc lý do tối đa 2000 ký tự") String reason,
        @NotNull @PositiveOrZero Long expectedVersion,
        boolean evidenceVerified) {}
