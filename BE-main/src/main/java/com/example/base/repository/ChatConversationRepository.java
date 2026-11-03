package com.example.base.repository;

import com.example.base.entity.ChatConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByPost_IdAndClaimant_Id(long postId, long claimantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id=:id")
    Optional<ChatConversation> findForUpdate(@Param("id") long id);
}
