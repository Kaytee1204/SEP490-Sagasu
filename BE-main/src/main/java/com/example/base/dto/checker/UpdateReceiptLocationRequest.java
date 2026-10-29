package com.example.base.dto.checker;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record UpdateReceiptLocationRequest(@Positive Long storageLocationId, @NotNull OffsetDateTime expectedUpdatedAt) {}
