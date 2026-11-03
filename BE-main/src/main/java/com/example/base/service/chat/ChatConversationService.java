package com.example.base.service.chat;

import com.example.base.entity.*;
import com.example.base.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class ChatConversationService {
    private final ChatConversationRepository conversations;
    private final ChatParticipantRepository participants;

    /** Caller already holds the post/claim locks in the approval/open transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatConversation forApprovedClaim(Claim claim) {
        ChatConversation conversation = conversations.findByPost_IdAndClaimant_Id(
                claim.getPost().getId(), claim.getClaimant().getId()).orElse(null);
        if (conversation == null) {
            conversation = conversations.saveAndFlush(new ChatConversation(claim));
            participants.save(new ChatParticipant(conversation.getId(), claim.getPost().getAccount().getId(), "FINDER"));
            participants.save(new ChatParticipant(conversation.getId(), claim.getClaimant().getId(), "CLAIMANT"));
            participants.flush();
        } else if (!conversation.getLatestClaim().getId().equals(claim.getId())) {
            conversation.useClaim(claim);
        }
        return conversation;
    }
}
