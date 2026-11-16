package com.example.base.dto.claim;
import jakarta.validation.constraints.*;
public record RevokeClaimApprovalRequest(
        @NotBlank(message = "Vui lòng nhập lý do thu hồi")
        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự") String reason,
        @NotNull @PositiveOrZero Long expectedVersion) {}
