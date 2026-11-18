package com.example.base.mapper;

import com.example.base.dto.account.AccountDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostSummaryDto;
import com.example.base.dto.receipt.ReceiptDto;
import com.example.base.entity.*;
import com.example.base.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapperTest {

    @Mock ImageStorage storage;

    // ---------- AccountMapper ----------

    private static Account account(String name, String email) {
        Account a = new Account();
        a.setId(7L);
        a.setFullName(name);
        a.setEmail(email);
        a.setRole(AccountRole.USER);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    @Test
    void utc011_accountMapper_null_returnsNull() {
        assertThat(new AccountMapper().toDto(null)).isNull();
    }

    @Test
    void utc012_accountMapper_stripsRoleSuffixFromName() {
        AccountDto dto = new AccountMapper().toDto(account("Nguyen Van A (Manager)", "a@gmail.com"));
        assertThat(dto.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(dto.getEmail()).isEqualTo("a@gmail.com");
        assertThat(dto.getRole()).isEqualTo(AccountRole.USER);
    }

    @Test
    void utc013_accountMapper_noName_usesEmailLocalPart() {
        assertThat(new AccountMapper().toDto(account(null, "anhduc@gmail.com")).getFullName()).isEqualTo("anhduc");
        assertThat(new AccountMapper().toDto(account(" ", "noatsign")).getFullName()).isEqualTo("noatsign");
        assertThat(new AccountMapper().toDto(account(null, null)).getFullName()).isNull();
    }

    @Test
    void utc014_accountMapper_mapsCreator() {
        Account a = account("A", "a@gmail.com");
        Account creator = account("Admin", "admin@gmail.com");
        creator.setId(1L);
        a.setCreatedBy(creator);

        AccountDto dto = new AccountMapper().toDto(a);

        assertThat(dto.getCreatedById()).isEqualTo(1L);
        assertThat(dto.getCreatedByEmail()).isEqualTo("admin@gmail.com");
    }

    // ---------- PostMapper ----------

    private Post post(boolean withImage, long ownerId, long creatorId) {
        Post p = mock(Post.class, Answers.RETURNS_DEEP_STUBS);
        when(p.getId()).thenReturn(10L);
        when(p.getTitle()).thenReturn("Wallet");
        when(p.getCategory().getCategoryId()).thenReturn(3L);
        when(p.getCategory().getName()).thenReturn("Ví");
        when(p.getAccount().getId()).thenReturn(ownerId);
        when(p.getCreatedBy().getId()).thenReturn(creatorId);
        when(p.getCreatedBy().getFullName()).thenReturn("Finder");
        if (withImage) {
            PostImage img = mock(PostImage.class);
            when(img.getStorageKey()).thenReturn("posts/1.jpg");
            when(img.getImageUrl()).thenReturn(null);
            when(img.getSortOrder()).thenReturn(0);
            when(p.getImages()).thenReturn(List.of(img));
        } else {
            when(p.getImages()).thenReturn(List.of());
        }
        return p;
    }

    @Test
    void utc015_postMapper_summary_thumbnailFromFirstImage() {
        when(storage.readUrl("posts/1.jpg", null)).thenReturn("https://cdn/1.jpg");
        PostSummaryDto dto = new PostMapper(storage).toSummary(post(true, 8L, 8L));
        assertThat(dto.thumbnailUrl()).isEqualTo("https://cdn/1.jpg");
        assertThat(dto.categoryName()).isEqualTo("Ví");
        assertThat(dto.authorName()).isEqualTo("Finder");
    }

    @Test
    void utc016_postMapper_summary_noImages_nullThumbnail() {
        assertThat(new PostMapper(storage).toSummary(post(false, 8L, 8L)).thumbnailUrl()).isNull();
        verifyNoInteractions(storage);
    }

    @Test
    void utc017_postMapper_detail_ownerCanEdit() {
        PostDetailDto dto = new PostMapper(storage).toDetail(post(true, 8L, 8L), 8L);
        assertThat(dto.isOwner()).isTrue();
        assertThat(dto.canEditOwnPost()).isTrue();
        assertThat(dto.images()).hasSize(1);
    }

    @Test
    void utc018_postMapper_detail_creatorOnBehalfIsOwner() {
        assertThat(new PostMapper(storage).toDetail(post(false, 8L, 2L), 2L).isOwner()).isTrue();
    }

    @Test
    void utc019_postMapper_detail_anonymousOrOther_notOwner() {
        PostMapper mapper = new PostMapper(storage);
        assertThat(mapper.toDetail(post(false, 8L, 8L)).isOwner()).isFalse();
        assertThat(mapper.toDetail(post(false, 8L, 8L), 99L).canEditOwnPost()).isFalse();
    }

    // ---------- ReceiptMapper ----------

    @Test
    void utc020_receiptMapper_mapsLocationAndImages() {
        ItemReceipt r = mock(ItemReceipt.class, Answers.RETURNS_DEEP_STUBS);
        when(r.getId()).thenReturn(1L);
        when(r.getCategory().getName()).thenReturn("Ví");
        when(r.getRecordedBy().getFullName()).thenReturn("Approver");
        when(r.getReviewedBy()).thenReturn(null);
        when(r.getReceivedBy()).thenReturn(null);
        when(r.getStorageLocation().getId()).thenReturn(4L);
        when(r.getStorageLocation().getName()).thenReturn("Shelf A");
        when(r.getStorageLocation().isActive()).thenReturn(true);
        ItemReceiptImage img = mock(ItemReceiptImage.class);
        when(img.getStorageKey()).thenReturn("k");
        when(img.isSensitive()).thenReturn(true);
        when(r.getImages()).thenReturn(List.of(img));
        when(storage.readUrl(eq("k"), any())).thenReturn("https://cdn/k");

        ReceiptDto dto = new ReceiptMapper(storage).detail(r);

        assertThat(dto.storageLocationName()).isEqualTo("Shelf A");
        assertThat(dto.storageLocationActive()).isTrue();
        assertThat(dto.reviewedBy()).isNull();
        assertThat(dto.images()).singleElement().satisfies(i -> {
            assertThat(i.url()).isEqualTo("https://cdn/k");
            assertThat(i.sensitive()).isTrue();
        });
    }

    @Test
    void utc021_receiptMapper_noLocation_nullFields() {
        ItemReceipt r = mock(ItemReceipt.class, Answers.RETURNS_DEEP_STUBS);
        when(r.getStorageLocation()).thenReturn(null);
        when(r.getImages()).thenReturn(List.of());

        ReceiptDto dto = new ReceiptMapper(storage).detail(r);

        assertThat(dto.storageLocationId()).isNull();
        assertThat(dto.storageLocationName()).isNull();
        assertThat(dto.storageLocationActive()).isFalse();
    }
}
