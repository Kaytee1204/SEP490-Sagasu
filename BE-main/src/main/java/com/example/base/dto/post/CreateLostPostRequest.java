package com.example.base.dto.post;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record CreateLostPostRequest(
                @NotBlank @Size(max = 200) String title,
                @NotBlank @Size(max = 10000) String description,
                @NotNull @Positive Long categoryId,
                @NotBlank @Size(max = 1000) String locationText,
                @NotNull @PastOrPresent OffsetDateTime eventAt,
                @Size(max = 5000) String privateIdentifiers) {
}
