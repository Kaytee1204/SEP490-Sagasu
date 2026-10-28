package com.example.base.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeletePostRequest(@NotBlank @Size(max = 1000) String reason) {}
