package com.example.base.dto.receipt;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record UpdateReceiptLocationRequest(@Positive Long storageLocationId, @NotNull OffsetDateTime expectedUpdatedAt) {}

