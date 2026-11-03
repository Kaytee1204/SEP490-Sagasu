package com.example.base.service.chat;

import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class ChatConversationService {
    private final ChatConversationRepository conversations;
    private final ChatParticipantRepository participants;
    private final ChatEvents events;
    private final ChatAccessPolicy policy;

    public boolean canOpen(Claim claim) { return policy.eligible(claim); }

    /** Caller already holds the post/claim locks in the approval/open transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChatConversation forApprovedClaim(Claim claim) {
        if (!canOpen(claim)) throw new AppException(ErrorCode.CHAT_UNAVAILABLE);
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
        events.changed(conversation.getId(), "CONVERSATION_UPDATED");
        return conversation;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyChanged(Claim claim) {
        conversations.findByPost_IdAndClaimant_Id(claim.getPost().getId(), claim.getClaimant().getId())
                .ifPresent(c -> events.changed(c.getId(), "CONVERSATION_UPDATED"));
    }
}
