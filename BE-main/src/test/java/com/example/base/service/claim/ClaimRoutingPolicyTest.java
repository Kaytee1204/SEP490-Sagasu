package com.example.base.service.claim;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Claim;
import com.example.base.entity.ItemReceipt;
import com.example.base.entity.Post;
import com.example.base.entity.StorageLocation;
import com.example.base.repository.ItemReceiptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One test per UTC of sheet "Claim" for ClaimRoutingPolicy (UTC071-UTC082). */
@ExtendWith(MockitoExtension.class)
class ClaimRoutingPolicyTest {

    @Mock
    private ItemReceiptRepository receipts;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ClaimRoutingPolicy policy;

    private final Account owner = account(10L, AccountRole.USER);
    private final Account claimant = account(20L, AccountRole.USER);
    private final Account approver = account(30L, AccountRole.APPROVER);

    @Test
    void utc071_canSubmit_p2pVisibleOpenFoundPostReturnsTrue() {
        assertTrue(policy.canSubmit(foundPost(1L, "P2P")));
    }

    @Test
    void utc072_canSubmit_p2pPostWithReceiptReturnsFalse() {
        Post p2p = foundPost(3L, "P2P");
        when(receipts.findByPostId(3L)).thenReturn(Optional.of(officeReceipt(3L)));
        assertFalse(policy.canSubmit(p2p));
    }

    @Test
    void utc073_canSubmit_officePostWithStoredReceiptReturnsTrue() {
        when(receipts.findByPostId(5L)).thenReturn(Optional.of(officeReceipt(5L)));
        assertTrue(policy.canSubmit(foundPost(5L, "OFFICE")));
    }

    @Test
    void utc074_canSubmit_officePostWithoutActiveReceiptReturnsFalse() {
        Post officePost = foundPost(5L, "OFFICE");
        when(receipts.findByPostId(5L)).thenReturn(Optional.empty());
        assertFalse(policy.canSubmit(officePost));

        ItemReceipt closed = officeReceipt(5L);
        ReflectionTestUtils.setField(closed, "status", "CLOSED");
        when(receipts.findByPostId(5L)).thenReturn(Optional.of(closed));
        assertFalse(policy.canSubmit(officePost));
    }

    @Test
    void utc075_canSubmit_deletedOrUnclaimablePostReturnsFalse() {
        Post deleted = foundPost(4L, "P2P");
        ReflectionTestUtils.setField(deleted, "deletedAt", OffsetDateTime.now());
        assertFalse(policy.canSubmit(deleted));

        // other non-claimable shapes: LOST type, CLOSED status, HIDDEN moderation
        assertFalse(policy.canSubmit(Post.lost(owner, category(), "Lost", "Desc", "Hall", OffsetDateTime.now().minusDays(1))));
        Post closed = foundPost(2L, "P2P");
        ReflectionTestUtils.setField(closed, "status", "CLOSED");
        assertFalse(policy.canSubmit(closed));
        Post hidden = foundPost(3L, "P2P");
        ReflectionTestUtils.setField(hidden, "moderationStatus", "HIDDEN");
        assertFalse(policy.canSubmit(hidden));
    }

    @Test
    void utc076_canSubmit_moderationPendingReturnsFalse() {
        Post pending = foundPost(4L, "P2P");
        ReflectionTestUtils.setField(pending, "moderationStatus", "PENDING");
        assertFalse(policy.canSubmit(pending));
        verifyNoInteractions(receipts);
    }

    @Test
    void utc077_matches_p2pClaimTypeOnOfficePostReturnsFalse() {
        Post p2pPost = foundPost(1L, "P2P");
        Claim p2pClaim = Claim.submit(p2pPost, claimant, "desc", null);
        when(receipts.findByPostId(1L)).thenReturn(Optional.empty());
        assertTrue(policy.matches(p2pClaim, Set.of("OPEN")));
        assertFalse(policy.matches(p2pClaim, Set.of("RESERVED")));
        p2pPost.reserveForClaim();
        assertTrue(policy.matches(p2pClaim, Set.of("RESERVED")));

        Post officePost = foundPost(2L, "OFFICE");
        ItemReceipt stored = officeReceipt(2L);
        Claim officeClaim = Claim.submit(officePost, claimant, "desc", null, stored);
        when(receipts.findByPostId(2L)).thenReturn(Optional.of(stored));
        assertTrue(policy.matches(officeClaim, Set.of("OPEN")));

        ReflectionTestUtils.setField(officeClaim, "claimType", "P2P");
        assertFalse(policy.matches(officeClaim, Set.of("OPEN")));
    }

    @Test
    void utc078_reviewer_p2pPostOwnerReturnsTrue() {
        Claim p2pClaim = Claim.submit(foundPost(1L, "P2P"), claimant, "Mine", null);
        assertTrue(policy.reviewer(p2pClaim, owner));
        assertFalse(policy.reviewer(p2pClaim, approver));
    }

    @Test
    void utc079_reviewer_officeClaimOnlyApproverReturnsTrue() {
        Claim officeClaim = officeClaim();
        assertTrue(policy.reviewer(officeClaim, approver));
        assertFalse(policy.reviewer(officeClaim, owner));
        assertFalse(policy.reviewer(officeClaim, account(35L, AccountRole.MANAGER)));
        assertFalse(policy.reviewer(officeClaim, account(40L, AccountRole.ADMIN)));
    }

    @Test
    void utc080_reviewer_claimantReturnsFalse() {
        Claim p2pClaim = Claim.submit(foundPost(1L, "P2P"), claimant, "Mine", null);
        assertFalse(policy.reviewer(p2pClaim, claimant));
    }

    @Test
    void utc081_lockReceipt_existingReceiptRefreshedWithPessimisticWrite() {
        ItemReceipt stored = officeReceipt(2L);
        when(receipts.findByPostId(2L)).thenReturn(Optional.of(stored));

        assertSame(stored, policy.lockReceipt(foundPost(2L, "OFFICE")));
        verify(entityManager).refresh(stored, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void utc082_lockReceipt_missingReceiptReturnsNullWithoutRefresh() {
        when(receipts.findByPostId(2L)).thenReturn(Optional.empty());
        assertNull(policy.lockReceipt(foundPost(2L, "OFFICE")));
        verifyNoInteractions(entityManager);
    }

    private Claim officeClaim() {
        Claim c = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(c, "claimType", "OFFICE");
        ReflectionTestUtils.setField(c, "post", foundPost(1L, "P2P"));
        ReflectionTestUtils.setField(c, "claimant", claimant);
        return c;
    }

    private static Account account(Long id, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@gmail.com");
        a.setFullName("User " + id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static Category category() {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", 1L);
        c.setName("Wallet");
        return c;
    }

    private Post foundPost(Long id, String custodyMode) {
        Post p = Post.found(owner, category(), "Title", "Desc", "Hall A",
                OffsetDateTime.now().minusDays(1), custodyMode, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "moderationStatus", "VISIBLE");
        return p;
    }

    private static ItemReceipt officeReceipt(Long postId) {
        Account officer = account(99L, AccountRole.APPROVER);
        ItemReceipt r = ItemReceipt.walkInDraft("VP-1", officer);
        r.edit(category(), "Title", "Desc", "Good", "Hall A", OffsetDateTime.now().minusDays(1), null, null);
        r.assignLocation(StorageLocation.create("A-01", "Shelf A1", null));
        r.receiveAndPublish(officer, postId);
        return r;
    }
}
