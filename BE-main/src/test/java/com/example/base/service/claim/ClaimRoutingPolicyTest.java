package com.example.base.service.claim;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Claim;
import com.example.base.entity.ItemReceipt;
import com.example.base.entity.Post;
import com.example.base.entity.StorageLocation;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimRoutingPolicyTest {

    @Mock
    private ItemReceiptRepository receipts;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ClaimRoutingPolicy policy;

    // UTC069 - canSubmit returns true for P2P visible OPEN FOUND post without receipt
    @Test
    void canSubmit_whenP2pVisibleOpenFoundPost_returnsTrue() {
        Post post = foundPost(1L, account(10L, AccountRole.USER), "P2P");
        assertTrue(policy.canSubmit(post));
    }

    // UTC073 - canSubmit returns false when deletedAt is set (plus Supplementary checks for LOST/CLOSED/HIDDEN)
    @Test
    void canSubmit_whenLostOrHiddenOrClosedOrDeleted_returnsFalse() {
        Account owner = account(10L, AccountRole.USER);
        Category cat = category(1L);

        // Supplementary (ClaimRoutingPolicy.canSubmit): non-FOUND (LOST) post returns false
        Post lost = Post.lost(owner, cat, "Lost", "Desc", "Hall", OffsetDateTime.now().minusDays(1));
        assertFalse(policy.canSubmit(lost));

        // Supplementary (ClaimRoutingPolicy.canSubmit): CLOSED post returns false
        Post closed = foundPost(2L, owner, "P2P");
        ReflectionTestUtils.setField(closed, "status", "CLOSED");
        assertFalse(policy.canSubmit(closed));

        // Supplementary (ClaimRoutingPolicy.canSubmit): HIDDEN moderation post returns false
        Post hidden = foundPost(3L, owner, "P2P");
        ReflectionTestUtils.setField(hidden, "moderationStatus", "HIDDEN");
        assertFalse(policy.canSubmit(hidden));

        // UTC073 - deletedAt set returns false
        Post deleted = foundPost(4L, owner, "P2P");
        ReflectionTestUtils.setField(deleted, "deletedAt", OffsetDateTime.now());
        assertFalse(policy.canSubmit(deleted));
    }

    // UTC071, UTC072 - canSubmit checks OFFICE receipt existence and storage status
    @Test
    void canSubmit_whenOfficePostWithOrWithoutReceivedReceipt_returnsExpectedResult() {
        Account owner = account(10L, AccountRole.USER);
        Post officePost = foundPost(5L, owner, "OFFICE");

        // Supplementary (ClaimRoutingPolicy.canSubmit): OFFICE post without receipt returns false
        when(receipts.findByPostId(5L)).thenReturn(Optional.empty());
        assertFalse(policy.canSubmit(officePost));

        // UTC071 - OFFICE post with accepted in-storage receipt returns true
        ItemReceipt stored = officeReceipt(5L, account(99L, AccountRole.MANAGER), category(1L));
        when(receipts.findByPostId(5L)).thenReturn(Optional.of(stored));
        assertTrue(policy.canSubmit(officePost));

        // UTC072 - OFFICE post with closed/returned receipt returns false
        ReflectionTestUtils.setField(stored, "status", "CLOSED");
        assertFalse(policy.canSubmit(officePost));
    }

    // UTC075 - matches rejects P2P claimType on OFFICE custody post and verifies active state
    @Test
    void matches_checksCustodyModePostStatusAndOfficeReceiptVerification() {
        Account owner = account(10L, AccountRole.USER);
        Account claimant = account(20L, AccountRole.USER);
        Account approver = account(30L, AccountRole.APPROVER);

        Post p2pPost = foundPost(1L, owner, "P2P");
        Claim p2pClaim = Claim.submit(p2pPost, claimant, "desc", null);
        when(receipts.findByPostId(1L)).thenReturn(Optional.empty());

        // Supplementary (ClaimRoutingPolicy.matches): verifies P2P post status matches allowed set
        assertTrue(policy.matches(p2pClaim, Set.of("OPEN")));
        assertFalse(policy.matches(p2pClaim, Set.of("RESERVED")));
        p2pPost.reserveForClaim();
        assertTrue(policy.matches(p2pClaim, Set.of("RESERVED")));

        // Supplementary (ClaimRoutingPolicy.matches): verifies OFFICE claim matches stored receipt
        Post officePost = foundPost(2L, owner, "OFFICE");
        ItemReceipt stored = officeReceipt(2L, approver, category(1L));
        Claim officeClaim = Claim.submit(officePost, claimant, "desc", null, stored);
        when(receipts.findByPostId(2L)).thenReturn(Optional.of(stored));
        assertTrue(policy.matches(officeClaim, Set.of("OPEN")));

        // UTC075 - claimType P2P on OFFICE custody post returns false
        ReflectionTestUtils.setField(officeClaim, "claimType", "P2P");
        assertFalse(policy.matches(officeClaim, Set.of("OPEN")));
    }

    // UTC076, UTC077, UTC078 - reviewer authorizes P2P post owner or OFFICE approver and rejects claimant
    @Test
    void reviewer_distinguishesP2pPostOwnerFromOfficeApproverOnly() {
        Account owner = account(10L, AccountRole.USER);
        Account claimant = account(20L, AccountRole.USER);
        Account approver = account(30L, AccountRole.APPROVER);
        Account manager = account(35L, AccountRole.MANAGER);
        Account admin = account(40L, AccountRole.ADMIN);

        Post p2pPost = foundPost(1L, owner, "P2P");
        Claim p2pClaim = Claim.submit(p2pPost, claimant, "Mine", null);

        // UTC076 - P2P claim reviewed by post owner returns true
        assertTrue(policy.reviewer(p2pClaim, owner));
        // UTC078 - actor is claimant returns false
        assertFalse(policy.reviewer(p2pClaim, claimant));
        assertFalse(policy.reviewer(p2pClaim, approver));

        Claim officeClaim = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(officeClaim, "claimType", "OFFICE");
        ReflectionTestUtils.setField(officeClaim, "post", p2pPost);
        ReflectionTestUtils.setField(officeClaim, "claimant", claimant);

        // Supplementary (ClaimRoutingPolicy.reviewer): OFFICE claim rejects P2P post owner
        assertFalse(policy.reviewer(officeClaim, owner));
        // UTC077 - OFFICE claim reviewed by APPROVER returns true (MANAGER/ADMIN false)
        assertTrue(policy.reviewer(officeClaim, approver));
        assertFalse(policy.reviewer(officeClaim, manager));
        assertFalse(policy.reviewer(officeClaim, admin));
    }

    // UTC079 - lockReceipt returns null when absent for P2P and acquires PESSIMISTIC_WRITE lock when present
    @Test
    void lockReceipt_forMissingReturnsNullAndForPresentRefreshesPessimisticWrite() {
        Account owner = account(10L, AccountRole.USER);
        Post p2p = foundPost(1L, owner, "P2P");
        when(receipts.findByPostId(1L)).thenReturn(Optional.empty());
        assertNull(policy.lockReceipt(p2p));
        verify(receipts).findByPostId(1L);

        // UTC079 - existing receipt is locked via entityManager.refresh PESSIMISTIC_WRITE
        Post office = foundPost(2L, owner, "OFFICE");
        ItemReceipt stored = officeReceipt(2L, account(99L, AccountRole.APPROVER), category(1L));
        when(receipts.findByPostId(2L)).thenReturn(Optional.of(stored));

        ItemReceipt locked = policy.lockReceipt(office);
        assertSame(stored, locked);
        verify(entityManager).refresh(stored, LockModeType.PESSIMISTIC_WRITE);
    }

    // UTC080 - lockReceipt returns null without refresh when receipt is missing
    @Test
    void lockReceipt_whenOfficeReceiptMissing_returnsNullWithoutLockRefresh() {
        Account owner = account(10L, AccountRole.USER);
        Post office = foundPost(2L, owner, "OFFICE");
        when(receipts.findByPostId(2L)).thenReturn(Optional.empty());

        assertNull(policy.lockReceipt(office));
        verifyNoInteractions(entityManager);
    }

    // UTC070 - canSubmit returns false when P2P post already has a receipt
    @Test
    void canSubmit_whenP2pPostHasExistingReceipt_returnsFalse() {
        Account owner = account(10L, AccountRole.USER);
        Account officer = account(11L, AccountRole.APPROVER);
        Post p2p = foundPost(3L, owner, "P2P");
        when(receipts.findByPostId(3L)).thenReturn(Optional.of(officeReceipt(3L, officer, category(1L))));

        assertFalse(policy.canSubmit(p2p));
    }

    // UTC074 - canSubmit returns false when post moderationStatus is PENDING
    @Test
    void canSubmit_whenPostModerationPending_returnsFalse() {
        Account owner = account(10L, AccountRole.USER);
        Post pending = foundPost(4L, owner, "P2P");
        ReflectionTestUtils.setField(pending, "moderationStatus", "PENDING");

        assertFalse(policy.canSubmit(pending));
        verifyNoInteractions(receipts);
    }

    private static Account account(Long id, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@fpt.edu.vn");
        a.setFullName("User " + id);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static Category category(Long id) {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", id);
        c.setName("Wallet");
        return c;
    }

    private static Post foundPost(Long id, Account owner, String custodyMode) {
        Post p = Post.found(owner, category(1L), "Title", "Desc", "Hall A",
                OffsetDateTime.now().minusDays(1), custodyMode, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "moderationStatus", "VISIBLE");
        return p;
    }

    private static ItemReceipt officeReceipt(Long postId, Account officer, Category cat) {
        ItemReceipt r = ItemReceipt.walkInDraft("VP-1", officer);
        r.edit(cat, "Title", "Desc", "Good", "Hall A", OffsetDateTime.now().minusDays(1), null, null);
        r.assignLocation(StorageLocation.create("A-01", "Shelf A1", null));
        r.receiveAndPublish(officer, postId);
        return r;
    }
}

