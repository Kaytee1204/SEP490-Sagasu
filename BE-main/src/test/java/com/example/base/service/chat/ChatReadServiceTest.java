package com.example.base.service.chat;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ChatConversation;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ChatMessageRepository;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatReadServiceTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ChatAccessPolicy policy;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private ChatEvents events;
    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ChatReadService service;

    private Account actor;
    private CustomUserDetails user;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        actor = new Account();
        actor.setId(20L);
        actor.setEmail("claimant@fpt.edu.vn");
        actor.setFullName("Claimant");
        actor.setRole(AccountRole.USER);
        actor.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(actor);

        Account finder = new Account();
        finder.setId(10L);
        finder.setEmail("finder@fpt.edu.vn");
        finder.setFullName("Finder");
        finder.setRole(AccountRole.USER);
        finder.setStatus(AccountStatus.ACTIVE);

        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 1L);
        cat.setName("Wallet");

        Post post = Post.found(finder, cat, "Wallet", "Black", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        Claim claim = Claim.submit(post, actor, "Mine", null);
        conversation = new ChatConversation(claim);
        ReflectionTestUtils.setField(conversation, "id", 800L);
        ReflectionTestUtils.setField(conversation, "lastMessageSequence", 10L);
    }

    @Test
    void read_whenNegativeOrExceedsLastSequence_throwsChatInputInvalid() {
        when(policy.actor(user)).thenReturn(actor);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);

        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.read(800L, -1L, user)).getErrorCode());

        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.read(800L, 11L, user)).getErrorCode());
    }

    @Test
    void read_whenAlreadyAtOrBeyondTargetSequence_returnsCurrentWithoutUpdatesOrEvents() {
        when(policy.actor(user)).thenReturn(actor);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(messages.existsByConversationIdAndSequenceNo(800L, 5L)).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(800L), eq(20L))).thenReturn(5L);

        long res = service.read(800L, 5L, user);

        assertEquals(5L, res);
        verifyNoInteractions(events);
    }

    @Test
    void read_whenNewWatermarkHigher_updatesParticipantMessagesAndEmitsEvent() {
        when(policy.actor(user)).thenReturn(actor);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(messages.existsByConversationIdAndSequenceNo(800L, 6L)).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(800L), eq(20L))).thenReturn(2L);

        long res = service.read(800L, 6L, user);

        assertEquals(6L, res);
        verify(jdbc).update(anyString(), any(Timestamp.class), eq(800L), eq(20L), eq(2L), eq(6L));
        verify(jdbc).update(anyString(), eq(6L), any(Timestamp.class), eq(800L), eq(20L));
        verify(events).changed(800L, "READ_UPDATED");
    }

    @Test
    void read_whenTargetSequenceMissingOrZero_throwsOrSkipsExistenceCheck() {
        when(policy.actor(user)).thenReturn(actor);
        when(chatService.requireMember(800L, 20L)).thenReturn(conversation);
        when(messages.existsByConversationIdAndSequenceNo(800L, 99L)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.read(800L, 99L, user));
        assertEquals(ErrorCode.CHAT_INPUT_INVALID, ex.getErrorCode());

        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(800L), eq(20L))).thenReturn(0L);
        assertEquals(0L, service.read(800L, 0L, user));
        verify(messages, never()).existsByConversationIdAndSequenceNo(800L, 0L);
        verifyNoInteractions(events);
    }
}

