package com.example.base.service.post;

import com.example.base.entity.*;
import com.example.base.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs inside the receipt transaction; never exposes a public post creation endpoint. */
@Service
@RequiredArgsConstructor
public class ReceiptPostPublisher {
    private final PostRepository posts;

    @Transactional(propagation = Propagation.MANDATORY)
    public Post publishFromReceipt(ItemReceipt receipt, Account officer) {
        if (officer.getRole() != AccountRole.APPROVER || officer.getStatus() != AccountStatus.ACTIVE
                || !java.util.Set.of("WALK_IN", "EXISTING_POST").contains(receipt.getSource())
                || !java.util.Set.of("DRAFT", "SUBMITTED").contains(receipt.getStatus())
                || "REJECTED".equals(receipt.getReviewStatus())
                || receipt.getPostId() != null || receipt.getReceivedAt() != null || receipt.getStorageLocation() == null) {
            throw new IllegalStateException("Only an unposted direct intake can be published");
        }
        Post post = Post.receivedByOffice(officer, receipt.getCategory(), receipt.getTitle(),
                receipt.getDescription(), receipt.getFoundLocation(), receipt.getFoundAt());
        int order = 0;
        for (ItemReceiptImage image : receipt.getImages()) {
            if (!image.isSensitive()) post.addImage(PostImage.fromReceipt(image, order++));
        }
        if (order == 0) throw new IllegalStateException("A public image is required");
        return posts.saveAndFlush(post);
    }
}
