package com.example.base.service.chat;

import com.example.base.entity.ChatParticipant;
import com.example.base.repository.ChatParticipantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEventsTest {

    @Mock
    private SimpMessagingTemplate messaging;
    @Mock
    private ChatParticipantRepository participants;

    @InjectMocks
    private ChatEvents events;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // UTC089 - changed sends to all participants immediately when no transaction is active
    @Test
    void changed_withoutActiveTransaction_sendsToEachParticipantImmediately() {
        when(participants.findByConversationId(55L)).thenReturn(List.of(
                new ChatParticipant(55L, 10L, "FINDER"),
                new ChatParticipant(55L, 20L, "CLAIMANT")
        ));

        events.changed(55L, "MESSAGE_SENT");

        ChatEvents.Event expected = new ChatEvents.Event("MESSAGE_SENT", 55L);
        verify(messaging).convertAndSendToUser(eq("10"), eq("/queue/chat-events"), eq(expected));
        verify(messaging).convertAndSendToUser(eq("20"), eq("/queue/chat-events"), eq(expected));
    }

    // UTC090 - changed defers sending events until TransactionSynchronization.afterCommit
    @Test
    void changed_insideActiveTransaction_defersDispatchUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(participants.findByConversationId(55L)).thenReturn(List.of(
                new ChatParticipant(55L, 10L, "FINDER")
        ));

        events.changed(55L, "READ_UPDATED");

        verifyNoInteractions(messaging);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(messaging).convertAndSendToUser(eq("10"), eq("/queue/chat-events"), eq(new ChatEvents.Event("READ_UPDATED", 55L)));
    }

    // UTC091 - changed continues notifying second recipient when messaging fails for first
    @Test
    void changed_whenMessagingThrowsForFirstRecipient_stillNotifiesSecondRecipient() {
        when(participants.findByConversationId(55L)).thenReturn(List.of(
                new ChatParticipant(55L, 10L, "FINDER"),
                new ChatParticipant(55L, 20L, "CLAIMANT")
        ));
        doThrow(new RuntimeException("WS unavailable"))
                .when(messaging).convertAndSendToUser(eq("10"), anyString(), any());

        events.changed(55L, "MESSAGE_CREATED");

        verify(messaging).convertAndSendToUser(
                eq("20"), eq("/queue/chat-events"), eq(new ChatEvents.Event("MESSAGE_CREATED", 55L)));
    }
}

