package com.example.base.dto.chat;

import jakarta.validation.constraints.Min;

public record ReadChatRequest(@Min(0) long lastReadSequence) {}
