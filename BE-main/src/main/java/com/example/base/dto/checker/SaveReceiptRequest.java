package com.example.base.dto.checker;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public record SaveReceiptRequest(
        @NotNull(message = "Vui lòng chọn danh mục") @Positive Long categoryId,
        @NotBlank(message = "Vui lòng nhập tên đồ vật") @Size(max = 200) String title,
        @NotBlank(message = "Vui lòng mô tả đồ vật") @Size(max = 5000) String description,
        @NotBlank(message = "Vui lòng ghi tình trạng đồ vật") @Size(max = 2000) String conditionAtReceipt,
        @NotBlank(message = "Vui lòng nhập nơi nhặt được") @Size(max = 500) String foundLocation,
        @NotNull(message = "Vui lòng chọn thời gian nhặt được")
        @PastOrPresent(message = "Thời gian nhặt không được ở tương lai") OffsetDateTime foundAt,
        @Size(max = 2000) String privateIdentifiers,
        @Size(max = 2000) String note,
        @NotNull(message = "Thiếu danh sách ảnh cần giữ") @Size(max = 5) List<@NotNull @Positive Long> keptImageIds,
        OffsetDateTime expectedUpdatedAt,
        @Positive Long storageLocationId
) {}
