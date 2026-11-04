package com.example.base.dto.post;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public record UpdatePostRequest(
        @NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=10000) String description,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max=1000) String locationText,
        @NotNull @PastOrPresent OffsetDateTime eventAt,
        @Pattern(regexp="P2P|OFFICE") String custodyMode,
        @NotNull @Size(max=5) List<@NotNull @Positive Long> keptImageIds,
        @NotNull OffsetDateTime expectedUpdatedAt,
        @Null(message = "Hiện không hỗ trợ đặt lịch giao đồ") OffsetDateTime handoverRequestedAt) {}
