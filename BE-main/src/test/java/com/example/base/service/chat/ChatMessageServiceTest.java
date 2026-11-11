package com.example.base.service.chat;

import com.example.base.config.chat.ChatProperties;
import com.example.base.dto.chat.ChatLocationRequest;
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
import com.example.base.repository.ChatConversationRepository;
import com.example.base.repository.ChatMessageRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.notification.NotificationService;
import com.example.base.storage.ImageStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

        @Mock
        private ChatService chatService;
        @Mock
        private ChatAccessPolicy policy;
        @Mock
        private ChatConversationRepository conversations;
        @Mock
        private ChatMessageRepository messages;
        @Mock
        private PostRepository posts;
        @Mock
        private ClaimRepository claims;
        @Mock
        private EntityManager em;
        @Mock
        private ChatEvents events;
        @Mock
        private ChatAttachmentRepository attachments;
        @Mock
        private ImageStorage storage;
        @Mock
        private JdbcTemplate jdbc;
        @Mock
        private NotificationService notifications;

        private ChatProperties properties;
        private ChatMessageService service;

        private Account finder;
        private Account claimant;
        private CustomUserDetails finderUser;
        private CustomUserDetails claimantUser;
        private Post post;
        private Claim claim;
        private ChatConversation conversation;

        @BeforeEach
        void setUp() {
                properties = new ChatProperties();
                service = new ChatMessageService(
                                chatService, policy, conversations, messages, posts, claims,
                                em, properties, events, attachments, storage, jdbc, notifications);

                finder = account(10L, "Finder");
                claimant = account(20L, "Claimant");
                finderUser = new CustomUserDetails(finder);
                claimantUser = new CustomUserDetails(claimant);

                Category cat = new Category();
                ReflectionTestUtils.setField(cat, "categoryId", 1L);
                cat.setName("Wallet");

                post = Post.found(finder, cat, "Black wallet", "Leather", "Hall A", OffsetDateTime.now().minusDays(1),
                                "P2P", null);
                ReflectionTestUtils.setField(post, "id", 100L);

                claim = Claim.submit(post, claimant, "Mine", null);
                ReflectionTestUtils.setField(claim, "id", 500L);
                claim.review("APPROVED");

                conversation = new ChatConversation(claim);
                ReflectionTestUtils.setField(conversation, "id", 800L);
                ReflectionTestUtils.setField(conversation, "lastMessageSequence", 5L);
        }

        @Test
        void text_enforcesConfiguredMaxLengthDefault4000AndCustom2000() {
                assertEquals("hello", service.text("  hello  "));

                String len4000 = "a".repeat(4000);
                assertEquals(len4000, service.text(len4000));

                String len4001 = "a".repeat(4001);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.text(len4001)).getErrorCode());

                assertNull(service.text("   "));

                properties.setMessageMaxLength(2000);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.text("a".repeat(2001))).getErrorCode());
        }

        @Test
        void history_whenBeforeAndAfterUnset_pagesBackwardInAscendingOrderWithHasMoreAndAttachments() {
                ChatMessage m3 = textMessage(3L, 3L, "third");
                ChatMessage m2 = textMessage(2L, 2L, "second");
                ChatMessage m1 = textMessage(1L, 1L, "first");
                ChatAttachment att = new ChatAttachment(2L, "chat/800/a.jpg", "a.jpg", "image/jpeg", 100L, "h", 0);
                ReflectionTestUtils.setField(att, "id", 90L);

                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
                when(messages.findByConversationIdAndSequenceNoLessThanOrderBySequenceNoDesc(
                                eq(800L), eq(Long.MAX_VALUE), eq(PageRequest.of(0, 3))))
                                .thenReturn(List.of(m3, m2, m1));
                when(attachments.findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(2L, 3L)))
                                .thenReturn(List.of(att));
                when(storage.readUrl("chat/800/a.jpg", null)).thenReturn("https://cdn/a.jpg");

                ChatMessageDto.Page page = service.history(800L, null, null, 2, claimantUser);

                assertTrue(page.hasMore());
                assertEquals(2L, page.nextBefore());
                assertEquals(2, page.items().size());
                assertEquals(2L, page.items().get(0).sequence());
                assertEquals(1, page.items().get(0).attachments().size());
                assertEquals(3L, page.items().get(1).sequence());
        }

        @Test
        void history_whenAfterSpecifiedAndBothCursorsProvided_handlesBranching() {
                ChatMessage m4 = textMessage(4L, 4L, "fourth");
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);

                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.history(800L, 5L, 1L, 10, claimantUser))
                                                .getErrorCode());

                when(messages.findByConversationIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
                                eq(800L), eq(3L), eq(PageRequest.of(0, 11))))
                                .thenReturn(List.of(m4));

                ChatMessageDto.Page page = service.history(800L, null, 3L, 10, claimantUser);
                assertFalse(page.hasMore());
                assertEquals(1, page.items().size());
                assertEquals(4L, page.items().get(0).sequence());
        }

        @Test
        void receipts_whenValidParams_returnsReceiptsFromJdbc() {
                OffsetDateTime now = OffsetDateTime.now();
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
                when(jdbc.query(anyString(), any(RowMapper.class), eq(800L), eq(20L), eq(1L), eq(5L), eq(11)))
                                .thenReturn(List.of(new ChatMessageDto.Receipt(2L, now)));

                ChatMessageDto.Receipts receipts = service.receipts(800L, 1L, 5L, 10, claimantUser);
                assertFalse(receipts.hasMore());
                assertEquals(1, receipts.items().size());
                assertEquals(2L, receipts.items().get(0).sequence());
        }

        @Test
        void send_whenNewValidTextMessage_persistsIncrementsSequenceEmitsEventAndNotifiesRecipient() {
                UUID clientMsgId = UUID.randomUUID();
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(policy.readOnlyReason(conversation)).thenReturn(null);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, clientMsgId))
                                .thenReturn(Optional.empty());
                when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", 900L);
                        return m;
                });

                ChatMessageDto dto = service.send(800L, new SendChatRequest(clientMsgId, "  I am at Hall C  "),
                                claimantUser);

                assertEquals(900L, dto.id());
                assertEquals(6L, dto.sequence());
                assertEquals("I am at Hall C", dto.body());
                verify(events).changed(800L, "MESSAGE_CREATED");
                verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(),
                                eq(false));
        }

        @Test
        void send_whenDuplicateClientMessageIdMatchesOrDiffers_returnsExistingOrThrowsConflict() {
                UUID clientMsgId = UUID.randomUUID();
                ChatMessage existing = new ChatMessage(800L, 20L, 6L, clientMsgId, "TEXT", "Same body");
                ReflectionTestUtils.setField(existing, "id", 901L);

                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, clientMsgId))
                                .thenReturn(Optional.of(existing));
                when(attachments.findByMessageIdOrderBySortOrderAsc(901L)).thenReturn(List.of());

                ChatMessageDto reused = service.send(800L, new SendChatRequest(clientMsgId, "Same body"), claimantUser);
                assertEquals(901L, reused.id());
                verify(messages, never()).saveAndFlush(any());

                assertEquals(ErrorCode.CHAT_DUPLICATE_PAYLOAD,
                                assertThrows(AppException.class,
                                                () -> service.send(800L,
                                                                new SendChatRequest(clientMsgId, "Different body"),
                                                                claimantUser))
                                                .getErrorCode());
        }

        @Test
        void send_whenConversationReadOnly_throwsChatUnavailable() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(policy.readOnlyReason(conversation)).thenReturn("Chỉ xem");

                AppException ex = assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(UUID.randomUUID(), "Hi"), claimantUser));
                assertEquals(ErrorCode.CHAT_UNAVAILABLE, ex.getErrorCode());
        }

        @Test
        void sendLocation_whenValidCoordinatesAndLabel_savesLocationMessageAndEmitsEvent() {
                UUID clientMsgId = UUID.randomUUID();
                when(policy.actor(finderUser)).thenReturn(finder);
                when(chatService.requireMember(800L, 10L)).thenReturn(conversation);
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(policy.readOnlyReason(conversation)).thenReturn(null);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 10L, clientMsgId))
                                .thenReturn(Optional.empty());
                when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", 902L);
                        return m;
                });

                ChatMessageDto dto = service.sendLocation(800L,
                                new ChatLocationRequest(clientMsgId, 21.012, 105.525, " Alpha Building "), finderUser);

                assertEquals(902L, dto.id());
                assertNotNull(dto.location());
                assertEquals("Alpha Building", dto.location().label());
                verify(events).changed(800L, "MESSAGE_CREATED");
                verify(notifications).notifyUser(eq(20L), anyString(), anyString(), anyString(), anyString(),
                                eq(false));
        }

        @Test
        void sendLocation_whenCoordinatesInvalid_throwsChatInputInvalid() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.sendLocation(800L,
                                                new ChatLocationRequest(UUID.randomUUID(), 95.0, 105.0, "Alpha"),
                                                claimantUser))
                                                .getErrorCode());
        }

        private static Account account(Long id, String name) {
                Account a = new Account();
                a.setId(id);
                a.setEmail(name.toLowerCase() + "@fpt.edu.vn");
                a.setFullName(name);
                a.setRole(AccountRole.USER);
                a.setStatus(AccountStatus.ACTIVE);
                return a;
        }

        private static ChatMessage textMessage(Long id, long seq, String body) {
                ChatMessage m = new ChatMessage(800L, 10L, seq, UUID.randomUUID(), "TEXT", body);
                ReflectionTestUtils.setField(m, "id", id);
                ReflectionTestUtils.setField(m, "createdAt", OffsetDateTime.now());
                return m;
        }

        @Test
        void send_whenInvalidPayload_throwsChatInputInvalid() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, null, claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(null, "hi"), claimantUser))
                                .getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(UUID.randomUUID(), "   "), claimantUser))
                                .getErrorCode());
        }

        @Test
        void text_whenConfigured2000AndBodyAt2000Chars_acceptsBoundary() {
                properties.setMessageMaxLength(2000);
                String exact2000 = "m".repeat(2000);

                assertEquals(exact2000, service.text(exact2000));
        }

        @Test
        void send_whenBody150Chars_truncatesNotificationPreviewTo120PlusEllipsis() {
                UUID reqId = UUID.randomUUID();
                String body150 = "k".repeat(150);
                stubWritableLock(20L);
                when(policy.readOnlyReason(conversation)).thenReturn(null);
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                                .thenReturn(Optional.empty());
                when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", 920L);
                        ReflectionTestUtils.setField(m, "createdAt", OffsetDateTime.now());
                        return m;
                });

                service.send(800L, new SendChatRequest(reqId, body150), claimantUser);

                ArgumentCaptor<String> previewCaptor = ArgumentCaptor.forClass(String.class);
                verify(notifications).notifyUser(eq(10L), eq("CHAT_MESSAGE"), anyString(), previewCaptor.capture(),
                                eq("/tin-nhan/800"), eq(false));
                assertEquals("k".repeat(120) + "...", previewCaptor.getValue());
        }

        @Test
        void sendLocation_whenNullLabelAndBoundary90_180_succeedsAndNotifiesDefaultPreview() {
                UUID reqId = UUID.randomUUID();
                stubWritableLock(20L);
                when(policy.readOnlyReason(conversation)).thenReturn(null);
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                                .thenReturn(Optional.empty());
                when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", 921L);
                        ReflectionTestUtils.setField(m, "createdAt", OffsetDateTime.now());
                        return m;
                });

                ChatMessageDto dto = service.sendLocation(800L, new ChatLocationRequest(reqId, 90.0, 180.0, null),
                                claimantUser);

                assertEquals(90.0, dto.location().latitude());
                assertEquals(180.0, dto.location().longitude());
                verify(notifications).notifyUser(eq(10L), eq("CHAT_MESSAGE"), anyString(),
                                eq("Đã chia sẻ vị trí hẹn gặp trên bản đồ."), eq("/tin-nhan/800"), eq(false));
        }

        @Test
        void sendLocation_whenInvalidCoordsLabelOrDuplicateDiffCoords_throwsExpectedError() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                UUID id = UUID.randomUUID();
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.sendLocation(800L, new ChatLocationRequest(id, 10.0, -180.0001, null),
                                                claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.sendLocation(800L, new ChatLocationRequest(id, Double.NaN, 106.7, null),
                                                claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.sendLocation(800L, new ChatLocationRequest(id, null, 106.7, null),
                                                claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.sendLocation(800L,
                                                new ChatLocationRequest(id, 10.7, 106.7, "x".repeat(201)),
                                                claimantUser)).getErrorCode());

                stubWritableLock(20L);
                ChatMessage previous = ChatMessage.location(800L, 20L, 6L, id, 10.0, 106.0, null);
                ReflectionTestUtils.setField(previous, "id", 922L);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, id))
                                .thenReturn(Optional.of(previous));

                assertEquals(ErrorCode.CHAT_DUPLICATE_PAYLOAD, assertThrows(AppException.class,
                                () -> service.sendLocation(800L, new ChatLocationRequest(id, 11.0, 106.0, null),
                                                claimantUser)).getErrorCode());
        }

        @Test
        void history_and_receipts_validateParamsMapPdfNullUrlAndSliceLimit() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);

                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.history(800L, 0L, null, 20, claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.history(800L, null, -1L, 20, claimantUser)).getErrorCode());
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.history(800L, null, null, 101, claimantUser)).getErrorCode());

                ChatMessage mixed = new ChatMessage(800L, 10L, 6L, UUID.randomUUID(), "ATTACHMENT", null);
                ReflectionTestUtils.setField(mixed, "id", 60L);
                ReflectionTestUtils.setField(mixed, "createdAt", OffsetDateTime.now());
                ChatAttachment img = new ChatAttachment(60L, "chat/800/a.png", "a.png", "image/png", 10L, "h1", 0);
                ReflectionTestUtils.setField(img, "id", 1L);
                ChatAttachment pdf = new ChatAttachment(60L, "chat/800/b.pdf", "b.pdf", "application/pdf", 20L, "h2",
                                1);
                ReflectionTestUtils.setField(pdf, "id", 2L);
                when(messages.findByConversationIdAndSequenceNoLessThanOrderBySequenceNoDesc(800L, Long.MAX_VALUE,
                                PageRequest.of(0, 21))).thenReturn(List.of(mixed));
                when(attachments.findByMessageIdInOrderByMessageIdAscSortOrderAsc(List.of(60L)))
                                .thenReturn(List.of(img, pdf));
                when(storage.readUrl("chat/800/a.png", null)).thenReturn("https://cdn/a.png");

                ChatMessageDto.Page page = service.history(800L, null, null, 20, claimantUser);
                assertEquals(1, page.items().size());
                assertEquals("https://cdn/a.png", page.items().get(0).attachments().get(0).url());
                assertNull(page.items().get(0).attachments().get(1).url());

                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.receipts(800L, 5L, 3L, 10, claimantUser)).getErrorCode());

                List<ChatMessageDto.Receipt> twelve = java.util.stream.LongStream.rangeClosed(1, 12)
                                .mapToObj(seq -> new ChatMessageDto.Receipt(seq, OffsetDateTime.now()))
                                .toList();
                when(jdbc.query(anyString(), any(RowMapper.class), eq(800L), eq(20L), eq(0L), eq(50L), eq(11)))
                                .thenReturn(twelve);
                ChatMessageDto.Receipts receipts = service.receipts(800L, 0L, 50L, 10, claimantUser);
                assertEquals(10, receipts.items().size());
                assertTrue(receipts.hasMore());
        }

        private void stubWritableLock(long actorId) {
                when(chatService.requireMember(800L, actorId)).thenReturn(conversation);
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
        }
}
