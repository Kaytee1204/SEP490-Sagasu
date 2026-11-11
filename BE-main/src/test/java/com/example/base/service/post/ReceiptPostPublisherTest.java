package com.example.base.service.post;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ItemReceipt;
import com.example.base.entity.ItemReceiptImage;
import com.example.base.entity.Post;
import com.example.base.entity.StorageLocation;
import com.example.base.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptPostPublisherTest {

    @Mock
    private PostRepository posts;

    @InjectMocks
    private ReceiptPostPublisher publisher;

    // UTC080 - publishFromReceipt: valid WALK_IN draft with 2 public & 1 sensitive image publishes 2 ordered public images
    @Test
    void publishFromReceipt_whenValidWithTwoPublicAndOneSensitiveImage_publishesTwoOrderedImages() {
        Account officer = officer(AccountRole.APPROVER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        receipt.addImage(receiptImage("receipts/1.jpg", 0, false));
        receipt.addImage(receiptImage("receipts/2-secret.jpg", 1, true));
        receipt.addImage(receiptImage("receipts/3.jpg", 2, false));

        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 101L);
            return p;
        });

        Post published = publisher.publishFromReceipt(receipt, officer);

        assertEquals("FOUND", published.getPostType());
        assertEquals("OFFICE", published.getCustodyMode());
        assertEquals("VISIBLE", published.getModerationStatus());
        assertEquals(2, published.getImages().size());
        assertEquals(0, published.getImages().get(0).getSortOrder());
        assertEquals("receipts/1.jpg", published.getImages().get(0).getStorageKey());
        assertEquals(1, published.getImages().get(1).getSortOrder());
        assertEquals("receipts/3.jpg", published.getImages().get(1).getStorageKey());
        verify(posts).saveAndFlush(any(Post.class));
    }

    // UTC081 - publishFromReceipt: officer with role USER throws IllegalStateException
    @Test
    void publishFromReceipt_whenOfficerRoleIsUser_throwsIllegalStateException() {
        Account officer = officer(AccountRole.USER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        receipt.addImage(receiptImage("receipts/1.jpg", 0, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> publisher.publishFromReceipt(receipt, officer));
        assertEquals("Only an unposted direct intake can be published", ex.getMessage());
    }

    // UTC082 - publishFromReceipt: receipt with postId already set throws IllegalStateException
    @Test
    void publishFromReceipt_whenPostIdAlreadySet_throwsIllegalStateException() {
        Account officer = officer(AccountRole.APPROVER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        ReflectionTestUtils.setField(receipt, "postId", 99L);
        receipt.addImage(receiptImage("receipts/1.jpg", 0, false));

        assertThrows(IllegalStateException.class, () -> publisher.publishFromReceipt(receipt, officer));
    }

    // UTC083 - publishFromReceipt: receipt reviewStatus=REJECTED throws IllegalStateException
    @Test
    void publishFromReceipt_whenReviewStatusRejected_throwsIllegalStateException() {
        Account officer = officer(AccountRole.APPROVER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        ReflectionTestUtils.setField(receipt, "reviewStatus", "REJECTED");
        receipt.addImage(receiptImage("receipts/1.jpg", 0, false));

        assertThrows(IllegalStateException.class, () -> publisher.publishFromReceipt(receipt, officer));
    }

    // UTC084 - publishFromReceipt: receipt storageLocation=null throws IllegalStateException
    @Test
    void publishFromReceipt_whenStorageLocationNull_throwsIllegalStateException() {
        Account officer = officer(AccountRole.APPROVER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        receipt.assignLocation(null);
        receipt.addImage(receiptImage("receipts/1.jpg", 0, false));

        assertThrows(IllegalStateException.class, () -> publisher.publishFromReceipt(receipt, officer));
    }

    // UTC085 - publishFromReceipt: all sensitive receipt images throws IllegalStateException
    @Test
    void publishFromReceipt_whenAllImagesSensitive_throwsIllegalStateException() {
        Account officer = officer(AccountRole.APPROVER, AccountStatus.ACTIVE);
        ItemReceipt receipt = validReceipt(officer);
        receipt.addImage(receiptImage("receipts/1-secret.jpg", 0, true));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> publisher.publishFromReceipt(receipt, officer));
        assertEquals("A public image is required", ex.getMessage());
    }

    private static Account officer(AccountRole role, AccountStatus status) {
        Account a = new Account();
        a.setId(2L);
        a.setEmail("approver@fpt.edu.vn");
        a.setFullName("Approver");
        a.setRole(role);
        a.setStatus(status);
        return a;
    }

    private static ItemReceipt validReceipt(Account officer) {
        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 3L);
        cat.setName("Wallet");
        ItemReceipt receipt = ItemReceipt.walkInDraft("REC-001", officer);
        receipt.edit(cat, "Found wallet", "Black leather", "Good", "Hall A",
                OffsetDateTime.now().minusHours(2), null, null);
        receipt.assignLocation(StorageLocation.create("A-01", "Shelf A1", null));
        return receipt;
    }

    private static ItemReceiptImage receiptImage(String key, int order, boolean sensitive) {
        ItemReceiptImage img = new ItemReceiptImage(key, order, "image/jpeg", 1024L);
        ReflectionTestUtils.setField(img, "sensitive", sensitive);
        return img;
    }
}

