package com.example.base.service.chat;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ChatConversation;
import com.example.base.entity.ChatParticipant;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ChatConversationRepository;
import com.example.base.repository.ChatParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationServiceTest {

    @Mock
    private ChatConversationRepository conversations;
    @Mock
    private ChatParticipantRepository participants;
    @Mock
    private ChatEvents events;
    @Mock
    private ChatAccessPolicy policy;

    @InjectMocks
    private ChatConversationService service;

    private Account finder;
    private Account claimant;
    private Post post;
    private Claim claim;

    @BeforeEach
    void setUp() {
        finder = account(10L, "Finder");
        claimant = account(20L, "Claimant");
        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 1L);
        cat.setName("Wallet");

        post = Post.found(finder, cat, "Wallet", "Black", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 100L);

        claim = Claim.submit(post, claimant, "Mine", null);
        ReflectionTestUtils.setField(claim, "id", 500L);
        claim.review("APPROVED");
    }

    @Test
    void canOpen_delegatesToPolicy() {
        when(policy.eligible(claim)).thenReturn(true);
        assertTrue(service.canOpen(claim));

        when(policy.eligible(claim)).thenReturn(false);
        assertFalse(service.canOpen(claim));
    }

    @Test
    void forApprovedClaim_whenNewConversation_createsConversationAndTwoParticipants() {
        when(policy.eligible(claim)).thenReturn(true);
        when(conversations.findByPost_IdAndClaimant_Id(100L, 20L)).thenReturn(Optional.empty());
        when(conversations.saveAndFlush(any(ChatConversation.class))).thenAnswer(inv -> {
            ChatConversation c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 800L);
            return c;
        });

        ChatConversation result = service.forApprovedClaim(claim);

        assertEquals(800L, result.getId());
        verify(participants, times(2)).save(any(ChatParticipant.class));
    }

    @Test
    void forApprovedClaim_whenExistingConversation_updatesLatestClaimWithoutDuplicatingParticipants() {
        ChatConversation existing = new ChatConversation(claim);
        ReflectionTestUtils.setField(existing, "id", 800L);

        Claim newerClaim = Claim.submit(post, claimant, "Retry claim", null);
        ReflectionTestUtils.setField(newerClaim, "id", 505L);
        newerClaim.review("APPROVED");

        when(policy.eligible(newerClaim)).thenReturn(true);
        when(conversations.findByPost_IdAndClaimant_Id(100L, 20L)).thenReturn(Optional.of(existing));

        ChatConversation result = service.forApprovedClaim(newerClaim);

        assertSame(existing, result);
        assertEquals(505L, existing.getLatestClaim().getId());
        verify(participants, never()).save(any());
        verify(events).changed(800L, "CONVERSATION_UPDATED");
    }

    @Test
    void forApprovedClaim_whenNotEligible_throwsChatUnavailable() {
        when(policy.eligible(claim)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.forApprovedClaim(claim));
        assertEquals(ErrorCode.CHAT_UNAVAILABLE, ex.getErrorCode());
        verifyNoInteractions(conversations);
    }

    @Test
    void notifyChanged_whenConversationExists_emitsConversationUpdatedEvent() {
        ChatConversation existing = new ChatConversation(claim);
        ReflectionTestUtils.setField(existing, "id", 800L);
        when(conversations.findByPost_IdAndClaimant_Id(100L, 20L)).thenReturn(Optional.of(existing));

        service.notifyChanged(claim);

        verify(events).changed(800L, "CONVERSATION_UPDATED");
    }

    @Test
    void notifyChanged_whenNoConversationExists_publishesNoEvent() {
        when(conversations.findByPost_IdAndClaimant_Id(100L, 20L)).thenReturn(Optional.empty());

        service.notifyChanged(claim);

        verifyNoInteractions(events);
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
}

