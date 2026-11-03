package com.example.base.dto.chat;

public record ChatDto(long id, long postId, String postTitle, long claimId,
        long otherAccountId, String otherName, boolean canSend, String readOnlyReason,
        long lastSequence, long lastReadSequence, long otherReadSequence) {}
