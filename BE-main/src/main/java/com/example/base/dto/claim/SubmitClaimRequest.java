package com.example.base.dto.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitClaimRequest(
        @NotBlank(message = "Vui lòng mô tả căn cứ nhận đồ")
        @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự") String description,
        @Size(max = 5000, message = "Ghi chú bằng chứng tối đa 5000 ký tự") String evidenceNote) {}
