package com.example.base.service.chat;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ChatConversation;
import com.example.base.entity.Claim;
import com.example.base.entity.ItemReceipt;
import com.example.base.entity.Post;
import com.example.base.entity.StorageLocation;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessPolicyTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private ItemReceiptRepository receipts;

    @InjectMocks
    private ChatAccessPolicy policy;

    // UTC001 - actor returns active Account when principal is valid
    @Test
    void actor_whenValidActiveAccount_returnsAccount() {
        Account active = account(5L, AccountStatus.ACTIVE, null, null);
        when(accounts.findById(5L)).thenReturn(Optional.of(active));

        assertSame(active, policy.actor(new CustomUserDetails(active)));
    }

    // UTC002, UTC003 - actor throws UNAUTHORIZED when null or FORBIDDEN when suspended/locked
    @Test
    void actor_whenNullOrLockedOrMissing_throwsUnauthorizedOrForbidden() {
        // UTC002 - null principal throws UNAUTHORIZED
        assertEquals(ErrorCode.UNAUTHORIZED,
                assertThrows(AppException.class, () -> policy.actor(null)).getErrorCode());

        // UTC003 - suspended account throws FORBIDDEN
        Account suspended = account(5L, AccountStatus.SUSPENDED, null, null);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> policy.actor(new CustomUserDetails(suspended))).getErrorCode());

        // Supplementary (ChatAccessPolicy.actor): DB-locked account throws FORBIDDEN
        Account activePrincipal = account(6L, AccountStatus.ACTIVE, null, null);
        Account lockedInDb = account(6L, AccountStatus.ACTIVE, OffsetDateTime.now().plusHours(2), null);
        when(accounts.findById(6L)).thenReturn(Optional.of(lockedInDb));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> policy.actor(new CustomUserDetails(activePrincipal))).getErrorCode());
    }

    // Supplementary (ChatAccessPolicy.active): checks null, status, lock window, and validUntil
    @Test
    void active_checksStatusLockAndValidity() {
        assertThrows(NullPointerException.class, () -> policy.active(null));
        assertTrue(policy.active(account(1L, AccountStatus.ACTIVE, null, OffsetDateTime.now().plusDays(1))));
        assertFalse(policy.active(account(2L, AccountStatus.SUSPENDED, null, null)));
        assertFalse(policy.active(account(3L, AccountStatus.ACTIVE, OffsetDateTime.now().plusMinutes(10), null)));
        assertFalse(policy.active(account(4L, AccountStatus.ACTIVE, null, OffsetDateTime.now().minusMinutes(10))));
    }

    // UTC005, UTC006, UTC008, UTC009, UTC010 - eligible and readOnlyReason evaluate claim type, state, and receipt
    @Test
    void eligible_and_readOnlyReason_evaluateClaimAndPostAndAccounts() {
        Account finder = account(10L, AccountStatus.ACTIVE, null, null);
        Account claimant = account(20L, AccountStatus.ACTIVE, null, null);
        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 1L);
        cat.setName("Wallet");

        Post post = Post.found(finder, cat, "Wallet", "Desc", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 100L);
        ReflectionTestUtils.setField(post, "status", "RESERVED");

        // UTC005 - approved P2P claim without receipt is eligible
        Claim claim = Claim.submit(post, claimant, "Mine", null);
        claim.review("APPROVED");
        assertTrue(policy.eligible(claim));

        // UTC009 - eligible claim with active participants returns readOnlyReason=null
        ChatConversation conv = new ChatConversation(claim);
        assertNull(policy.readOnlyReason(conv));

        // UTC010 - cancelled claim returns non-blank read-only reason message
        claim.review("CANCELLED");
        assertFalse(policy.eligible(claim));
        assertTrue(policy.readOnlyReason(conv) != null && !policy.readOnlyReason(conv).isBlank());

        // UTC008 - OFFICE claim is ineligible
        claim.review("APPROVED");
        Claim officeClaim = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(officeClaim, "claimType", "OFFICE");
        ReflectionTestUtils.setField(officeClaim, "status", "APPROVED");
        ReflectionTestUtils.setField(officeClaim, "post", post);
        ReflectionTestUtils.setField(officeClaim, "claimant", claimant);
        assertFalse(policy.eligible(officeClaim));

        // UTC006 - claim whose post already has an ItemReceipt is ineligible
        ItemReceipt receipt = ItemReceipt.walkInDraft("VP-1", finder);
        receipt.assignLocation(StorageLocation.create("A1", "Shelf 1", null));
        when(receipts.existsByPostId(100L)).thenReturn(true);
        assertFalse(policy.eligible(claim));
    }

    private static Account account(Long id, AccountStatus status, OffsetDateTime lockedUntil, OffsetDateTime validUntil) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@fpt.edu.vn");
        a.setFullName("User " + id);
        a.setRole(AccountRole.USER);
        a.setStatus(status);
        a.setLockedUntil(lockedUntil);
        a.setValidUntil(validUntil);
        return a;
    }

    // UTC004 - active returns false when account role is null
    @Test
    void active_whenRoleNull_returnsFalse() {
        Account noRole = account(4L, AccountStatus.ACTIVE, null, null);
        noRole.setRole(null);

        assertFalse(policy.active(noRole));
    }

    // UTC007 - eligible returns false when claim status is PENDING
    @Test
    void eligible_whenClaimPending_returnsFalse() {
        Account owner = account(10L, AccountStatus.ACTIVE, null, null);
        Account claimant = account(20L, AccountStatus.ACTIVE, null, null);
        Category category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 1L);
        Post post = Post.found(owner, category, "Phone", "Black", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 100L);
        ReflectionTestUtils.setField(post, "moderationStatus", "VISIBLE");
        post.reserveForClaim();
        Claim pending = Claim.submit(post, claimant, "Mine", null);

        assertFalse(policy.eligible(pending));
    }

    // UTC011 - readOnlyReason returns participant-inactive message when post owner is SUSPENDED
    @Test
    void readOnlyReason_whenPostOwnerSuspended_returnsParticipantInactiveMessage() {
        Account owner = account(10L, AccountStatus.SUSPENDED, null, null);
        Account claimant = account(20L, AccountStatus.ACTIVE, null, null);
        Category category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 1L);
        Post post = Post.found(owner, category, "Phone", "Black", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(post, "id", 100L);
        ReflectionTestUtils.setField(post, "moderationStatus", "VISIBLE");
        post.reserveForClaim();
        Claim approved = Claim.submit(post, claimant, "Mine", null);
        ReflectionTestUtils.setField(approved, "status", "APPROVED");

        String reason = policy.readOnlyReason(new ChatConversation(approved));

        assertEquals("Một tài khoản hiện không thể tham gia trò chuyện. Lịch sử vẫn được giữ.", reason);
    }
}

