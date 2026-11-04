package com.example.base.dto.chat;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SendChatRequest(@NotNull UUID clientMessageId, String body) {}
