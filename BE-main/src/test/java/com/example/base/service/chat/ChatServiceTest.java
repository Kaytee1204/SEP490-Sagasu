package com.example.base.service.chat;

import com.example.base.dto.chat.ChatDto;
import com.example.base.dto.chat.ChatPageDto;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ChatConversation;
import com.example.base.entity.ChatParticipant;
import com.example.base.entity.ChatParticipantId;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ChatConversationRepository;
import com.example.base.repository.ChatParticipantRepository;
import com.example.base.repository.ChatQueryRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    private ChatConversationRepository conversations;
    @Mock
    private ChatParticipantRepository participants;
    @Mock
    private ClaimRepository claims;
    @Mock
    private PostRepository posts;
    @Mock
    private ChatAccessPolicy policy;
    @Mock
    private ChatConversationService chatConversations;
    @Mock
    private EntityManager em;
    @Mock
    private ChatQueryRepository queries;

    @InjectMocks
    private ChatService service;

    private Account finder;
    private Account claimant;
    private Account outsider;
    private CustomUserDetails finderUser;
    private CustomUserDetails claimantUser;
    private CustomUserDetails outsiderUser;
    private Post post;
    private Claim claim;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        finder = account(10L, "Finder");
        claimant = account(20L, "Claimant");
        outsider = account(30L, "Outsider");
        finderUser = new CustomUserDetails(finder);
        claimantUser = new CustomUserDetails(claimant);
        outsiderUser = new CustomUserDetails(outsider);

        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 1L);
        cat.setName("Wallet");

        post = Post.found(finder, cat, "Black wallet", "Leather", "Hall A", OffsetDateTime.now().minusDays(1), "P2P",
                null);
        ReflectionTestUtils.setField(post, "id", 100L);

        claim = Claim.submit(post, claimant, "Mine", null);
        ReflectionTestUtils.setField(claim, "id", 500L);
        claim.review("APPROVED");

        conversation = new ChatConversation(claim);
        ReflectionTestUtils.setField(conversation, "id", 800L);
        ReflectionTestUtils.setField(conversation, "lastMessageSequence", 12L);
    }

    // UTC013 - list slices results to size and computes nextCursor when more rows exist
    @Test
    void list_whenMoreThanSizeReturned_setsNextCursorAndSlicesToSize() {
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(1);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(2);
        OffsetDateTime t3 = OffsetDateTime.now().minusMinutes(3);
        List<ChatPageDto.Summary> rows = List.of(
                new ChatPageDto.Summary(3L, 100L, "Post 3", "Finder", "hi", t1, 1L, true),
                new ChatPageDto.Summary(2L, 100L, "Post 2", "Finder", "yo", t2, 0L, true),
                new ChatPageDto.Summary(1L, 100L, "Post 1", "Finder", "old", t3, 0L, false));

        when(policy.actor(claimantUser)).thenReturn(claimant);
        when(queries.list(eq(20L), isNull(), eq(0L), eq(3))).thenReturn(rows);

        ChatPageDto page = service.list(null, 2, claimantUser);

        assertEquals(2, page.items().size());
        assertNotNull(page.nextCursor());
    }

    @Test
    void utc015_list_sizeZeroThrowsInputInvalid() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.list(null, 0, claimantUser)).getErrorCode());
    }

    @Test
    void utc016_list_size51ThrowsInputInvalid() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.list(null, 51, claimantUser)).getErrorCode());
    }

    @Test
    void utc019_list_malformedCursorThrowsInputInvalid() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.list("not-base64!!!", 20, claimantUser)).getErrorCode());
    }

    @Test
    void utc026_detail_participantSeesOtherPartyAndWatermarks() {
        ChatParticipant me = new ChatParticipant(800L, 20L, "CLAIMANT");
        ReflectionTestUtils.setField(me, "lastReadSequence", 5L);
        ChatParticipant other = new ChatParticipant(800L, 10L, "FINDER");
        ReflectionTestUtils.setField(other, "lastReadSequence", 12L);
        when(policy.actor(claimantUser)).thenReturn(claimant);
        when(participants.existsById(new ChatParticipantId(800L, 20L))).thenReturn(true);
        when(conversations.findById(800L)).thenReturn(Optional.of(conversation));
        when(participants.findByConversationId(800L)).thenReturn(List.of(me, other));
        when(policy.readOnlyReason(conversation)).thenReturn(null);

        ChatDto dto = service.detail(800L, claimantUser);

        assertEquals(800L, dto.id());
        assertEquals(10L, dto.otherAccountId());
        assertEquals("Finder", dto.otherName());
        assertTrue(dto.canSend());
        assertNull(dto.readOnlyReason());
        assertEquals(5L, dto.lastReadSequence());
        assertEquals(12L, dto.otherReadSequence());
    }

    @Test
    void utc027_detail_nonParticipantThrowsChatNotFound() {
        when(policy.actor(outsiderUser)).thenReturn(outsider);
        assertEquals(ErrorCode.CHAT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.detail(800L, outsiderUser)).getErrorCode());
    }

    // UTC022 - open as claimant opens or creates conversation and returns ChatDto
    @Test
    void open_whenParticipantAndApprovedClaim_opensOrCreatesConversationAndEmitsChatDto() {
        ChatParticipant me = new ChatParticipant(800L, 20L, "CLAIMANT");
        ChatParticipant other = new ChatParticipant(800L, 10L, "FINDER");

        when(policy.actor(claimantUser)).thenReturn(claimant);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
        when(claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(100L, "APPROVED"))
                .thenReturn(Optional.of(claim));
        when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
        when(policy.eligible(claim)).thenReturn(true);
        when(chatConversations.forApprovedClaim(claim)).thenReturn(conversation);
        when(participants.findByConversationId(800L)).thenReturn(List.of(me, other));
        when(policy.readOnlyReason(conversation)).thenReturn("Chỉ xem");

        ChatDto dto = service.open(100L, claimantUser);

        assertEquals(800L, dto.id());
        assertFalse(dto.canSend());
        assertEquals("Chỉ xem", dto.readOnlyReason());
        verify(em).refresh(post, LockModeType.PESSIMISTIC_WRITE);
        verify(em).refresh(claim, LockModeType.PESSIMISTIC_WRITE);
    }

    // UTC023 - open throws CHAT_NOT_FOUND when actor is neither post owner nor claimant
    @Test
    void open_whenNonParticipant_throwsChatNotFound() {
        when(policy.actor(outsiderUser)).thenReturn(outsider);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
        when(claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(100L, "APPROVED"))
                .thenReturn(Optional.of(claim));

        AppException ex = assertThrows(AppException.class, () -> service.open(100L, outsiderUser));
        assertEquals(ErrorCode.CHAT_NOT_FOUND, ex.getErrorCode());
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

    private static final OffsetDateTime TS = OffsetDateTime.parse("2026-11-10T10:00:00Z");

    private List<ChatPageDto.Summary> fiveRows() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new ChatPageDto.Summary(i, 100L, "Item", "Finder", "Hi", TS.minusMinutes(i), 0, true))
                .toList();
    }

    @Test
    void utc014_list_lastPageHasNoNextCursor() {
        List<ChatPageDto.Summary> rows = fiveRows();
        when(queries.list(20L, null, 0L, 21)).thenReturn(rows);
        ChatPageDto page = service.list(null, 20, claimantUser);
        assertEquals(5, page.items().size());
        assertNull(page.nextCursor());
    }

    @Test
    void utc017_list_boundarySize50Accepted() {
        List<ChatPageDto.Summary> rows = fiveRows();
        when(queries.list(20L, null, 0L, 51)).thenReturn(rows);
        assertEquals(5, service.list(null, 50, claimantUser).items().size());
    }

    @Test
    void utc018_list_validCursorForwardedToRepository() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        when(queries.list(20L, TS, 15L, 21)).thenReturn(List.of());
        assertTrue(service.list("2026-11-10T10:00:00Z|15", 20, claimantUser).items().isEmpty());
        verify(queries).list(20L, TS, 15L, 21);
    }

    @Test
    void utc020_list_cursorWithIdZeroThrowsInputInvalid() {
        when(policy.actor(claimantUser)).thenReturn(claimant);
        assertEquals(ErrorCode.CHAT_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.list("2026-11-10T10:00:00Z|0", 20, claimantUser)).getErrorCode());
    }

    @Test
    void utc021_open_postOwnerOpensConversation() {
        stubFinderOpen();
        when(chatConversations.forApprovedClaim(claim)).thenReturn(conversation);
        when(participants.findByConversationId(800L)).thenReturn(List.of(
                new ChatParticipant(800L, 10L, "FINDER"), new ChatParticipant(800L, 20L, "CLAIMANT")));

        ChatDto opened = service.open(100L, finderUser);
        assertEquals(800L, opened.id());
        assertEquals(20L, opened.otherAccountId());
    }

    @Test
    void utc024_open_noApprovedClaimThrowsUnavailable() {
        stubFinderOpen();
        when(claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(100L, "APPROVED")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CHAT_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.open(100L, finderUser)).getErrorCode());
    }

    @Test
    void utc025_open_ineligibleApprovedClaimThrowsUnavailable() {
        stubFinderOpen();
        when(policy.eligible(claim)).thenReturn(false);
        assertEquals(ErrorCode.CHAT_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.open(100L, finderUser)).getErrorCode());
    }

    /** Finder opening chat on post 100 whose latest APPROVED claim is eligible. */
    private void stubFinderOpen() {
        when(policy.actor(finderUser)).thenReturn(finder);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
        when(claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(100L, "APPROVED")).thenReturn(Optional.of(claim));
        when(claims.findForUpdate(500L)).thenReturn(Optional.of(claim));
        when(policy.eligible(claim)).thenReturn(true);
    }

    // UTC028 - requireMember throws CHAT_NOT_FOUND when conversationId <= 0
    @Test
    void requireMember_whenConversationIdNonPositive_throwsChatNotFound() {
        assertEquals(ErrorCode.CHAT_NOT_FOUND, assertThrows(AppException.class,
                () -> service.requireMember(0L, 7L)).getErrorCode());
    }
}
