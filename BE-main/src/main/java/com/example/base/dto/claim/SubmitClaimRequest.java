package com.example.base.dto.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

public record SubmitClaimRequest(
        @NotBlank(message = "Vui lòng mô tả căn cứ nhận đồ")
        @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự") String description,
        @Size(max = 5000, message = "Ghi chú bằng chứng tối đa 5000 ký tự") String evidenceNote,
        @NotBlank @Size(max = 160) String questionnaireVersion,
        @NotNull @Size(max = 24) List<@Valid ClaimAnswerRequest> answers) {}
