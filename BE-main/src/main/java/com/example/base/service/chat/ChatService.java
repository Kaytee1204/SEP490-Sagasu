package com.example.base.service.chat;

import com.example.base.dto.chat.ChatDto;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ChatService {
    private final ChatConversationRepository conversations;
    private final ChatParticipantRepository participants;
    private final ClaimRepository claims;
    private final PostRepository posts;
    private final ChatAccessPolicy policy;
    private final ChatConversationService conversationService;
    private final EntityManager entityManager;

    public ChatDto detail(long id, CustomUserDetails user) {
        Account actor = policy.actor(user);
        return dto(requireMember(id, actor.getId()), actor.getId());
    }

    @Transactional
    public ChatDto open(long postId, CustomUserDetails user) {
        Account actor = policy.actor(user);
        Post post = posts.findForClaimUpdate(postId).orElseThrow(this::missing);
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
        Claim claim = claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(postId, "APPROVED")
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_UNAVAILABLE));
        if (!Objects.equals(actor.getId(), post.getAccount().getId())
                && !Objects.equals(actor.getId(), claim.getClaimant().getId())) throw missing();
        claim = claims.findForUpdate(claim.getId()).orElseThrow(this::missing);
        entityManager.refresh(claim, LockModeType.PESSIMISTIC_WRITE);
        if (!policy.eligible(claim)) throw new AppException(ErrorCode.CHAT_UNAVAILABLE);
        return dto(conversationService.forApprovedClaim(claim), actor.getId());
    }

    public ChatConversation requireMember(long id, long actorId) {
        if (id <= 0 || !participants.existsById(new ChatParticipantId(id, actorId))) throw missing();
        return conversations.findById(id).orElseThrow(this::missing);
    }

    public ChatDto dto(ChatConversation conversation, long actorId) {
        var members = participants.findByConversationId(conversation.getId());
        var mine = members.stream().filter(m -> m.getAccountId().equals(actorId)).findFirst().orElseThrow(this::missing);
        var other = members.stream().filter(m -> !m.getAccountId().equals(actorId)).findFirst().orElseThrow(this::missing);
        Account account = "FINDER".equals(other.getParticipantRole()) ? conversation.getPost().getAccount() : conversation.getClaimant();
        String reason = policy.readOnlyReason(conversation);
        return new ChatDto(conversation.getId(), conversation.getPost().getId(), conversation.getPost().getTitle(),
                conversation.getLatestClaim().getId(), account.getId(), account.getFullName(), reason == null, reason,
                conversation.getLastMessageSequence(), mine.getLastReadSequence(), other.getLastReadSequence());
    }
    private AppException missing() { return new AppException(ErrorCode.CHAT_NOT_FOUND); }
}
