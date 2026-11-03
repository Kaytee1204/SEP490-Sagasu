package com.example.base.repository;

import com.example.base.entity.ChatParticipant;
import com.example.base.entity.ChatParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipantId> {
    List<ChatParticipant> findByConversationId(long conversationId);
}
