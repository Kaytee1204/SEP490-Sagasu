package com.example.base.repository;

import com.example.base.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByConversationIdAndSenderIdAndClientMessageId(long conversationId, long senderId, UUID clientId);
    List<ChatMessage> findByConversationIdAndSequenceNoLessThanOrderBySequenceNoDesc(long id, long before, Pageable page);
    List<ChatMessage> findByConversationIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(long id, long after, Pageable page);
    boolean existsByConversationIdAndSequenceNo(long conversationId, long sequenceNo);
}
