package com.example.base.dto.checker;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record SubmitReceiptRequest(
        @NotNull(message = "Vui lòng tải lại phiếu trước khi gửi duyệt") OffsetDateTime expectedUpdatedAt) {}
