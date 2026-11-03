package com.example.base.service.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.dto.chat.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatService chats;
    private final ChatAccessPolicy policy;
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final PostRepository posts;
    private final ClaimRepository claims;
    private final EntityManager entityManager;
    private final ChatProperties limits;
    private final ChatEvents events;
    private final ChatAttachmentRepository attachments;
    private final com.example.base.storage.ImageStorage storage;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ChatMessageDto.Receipts receipts(long id, long after, long through, int limit, CustomUserDetails user) {
        long actor = policy.actor(user).getId(); chats.requireMember(id, actor);
        if (after < 0 || through < after || limit < 1 || limit > 100) throw invalid();
        var rows = jdbc.query("""
            SELECT sequence_no,read_at FROM chat_message WHERE conversation_id=? AND sender_id=?
              AND sequence_no>? AND sequence_no<=? AND read_at IS NOT NULL ORDER BY sequence_no LIMIT ?
            """, (rs,n) -> new ChatMessageDto.Receipt(rs.getLong("sequence_no"), rs.getObject("read_at", java.time.OffsetDateTime.class)),
                id, actor, after, through, limit + 1);
        return new ChatMessageDto.Receipts(rows.stream().limit(limit).toList(), rows.size() > limit);
    }

    public ChatMessageDto.Page history(long id, Long before, Long after, int size, CustomUserDetails user) {
        long actor = policy.actor(user).getId(); chats.requireMember(id, actor);
        if (size < 1 || size > 100 || (before != null && after != null)
                || (before != null && before <= 0) || (after != null && after < 0)) throw invalid();
        var page = PageRequest.of(0, size + 1);
        var rows = after == null ? messages.findByConversationIdAndSequenceNoLessThanOrderBySequenceNoDesc(id, before == null ? Long.MAX_VALUE : before, page)
                : messages.findByConversationIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(id, after, page);
        boolean more = rows.size() > size;
        var selected = new ArrayList<>(rows.stream().limit(size).toList());
        if (after == null) Collections.reverse(selected);
        var images = selected.isEmpty() ? Map.<Long, List<ChatAttachment>>of()
                : attachments.findByMessageIdInOrderByMessageIdAscSortOrderAsc(selected.stream().map(ChatMessage::getId).toList())
                    .stream().collect(java.util.stream.Collectors.groupingBy(ChatAttachment::getMessageId));
        return new ChatMessageDto.Page(selected.stream().map(m -> dto(m, images.getOrDefault(m.getId(), List.of()))).toList(), more,
                after == null && more && !selected.isEmpty() ? selected.get(0).getSequenceNo() : null);
    }

    @Transactional
    public ChatMessageDto send(long id, SendChatRequest request, CustomUserDetails user) {
        long actor = policy.actor(user).getId();
        if (request == null || request.clientMessageId() == null) throw invalid();
        String body = text(request.body());
        if (body == null) throw invalid();
        ChatConversation conversation = lock(id, actor);
        var existing = messages.findByConversationIdAndSenderIdAndClientMessageId(id, actor, request.clientMessageId());
        if (existing.isPresent()) {
            if (!"TEXT".equals(existing.get().getMessageType()) || !Objects.equals(existing.get().getBody(), body))
                throw new AppException(ErrorCode.CHAT_DUPLICATE_PAYLOAD);
            return dto(existing.get());
        }
        requireWritable(conversation);
        var message = messages.saveAndFlush(new ChatMessage(id, actor, conversation.nextMessageSequence(), request.clientMessageId(), "TEXT", body));
        events.changed(id, "MESSAGE_CREATED");
        return dto(message);
    }

    public ChatConversation lock(long id, long actor) {
        ChatConversation conversation = chats.requireMember(id, actor);
        Post post = posts.findForClaimUpdate(conversation.getPost().getId()).orElseThrow(this::invalid);
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(conversation);
        Claim claim = claims.findForUpdate(conversation.getLatestClaim().getId()).orElseThrow(this::invalid);
        entityManager.refresh(claim, LockModeType.PESSIMISTIC_WRITE);
        conversation = conversations.findForUpdate(id).orElseThrow(this::invalid);
        entityManager.refresh(conversation, LockModeType.PESSIMISTIC_WRITE);
        return conversation;
    }
    public void requireWritable(ChatConversation conversation) {
        if (policy.readOnlyReason(conversation) != null) throw new AppException(ErrorCode.CHAT_UNAVAILABLE);
    }
    public String text(String value) {
        String result = value == null || value.isBlank() ? null : value.strip();
        if (result != null && result.length() > limits.getMessageMaxLength()) throw invalid();
        return result;
    }
    public ChatMessageDto dto(ChatMessage message) {
        return dto(message, attachments.findByMessageIdOrderBySortOrderAsc(message.getId()));
    }
    private ChatMessageDto dto(ChatMessage message, List<ChatAttachment> files) {
        return new ChatMessageDto(message.getId(), message.getSequenceNo(), message.getSenderId(), message.getBody(), message.getCreatedAt(),
                files.stream().map(file -> new ChatMessageDto.Attachment(file.getId(), file.getOriginalName(), file.getMimeType(), file.getSizeBytes(),
                    file.getMimeType().startsWith("image/") ? storage.readUrl(file.getStorageKey(), null) : null)).toList(), message.getReadAt());
    }
    private AppException invalid() { return new AppException(ErrorCode.CHAT_INPUT_INVALID); }
}
