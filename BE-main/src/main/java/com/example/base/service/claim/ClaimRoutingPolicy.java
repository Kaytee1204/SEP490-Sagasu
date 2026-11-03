package com.example.base.service.claim;

import com.example.base.entity.*;
import com.example.base.repository.ItemReceiptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

@Component @RequiredArgsConstructor
public class ClaimRoutingPolicy {
    private final ItemReceiptRepository receipts;
    private final EntityManager entityManager;

    public boolean canSubmit(Post post) { return available(post, Set.of("OPEN")); }

    public boolean matches(Claim claim, Set<String> statuses) {
        Post post = claim.getPost();
        if (!Objects.equals(claim.getClaimType(), post.getCustodyMode()) || !available(post, statuses)) return false;
        return !claim.isOffice() || (claim.getReceipt() != null
                && Objects.equals(claim.getReceipt().getPostId(), post.getId())
                && verified(claim.getReceipt()));
    }

    public boolean reviewer(Claim claim, Account actor) {
        if (Objects.equals(claim.getClaimant().getId(), actor.getId())) return false;
        return claim.isOffice() ? actor.getRole() == AccountRole.APPROVER
                : Objects.equals(claim.getPost().getAccount().getId(), actor.getId());
    }

    // Called after the post lock. Receipt state cannot change during a claim write.
    public ItemReceipt lockReceipt(Post post) {
        ItemReceipt receipt = receipts.findByPostId(post.getId()).orElse(null);
        if (receipt != null) entityManager.refresh(receipt, LockModeType.PESSIMISTIC_WRITE);
        return receipt;
    }

    private boolean available(Post post, Set<String> statuses) {
        if (post.getDeletedAt() != null || !"FOUND".equals(post.getPostType())
                || !"VISIBLE".equals(post.getModerationStatus()) || !statuses.contains(post.getStatus())) return false;
        ItemReceipt receipt = receipts.findByPostId(post.getId()).orElse(null);
        if ("P2P".equals(post.getCustodyMode())) return receipt == null;
        return "OFFICE".equals(post.getCustodyMode()) && receipt != null && verified(receipt);
    }

    private boolean verified(ItemReceipt receipt) {
        return "ACCEPTED".equals(receipt.getReviewStatus()) && "IN_STORAGE".equals(receipt.getStatus())
                && receipt.getReceivedAt() != null && receipt.getReceivedBy() != null && receipt.getStorageLocation() != null;
    }
}
