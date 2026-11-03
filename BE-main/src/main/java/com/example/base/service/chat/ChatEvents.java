package com.example.base.service.chat;

import com.example.base.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component @RequiredArgsConstructor @Slf4j
public class ChatEvents {
    private final SimpMessagingTemplate messaging;
    private final ChatParticipantRepository participants;
    public record Event(String type, long conversationId) {}
    public void changed(long conversationId, String type) {
        var recipients = participants.findByConversationId(conversationId).stream().map(p -> p.getAccountId().toString()).toList();
        Runnable publish = () -> recipients.forEach(id -> {
            try { messaging.convertAndSendToUser(id, "/queue/chat-events", new Event(type, conversationId)); }
            catch (RuntimeException ex) { log.warn("Chat event delivery unavailable for conversation {}", conversationId); }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }
}
