package com.example.base.dto.claim;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

public record ClaimAnswerRequest(@NotBlank @Size(max = 16) String questionCode, @NotNull JsonNode value) {}
