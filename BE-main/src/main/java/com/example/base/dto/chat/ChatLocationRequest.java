package com.example.base.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChatLocationRequest(@NotNull UUID clientMessageId, @NotNull Double latitude,
        @NotNull Double longitude, @Size(max = 200) String label) {}
