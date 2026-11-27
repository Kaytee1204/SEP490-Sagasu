package com.example.base.service.post;

import com.example.base.dto.post.CategoryDto;
import com.example.base.dto.post.OfficeReceiptDto;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.dto.post.PostPageDto;
import com.example.base.dto.post.PostSearchFilter;
import com.example.base.dto.post.PostSummaryDto;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ItemReceipt;
import com.example.base.entity.Post;
import com.example.base.entity.StorageLocation;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.mapper.PostMapper;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository posts;
    @Mock
    private PostMapper mapper;
    @Mock
    private CategoryRepository categories;
    @Mock
    private ItemReceiptRepository receipts;
    @Mock
    private ClaimRepository claims;

    @InjectMocks
    private PostService service;

    // UTC123 - listVisible with empty filter queries findVisibleIds and attaches office receipts
    @Test
    void utc123_listVisible_whenEmptyFilter_usesFindVisibleIdsAndAttachesOfficeReceipts() {
        Account owner = account(5L);
        Category cat = category(3L, "Wallet");
        Post p1 = foundPost(1L, owner, cat);
        Post p2 = foundPost(2L, owner, cat);
        Post p3 = foundPost(3L, owner, cat);
        ItemReceipt receipt = officeReceipt(2L, owner, cat);

        when(posts.findVisibleIds(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(1L, 2L, 3L), PageRequest.of(0, 20), 3));
        when(posts.findVisibleByIds(List.of(1L, 2L, 3L))).thenReturn(List.of(p3, p1, p2));
        when(receipts.findPublicIntakes(List.of(1L, 2L, 3L))).thenReturn(List.of(receipt));
        PostSummaryDto s1 = mock(PostSummaryDto.class);
        PostSummaryDto s2 = mock(PostSummaryDto.class);
        PostSummaryDto s3 = mock(PostSummaryDto.class);
        when(mapper.toSummary(p1, null)).thenReturn(s1);
        when(mapper.toSummary(eq(p2), any(OfficeReceiptDto.class))).thenReturn(s2);
        when(mapper.toSummary(p3, null)).thenReturn(s3);

        PostPageDto result = service.listVisible(1, 20);

        assertEquals(List.of(s1, s2, s3), result.content());
        verify(posts, never()).searchVisibleIds(any(), any(), any(), any(), any(), any(), any());
    }

    // UTC124 - listVisible with filter uses searchVisibleIds
    @Test
    void utc124_listVisible_whenFilterProvided_usesSearchVisibleIds() {
        PostSearchFilter filter = new PostSearchFilter("wallet", null, "LOST", "", null, null);
        when(posts.searchVisibleIds("wallet", null, "LOST", "", null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PostPageDto result = service.listVisible(1, 20, filter);

        assertTrue(result.content().isEmpty());
        verify(posts, never()).findVisibleByIds(anyList());
        verify(receipts, never()).findPublicIntakes(anyList());
    }

    @Test
    void utc125_listVisible_pageZeroThrowsPaginationInvalid() {
        assertEquals(ErrorCode.POST_PAGINATION_INVALID,
                assertThrows(AppException.class, () -> service.listVisible(0, 20, PostSearchFilter.empty())).getErrorCode());
    }

    @Test
    void utc126_listVisible_sizeZeroThrowsPaginationInvalid() {
        assertEquals(ErrorCode.POST_PAGINATION_INVALID,
                assertThrows(AppException.class, () -> service.listVisible(1, 0, PostSearchFilter.empty())).getErrorCode());
    }

    // UTC127 - listVisible returns empty page and skips batch fetch and receipt lookup when no visible IDs
    @Test
    void utc127_listVisible_whenNoVisibleIds_skipsBatchFetchAndReceipts() {
        var pageable = PageRequest.of(0, 20);
        when(posts.findVisibleIds(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PostPageDto result = service.listVisible(1, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        verify(posts, never()).findVisibleByIds(anyList());
        verify(receipts, never()).findPublicIntakes(anyList());
    }

    private static Account account(Long id) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@gmail.com");
        a.setFullName("User " + id);
        a.setRole(AccountRole.USER);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static Category category(Long id, String name) {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", id);
        c.setName(name);
        return c;
    }

    private static Post foundPost(Long id, Account owner, Category cat) {
        Post p = Post.found(owner, cat, "Title " + id, "Desc", "Hall A",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(p, "id", id);
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

