package com.example.base.dto.post;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Owner-only request/response. Empty text clears the stored identifiers. */
public record PrivateIdentifiersDto(@NotNull @Size(max = 5000) String privateIdentifiers) {}
