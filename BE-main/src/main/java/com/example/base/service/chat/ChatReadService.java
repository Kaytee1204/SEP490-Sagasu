package com.example.base.service.chat;

import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ChatReadService {
    private final ChatService chats;
    private final ChatAccessPolicy policy;
    private final ChatMessageRepository messages;
    private final ChatEvents events;
    private final JdbcTemplate jdbc;
    @Transactional
    public long read(long id, long sequence, CustomUserDetails user) {
        long actor = policy.actor(user).getId(); chats.requireMember(id, actor);
        if (sequence < 0 || (sequence > 0 && !messages.existsByConversationIdAndSequenceNo(id, sequence)))
            throw new AppException(ErrorCode.CHAT_INPUT_INVALID);
        int updated = jdbc.update("""
            UPDATE chat_participant SET last_read_sequence=?,last_read_at=now()
            WHERE conversation_id=? AND account_id=? AND last_read_sequence<?
            """, sequence, id, actor, sequence);
        if (updated > 0) events.changed(id, "READ_UPDATED");
        return jdbc.queryForObject("SELECT last_read_sequence FROM chat_participant WHERE conversation_id=? AND account_id=?",
                Long.class, id, actor);
    }
}
