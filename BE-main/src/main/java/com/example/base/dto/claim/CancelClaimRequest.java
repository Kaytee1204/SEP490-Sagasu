package com.example.base.dto.claim;

import jakarta.validation.constraints.*;

public record CancelClaimRequest(@NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String reason) {}
