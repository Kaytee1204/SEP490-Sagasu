package com.example.base.service.chat;

import com.example.base.dto.chat.ChatMessageDto;
import com.example.base.dto.chat.SendChatRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ChatAttachment;
import com.example.base.entity.ChatConversation;
import com.example.base.entity.ChatMessage;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ChatAttachmentRepository;
import com.example.base.repository.ChatMessageRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.R2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    private ChatAttachmentValidator validator;
    @Mock
    private ChatAccessPolicy policy;
    @Mock
    private ChatService chatService;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private ChatAttachmentRepository attachments;
    @Mock
    private ImageStorage storage;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ChatEvents events;
    @Mock
    private ObjectProvider<S3Client> s3Clients;
    @Mock
    private ObjectProvider<R2Properties> r2PropsProvider;
    @Mock
    private S3Client s3Client;

    private ChatAttachmentService service;
    private Account claimant;
    private CustomUserDetails user;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        service = new ChatAttachmentService(
                validator, policy, chatService, chatMessageService, messages, attachments,
                storage, transactionManager, events, s3Clients, r2PropsProvider);

        claimant = new Account();
        claimant.setId(20L);
        claimant.setEmail("claimant@fpt.edu.vn");
        claimant.setFullName("Claimant");
        claimant.setRole(AccountRole.USER);
        claimant.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(claimant);

        Account finder = new Account();
        finder.setId(10L);
        finder.setEmail("finder@fpt.edu.vn");
        finder.setFullName("Finder");
        finder.setRole(AccountRole.USER);
        finder.setStatus(AccountStatus.ACTIVE);

        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 1L);
        cat.setName("Wallet");

        Post post = Post.found(finder, cat, "Wallet", "Desc", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        Claim claim = Claim.submit(post, claimant, "Mine", null);
        conversation = new ChatConversation(claim);
        ReflectionTestUtils.setField(conversation, "id", 800L);
        ReflectionTestUtils.setField(conversation, "lastMessageSequence", 1L);

        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    // UTC057 - send with body text and PDF attachment persists MIXED message, saves attachment, and notifies
    @Test
    void send_whenValidAttachments_uploadsPersistsMessageAndNotifies() {
        UUID reqId = UUID.randomUUID();
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "doc.pdf", "application/pdf", new byte[]{1, 2}));
        List<ChatAttachmentValidator.Upload> uploads = List.of(
                new ChatAttachmentValidator.Upload(new byte[]{1, 2}, "application/pdf", "pdf", "doc.pdf", "hash1"));
        ChatMessageDto expectedDto = new ChatMessageDto(900L, 2L, 20L, "Here is proof",
                OffsetDateTime.now(), List.of(), null, null);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(chatMessageService.text("Here is proof")).thenReturn("Here is proof");
        when(validator.validate(files)).thenReturn(uploads);
        when(chatMessageService.lock(800L, 20L)).thenReturn(conversation);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                .thenReturn(Optional.empty());
        when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 900L);
            return m;
        });
        when(chatMessageService.dto(any(ChatMessage.class))).thenReturn(expectedDto);

        ChatMessageDto result = service.send(800L, new SendChatRequest(reqId, "Here is proof"), files, user);

        assertEquals(900L, result.id());
        verify(storage).put(anyString(), eq(new byte[]{1, 2}), eq("application/pdf"));
        verify(attachments).save(any(ChatAttachment.class));
        verify(events).changed(800L, "MESSAGE_CREATED");
        verify(chatMessageService).notifyChatRecipient(eq(conversation), eq(20L), anyString());
    }

    // UTC059 - send with duplicate clientMessageId and matching attachment hash reuses existing without upload
    @Test
    void send_whenDuplicateClientMessageId_reusesExistingWithoutUploading() {
        UUID reqId = UUID.randomUUID();
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1}));
        List<ChatAttachmentValidator.Upload> uploads = List.of(
                new ChatAttachmentValidator.Upload(new byte[]{1}, "image/jpeg", "jpg", "a.jpg", "hash1"));
        ChatMessage existing = new ChatMessage(800L, 20L, 2L, reqId, "TEXT", "Hi");
        ReflectionTestUtils.setField(existing, "id", 901L);
        ChatAttachment existingAtt = new ChatAttachment(901L, "k", "a.jpg", "image/jpeg", 1L, "hash1", 0);
        ChatMessageDto expectedDto = new ChatMessageDto(901L, 2L, 20L, "Hi",
                OffsetDateTime.now(), List.of(), null, null);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(chatMessageService.text("Hi")).thenReturn("Hi");
        when(validator.validate(files)).thenReturn(uploads);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                .thenReturn(Optional.of(existing));
        when(attachments.findByMessageIdOrderBySortOrderAsc(901L)).thenReturn(List.of(existingAtt));
        when(chatMessageService.dto(existing)).thenReturn(expectedDto);

        ChatMessageDto result = service.send(800L, new SendChatRequest(reqId, "Hi"), files, user);

        assertEquals(901L, result.id());
        verifyNoInteractions(storage);
    }

    // Supplementary (ChatAttachmentService.send): cleans up uploaded keys when DB transaction fails after upload
    @Test
    void send_whenTransactionFailsAfterUpload_deletesUploadedKeys() {
        UUID reqId = UUID.randomUUID();
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[]{1}));
        List<ChatAttachmentValidator.Upload> uploads = List.of(
                new ChatAttachmentValidator.Upload(new byte[]{1}, "image/jpeg", "jpg", "a.jpg", "hash1"));

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(validator.validate(files)).thenReturn(uploads);
        when(chatMessageService.lock(800L, 20L)).thenReturn(conversation);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("DB fail")).when(messages).saveAndFlush(any());

        assertThrows(RuntimeException.class,
                () -> service.send(800L, new SendChatRequest(reqId, null), files, user));

        verify(storage).delete(anyString());
    }

    // UTC062 - download returns bytes, filename, and content type when attachment belongs to conversation
    @Test
    void download_whenR2EnabledAndAttachmentBelongsToConversation_returnsBytes() {
        ChatAttachment att = new ChatAttachment(900L, "chat/800/file.pdf", "receipt.pdf", "application/pdf", 4L, "h", 0);
        ReflectionTestUtils.setField(att, "id", 55L);
        ChatMessage msg = new ChatMessage(800L, 10L, 1L, UUID.randomUUID(), "TEXT", "See file");
        ReflectionTestUtils.setField(msg, "id", 900L);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(attachments.findById(55L)).thenReturn(Optional.of(att));
        when(messages.findById(900L)).thenReturn(Optional.of(msg));
        when(s3Clients.getIfAvailable()).thenReturn(s3Client);
        when(r2PropsProvider.getIfAvailable()).thenReturn(
                new R2Properties(true, "https://r2.example", "bucket-1", "ak", "sk"));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{9, 8, 7}));

        ChatAttachmentService.Download dl = service.download(800L, 55L, user);

        assertArrayEquals(new byte[]{9, 8, 7}, dl.bytes());
        assertEquals("receipt.pdf", dl.name());
        assertEquals("application/pdf", dl.mime());
    }

    // UTC063 - download throws CHAT_NOT_FOUND when attachment belongs to a different conversation
    @Test
    void download_whenAttachmentBelongsToOtherConversation_throwsChatNotFound() {
        ChatAttachment att = new ChatAttachment(999L, "chat/801/file.pdf", "receipt.pdf", "application/pdf", 4L, "h", 0);
        ReflectionTestUtils.setField(att, "id", 55L);
        ChatMessage otherMsg = new ChatMessage(801L, 10L, 1L, UUID.randomUUID(), "TEXT", "Other");
        ReflectionTestUtils.setField(otherMsg, "id", 999L);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(attachments.findById(55L)).thenReturn(Optional.of(att));
        when(messages.findById(999L)).thenReturn(Optional.of(otherMsg));

        assertEquals(ErrorCode.CHAT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.download(800L, 55L, user)).getErrorCode());
    }

    // UTC064 - download throws STORAGE_UNAVAILABLE when R2 / S3Client is disabled or absent
    @Test
    void download_whenStorageDisabled_throwsStorageUnavailable() {
        ChatAttachment att = new ChatAttachment(900L, "chat/800/file.pdf", "receipt.pdf", "application/pdf", 4L, "h", 0);
        ReflectionTestUtils.setField(att, "id", 55L);
        ChatMessage msg = new ChatMessage(800L, 10L, 1L, UUID.randomUUID(), "TEXT", "See file");
        ReflectionTestUtils.setField(msg, "id", 900L);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(attachments.findById(55L)).thenReturn(Optional.of(att));
        when(messages.findById(900L)).thenReturn(Optional.of(msg));
        when(s3Clients.getIfAvailable()).thenReturn(null);

        assertEquals(ErrorCode.STORAGE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.download(800L, 55L, user)).getErrorCode());
    }

    // UTC056 - send with body=null and 2 PNG images persists ATTACHMENT message and sends fallback preview notification
    @Test
    void send_whenBodyNullWithTwoPngImages_savesAttachmentMessageAndNotifiesFallbackPreview() {
        UUID reqId = UUID.randomUUID();
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[] { 1 }),
                new MockMultipartFile("files", "b.png", "image/png", new byte[] { 2 }));
        List<ChatAttachmentValidator.Upload> uploads = List.of(
                new ChatAttachmentValidator.Upload(new byte[] { 1 }, "image/png", "png", "a.png", "h1"),
                new ChatAttachmentValidator.Upload(new byte[] { 2 }, "image/png", "png", "b.png", "h2"));
        ChatMessageDto expected = new ChatMessageDto(910L, 1L, 20L, null, OffsetDateTime.now(), List.of(), null, null);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(chatMessageService.text(null)).thenReturn(null);
        when(validator.validate(files)).thenReturn(uploads);
        when(chatMessageService.lock(800L, 20L)).thenReturn(conversation);
        when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 910L);
            return m;
        });
        when(chatMessageService.dto(any(ChatMessage.class))).thenReturn(expected);

        ChatMessageDto result = service.send(800L, new SendChatRequest(reqId, null), files, user);

        assertEquals(910L, result.id());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).saveAndFlush(msgCaptor.capture());
        assertEquals("ATTACHMENT", msgCaptor.getValue().getMessageType());
        verify(attachments, times(2)).save(any(ChatAttachment.class));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).put(keyCaptor.capture(), any(), eq("image/png"));
        assertTrue(keyCaptor.getAllValues().stream().allMatch(k -> k.startsWith("chat/800/") && k.endsWith(".png")));
        verify(chatMessageService).notifyChatRecipient(conversation, 20L,
                "Đã gửi hình ảnh/tệp đính kèm trong cuộc trò chuyện.");
    }

    // UTC058, UTC060, UTC061 - send rejects null request, mismatched duplicate hash, and cleans up keys when 2nd upload fails
    @Test
    void send_whenRequestNullOrHashDiffersOrSecondUploadFails_handlesErrorsAndCleansUp() {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "a.png", "image/png", new byte[] { 1 }),
                new MockMultipartFile("files", "b.png", "image/png", new byte[] { 2 }));
        // UTC058 - request=null throws CHAT_INPUT_INVALID
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.send(800L, null, files, user)).getErrorCode());

        // UTC060 - duplicate clientMessageId with different attachment hash throws CHAT_DUPLICATE_PAYLOAD
        UUID dupId = UUID.randomUUID();
        ChatMessage existing = new ChatMessage(800L, 20L, 1L, dupId, "ATTACHMENT", null);
        ReflectionTestUtils.setField(existing, "id", 911L);
        ChatAttachment existingAtt = new ChatAttachment(911L, "k1", "a.png", "image/png", 1L, "old-hash", 0);
        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(chatMessageService.text(null)).thenReturn(null);
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, dupId))
                .thenReturn(Optional.of(existing));
        when(validator.validate(files)).thenReturn(List.of(
                new ChatAttachmentValidator.Upload(new byte[] { 1 }, "image/png", "png", "a.png", "different-hash")));
        when(attachments.findByMessageIdOrderBySortOrderAsc(911L)).thenReturn(List.of(existingAtt));

        assertEquals(ErrorCode.CHAT_DUPLICATE_PAYLOAD,
                assertThrows(AppException.class, () -> service.send(800L, new SendChatRequest(dupId, null), files, user))
                        .getErrorCode());
        verify(storage, never()).put(anyString(), any(), anyString());

        // UTC061 - storage.put failure on second file cleans up uploaded keys and rethrows
        UUID failId = UUID.randomUUID();
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, failId))
                .thenReturn(Optional.empty());
        when(validator.validate(files)).thenReturn(List.of(
                new ChatAttachmentValidator.Upload(new byte[] { 1 }, "image/png", "png", "a.png", "h1"),
                new ChatAttachmentValidator.Upload(new byte[] { 2 }, "image/png", "png", "b.png", "h2")));
        doNothing().doThrow(new RuntimeException("S3 put failed on 2nd file"))
                .when(storage).put(anyString(), any(), anyString());

        assertThrows(RuntimeException.class,
                () -> service.send(800L, new SendChatRequest(failId, null), files, user));
        verify(storage, times(2)).delete(anyString());
    }

    // UTC065 - download throws STORAGE_UNAVAILABLE when S3 getObject fails
    @Test
    void download_whenS3ClientThrows_throwsStorageUnavailable() {
        ChatAttachment att = new ChatAttachment(900L, "chat/800/a.png", "a.png", "image/png", 3L, "h1", 0);
        ReflectionTestUtils.setField(att, "id", 55L);
        ChatMessage msg = new ChatMessage(800L, 10L, 1L, UUID.randomUUID(), "ATTACHMENT", null);
        ReflectionTestUtils.setField(msg, "id", 900L);

        when(policy.actor(user)).thenReturn(claimant);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(attachments.findById(55L)).thenReturn(Optional.of(att));
        when(messages.findById(900L)).thenReturn(Optional.of(msg));
        when(s3Clients.getIfAvailable()).thenReturn(s3Client);
        when(r2PropsProvider.getIfAvailable())
                .thenReturn(new R2Properties(true, "https://r2.example", "bucket-1", "ak", "sk"));
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(new RuntimeException("S3 IO error"));

        assertEquals(ErrorCode.STORAGE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.download(800L, 55L, user)).getErrorCode());
    }
}

