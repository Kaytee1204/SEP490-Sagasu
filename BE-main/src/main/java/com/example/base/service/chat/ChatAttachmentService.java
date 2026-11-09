package com.example.base.service.chat;

import com.example.base.dto.chat.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class ChatAttachmentService {
    private final ChatAttachmentValidator validator;
    private final ChatAccessPolicy policy;
    private final ChatService chats;
    private final ChatMessageService messageService;
    private final ChatMessageRepository messages;
    private final ChatAttachmentRepository attachments;
    private final ImageStorage storage;
    private final PlatformTransactionManager transactionManager;
    private final ChatEvents events;
    private final ObjectProvider<S3Client> clients;
    private final ObjectProvider<R2Properties> properties;
    private record Saved(ChatMessageDto message, boolean created) {}
    public record Download(byte[] bytes, String name, String mime) {}

    public ChatMessageDto send(long id, SendChatRequest request, List<MultipartFile> files, CustomUserDetails user) {
        if (request == null || request.clientMessageId() == null) throw new AppException(ErrorCode.CHAT_INPUT_INVALID);
        var tx = new TransactionTemplate(transactionManager);
        String body = messageService.text(request.body());
        long actor = tx.execute(status -> {
            long actorId = policy.actor(user).getId();
            var conversation = chats.requireMember(id, actorId);
            if (messages.findByConversationIdAndSenderIdAndClientMessageId(id, actorId, request.clientMessageId()).isEmpty())
                messageService.requireWritable(conversation);
            return actorId;
        });
        var uploads = validator.validate(files);
        ChatMessageDto previous = tx.execute(status -> messages.findByConversationIdAndSenderIdAndClientMessageId(id, actor, request.clientMessageId())
                .map(message -> { same(message, body, uploads); return messageService.dto(message); }).orElse(null));
        if (previous != null) return previous;
        List<String> keys = new ArrayList<>();
        try {
            for (var upload : uploads) {
                String key = "chat/" + id + "/" + UUID.randomUUID() + "." + upload.extension();
                keys.add(key); storage.put(key, upload.bytes(), upload.mime());
            }
            Saved saved = tx.execute(status -> {
                policy.actor(user);
                var conversation = messageService.lock(id, actor);
                var duplicate = messages.findByConversationIdAndSenderIdAndClientMessageId(id, actor, request.clientMessageId());
                if (duplicate.isPresent()) {
                    same(duplicate.get(), body, uploads);
                    return new Saved(messageService.dto(duplicate.get()), false);
                }
                messageService.requireWritable(conversation);
                var message = messages.saveAndFlush(new ChatMessage(id, actor, conversation.nextMessageSequence(),
                        request.clientMessageId(), body == null ? "ATTACHMENT" : "MIXED", body));
                for (int i=0; i<uploads.size(); i++) {
                    var upload = uploads.get(i);
                    attachments.save(new ChatAttachment(message.getId(), keys.get(i), upload.name(), upload.mime(),
                            upload.bytes().length, upload.hash(), i));
                }
                attachments.flush(); events.changed(id, "MESSAGE_CREATED");
                messageService.notifyChatRecipient(conversation, actor, body != null && !body.isBlank() ? body : "Đã gửi hình ảnh/tệp đính kèm trong cuộc trò chuyện.");
                return new Saved(messageService.dto(message), true);
            });
            if (!saved.created()) cleanup(keys);
            return saved.message();
        } catch (RuntimeException ex) { cleanup(keys); throw ex; }
    }

    private void same(ChatMessage message, String body, List<ChatAttachmentValidator.Upload> uploads) {
        var existing = attachments.findByMessageIdOrderBySortOrderAsc(message.getId());
        boolean same = Objects.equals(message.getBody(), body) && existing.size() == uploads.size();
        for (int i=0; same && i<uploads.size(); i++) {
            var old = existing.get(i); var upload = uploads.get(i);
            same = old.getContentHash().equals(upload.hash()) && old.getMimeType().equals(upload.mime())
                    && old.getSizeBytes() == upload.bytes().length && Objects.equals(old.getOriginalName(), upload.name());
        }
        if (!same) throw new AppException(ErrorCode.CHAT_DUPLICATE_PAYLOAD);
    }
    private void cleanup(List<String> keys) {
        for (String key : keys) try {
            // On an uncertain commit, preserve any object already referenced by DB.
            if (!attachments.existsByStorageKey(key)) storage.delete(key);
        } catch (RuntimeException ex) { log.warn("Deferred cleanup of chat upload {}", key); }
    }

    @Transactional(readOnly = true)
    public Download download(long conversationId, long attachmentId, CustomUserDetails user) {
        chats.requireMember(conversationId, policy.actor(user).getId());
        var attachment = attachments.findById(attachmentId).orElseThrow(() -> new AppException(ErrorCode.CHAT_NOT_FOUND));
        var message = messages.findById(attachment.getMessageId()).orElseThrow(() -> new AppException(ErrorCode.CHAT_NOT_FOUND));
        if (message.getConversationId() != conversationId) throw new AppException(ErrorCode.CHAT_NOT_FOUND);
        var client = clients.getIfAvailable(); var config = properties.getIfAvailable();
        if (client == null || config == null || !config.enabled()) throw new AppException(ErrorCode.STORAGE_UNAVAILABLE);
        try {
            byte[] bytes = client.getObjectAsBytes(GetObjectRequest.builder().bucket(config.bucket()).key(attachment.getStorageKey()).build()).asByteArray();
            return new Download(bytes, attachment.getOriginalName(), attachment.getMimeType());
        } catch (RuntimeException ex) { throw new AppException(ErrorCode.STORAGE_UNAVAILABLE); }
    }
}
