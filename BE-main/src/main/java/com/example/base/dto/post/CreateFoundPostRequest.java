package com.example.base.dto.post;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record CreateFoundPostRequest(
                @NotBlank @Size(max = 200) String title,
                @NotBlank @Size(max = 10000) String description,
                @NotNull @Positive Long categoryId,
                @NotBlank @Size(max = 1000) String locationText,
                @NotNull @PastOrPresent OffsetDateTime eventAt,
                @Pattern(regexp = "P2P", message = "Bài mới mặc định là P2P; giao bên tiếp nhận sau khi đăng thành công") String custodyMode,
                @Size(max = 5000) String privateIdentifiers,
                @Null(message = "Hiện không hỗ trợ đặt lịch giao đồ") OffsetDateTime handoverRequestedAt) {}
