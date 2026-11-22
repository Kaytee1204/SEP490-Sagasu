package com.example.base.repository;

import com.example.base.entity.ChatConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    Optional<ChatConversation> findByPost_IdAndClaimant_Id(long postId, long claimantId);
    Optional<ChatConversation> findByLatestClaim_Id(long claimId);

    @Query(value = """
            SELECT c.conversation_id
            FROM p2p_claim_appeal a
            JOIN p2p_claim_approval approval USING (approval_id)
            JOIN claim selected ON selected.claim_id = approval.approved_claim_id
            JOIN chat_conversation c ON c.post_id = selected.post_id
                AND c.claimant_id = selected.claimant_id AND c.latest_claim_id = selected.claim_id
            WHERE a.report_id = :reportId AND approval.closed_at IS NULL
            """, nativeQuery = true)
    Optional<Long> findActiveConversationIdForAppeal(@Param("reportId") long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id=:id")
    Optional<ChatConversation> findForUpdate(@Param("id") long id);
}
