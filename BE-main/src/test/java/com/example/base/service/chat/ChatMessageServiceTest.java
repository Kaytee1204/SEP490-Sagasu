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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
@MockitoSettings(strictness = Strictness.LENIENT)
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

        // UTC271 - text enforces custom maxLength=2000 (default 4000 also verified)
        @Test
        void utc271_text_enforcesConfiguredMaxLengthDefault4000AndCustom2000() {
                assertEquals("hello", service.text("  hello  "));

                String len4000 = "a".repeat(4000);
                assertEquals(len4000, service.text(len4000));

                String len4001 = "a".repeat(4001);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.text(len4001)).getErrorCode());

                assertNull(service.text("   "));

                // UTC271 - configured maxLength=2000 rejects 2001 characters with CHAT_INPUT_INVALID
                properties.setMessageMaxLength(2000);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                                assertThrows(AppException.class, () -> service.text("a".repeat(2001))).getErrorCode());
        }

        // UTC266 - send saves trimmed message, increments sequence, emits MESSAGE_CREATED, and notifies recipient
        @Test
        void utc266_send_whenNewValidTextMessage_persistsIncrementsSequenceEmitsEventAndNotifiesRecipient() {
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
        void utc272_send_sameClientIdAndBodyReturnsExisting() {
                UUID clientMsgId = stubExistingTextMessage("Same body");
                ChatMessageDto reused = service.send(800L, new SendChatRequest(clientMsgId, "Same body"), claimantUser);
                assertEquals(901L, reused.id());
                verify(messages, never()).saveAndFlush(any());
        }

        @Test
        void utc273_send_sameClientIdDifferentBodyThrowsDuplicatePayload() {
                UUID clientMsgId = stubExistingTextMessage("Same body");
                assertEquals(ErrorCode.CHAT_DUPLICATE_PAYLOAD, assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(clientMsgId, "Different body"), claimantUser))
                                .getErrorCode());
        }

        /** Claimant already sent message 901 with the returned clientMessageId and the given body. */
        private UUID stubExistingTextMessage(String body) {
                UUID clientMsgId = UUID.randomUUID();
                ChatMessage existing = new ChatMessage(800L, 20L, 6L, clientMsgId, "TEXT", body);
                ReflectionTestUtils.setField(existing, "id", 901L);
                when(policy.actor(claimantUser)).thenReturn(claimant);
                stubWritableLock(20L);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, clientMsgId))
                                .thenReturn(Optional.of(existing));
                when(attachments.findByMessageIdOrderBySortOrderAsc(901L)).thenReturn(List.of());
                return clientMsgId;
        }

        // UTC274 - send throws CHAT_UNAVAILABLE when conversation is read-only
        @Test
        void utc274_send_whenConversationReadOnly_throwsChatUnavailable() {
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

        private static Account account(Long id, String name) {
                Account a = new Account();
                a.setId(id);
                a.setEmail(name.toLowerCase() + "@gmail.com");
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
        void utc267_send_nullRequestThrowsInputInvalid() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, null, claimantUser)).getErrorCode());
        }

        @Test
        void utc268_send_nullClientMessageIdThrowsInputInvalid() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(null, "hi"), claimantUser)).getErrorCode());
        }

        @Test
        void utc269_send_blankBodyThrowsInputInvalid() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.send(800L, new SendChatRequest(UUID.randomUUID(), "   "), claimantUser))
                                .getErrorCode());
        }

        // UTC270 - text accepts boundary 2000 characters when ChatProperties.messageMaxLength=2000
        @Test
        void utc270_text_whenConfigured2000AndBodyAt2000Chars_acceptsBoundary() {
                properties.setMessageMaxLength(2000);
                String exact2000 = "m".repeat(2000);

                assertEquals(exact2000, service.text(exact2000));
        }

        // UTC275 - send truncates notification preview to 120 chars + "..." when body is 150 chars
        @Test
        void utc275_send_whenBody150Chars_truncatesNotificationPreviewTo120PlusEllipsis() {
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

        // ---------------------------------------------------------------- sendLocation (UC-38 'Chia sẻ vị trí')

        /** Claimant sends a new location into the writable conversation 800; the saved message gets id 930. */
        private UUID stubNewLocation() {
                UUID reqId = UUID.randomUUID();
                when(policy.actor(claimantUser)).thenReturn(claimant);
                stubWritableLock(20L);
                when(policy.readOnlyReason(conversation)).thenReturn(null);
                when(messages.findByConversationIdAndSenderIdAndClientMessageId(800L, 20L, reqId))
                                .thenReturn(Optional.empty());
                when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(inv -> {
                        ChatMessage m = inv.getArgument(0);
                        ReflectionTestUtils.setField(m, "id", 930L);
                        ReflectionTestUtils.setField(m, "createdAt", OffsetDateTime.now());
                        return m;
                });
                return reqId;
        }

        private void assertLocationInvalid(Double lat, Double lon, String label) {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.sendLocation(800L, new ChatLocationRequest(UUID.randomUUID(), lat, lon, label),
                                                claimantUser)).getErrorCode());
                verify(messages, never()).saveAndFlush(any());
        }

        private void stubMember() {
                when(policy.actor(claimantUser)).thenReturn(claimant);
                when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        }

        private void stubWritableLock(long actorId) {
                when(chatService.requireMember(800L, actorId)).thenReturn(conversation);
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
                when(conversations.findForUpdate(800L)).thenReturn(Optional.of(conversation));
        }
}
