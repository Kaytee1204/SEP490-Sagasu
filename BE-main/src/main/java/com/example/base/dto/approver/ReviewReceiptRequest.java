package com.example.base.dto.approver;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record ReviewReceiptRequest(@NotNull Decision decision, @Size(max = 2000) String note,
                                   @NotNull OffsetDateTime expectedUpdatedAt) {
    public enum Decision { ACCEPTED, REJECTED, NEEDS_INFO }
}
