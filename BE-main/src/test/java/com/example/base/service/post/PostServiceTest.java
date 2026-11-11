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

    // UTC061 - getActiveCategories returns ordered active CategoryDto list
    @Test
    void getActiveCategories_whenPresent_returnsOrderedDtos() {
        Category c1 = category(1L, "Books");
        Category c2 = category(2L, "Wallet");
        when(categories.findByActiveTrueOrderByNameAscCategoryIdAsc()).thenReturn(List.of(c1, c2));

        List<CategoryDto> result = service.getActiveCategories();

        assertEquals(List.of(new CategoryDto(1L, "Books"), new CategoryDto(2L, "Wallet")), result);
    }

    // UTC062 - getActiveCategories returns empty list when no active categories exist
    @Test
    void getActiveCategories_whenEmpty_returnsEmptyList() {
        when(categories.findByActiveTrueOrderByNameAscCategoryIdAsc()).thenReturn(List.of());
        assertTrue(service.getActiveCategories().isEmpty());
    }

    // UTC063 - listVisible with empty filter queries findVisibleIds and attaches office receipts
    @Test
    void listVisible_whenEmptyFilter_usesFindVisibleIdsAndAttachesOfficeReceipts() {
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

    // UTC064 - listVisible with filter uses searchVisibleIds
    @Test
    void listVisible_whenFilterProvided_usesSearchVisibleIds() {
        PostSearchFilter filter = new PostSearchFilter("wallet", null, "LOST", "", null, null);
        when(posts.searchVisibleIds("wallet", null, "LOST", "", null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PostPageDto result = service.listVisible(1, 20, filter);

        assertTrue(result.content().isEmpty());
        verify(posts, never()).findVisibleByIds(anyList());
        verify(receipts, never()).findPublicIntakes(anyList());
    }

    // UTC065, UTC066 - listVisible throws POST_PAGINATION_INVALID on invalid page or size
    @Test
    void listVisible_whenInvalidPagination_throwsPostPaginationInvalid() {
        // UTC065 - page < 1 throws POST_PAGINATION_INVALID
        assertEquals(ErrorCode.POST_PAGINATION_INVALID,
                assertThrows(AppException.class, () -> service.listVisible(0, 20, PostSearchFilter.empty())).getErrorCode());
        // UTC066 - size < 1 throws POST_PAGINATION_INVALID
        assertEquals(ErrorCode.POST_PAGINATION_INVALID,
                assertThrows(AppException.class, () -> service.listVisible(1, 0, PostSearchFilter.empty())).getErrorCode());
    }

    // UTC067 - listVisible returns empty page and skips batch fetch and receipt lookup when no visible IDs
    @Test
    void listVisible_whenNoVisibleIds_skipsBatchFetchAndReceipts() {
        var pageable = PageRequest.of(0, 20);
        when(posts.findVisibleIds(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PostPageDto result = service.listVisible(1, 20);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        verify(posts, never()).findVisibleByIds(anyList());
        verify(receipts, never()).findPublicIntakes(anyList());
    }

    // UTC068 - getDetailForViewer sets canRequestHandover=false when visible post has null viewer
    @Test
    void getDetailForViewer_whenVisibleAndNullViewer_setsCanRequestHandoverFalse() {
        Account owner = account(5L);
        Post post = foundPost(10L, owner, category(3L, "Wallet"));
        PostDetailDto expected = mock(PostDetailDto.class);
        when(posts.findVisibleDetail(10L)).thenReturn(Optional.of(post));
        when(receipts.findPublicIntakes(List.of(10L))).thenReturn(List.of());
        when(mapper.toDetail(post, null, null, false)).thenReturn(expected);

        PostDetailDto result = service.getDetailForViewer(10L, null);

        assertEquals(expected, result);
        verify(posts, never()).findOwnDetail(eq(10L), anyLong());
    }

    // UTC069 - getDetailForViewer falls back to findOwnDetail for PENDING post owned by viewer
    @Test
    void getDetailForViewer_whenPendingOwnedByViewer_fallsBackToFindOwnDetail() {
        Account owner = account(5L);
        Post post = foundPost(10L, owner, category(3L, "Wallet"));
        ReflectionTestUtils.setField(post, "moderationStatus", "PENDING");
        PostDetailDto expected = mock(PostDetailDto.class);
        when(posts.findVisibleDetail(10L)).thenReturn(Optional.empty());
        when(posts.findOwnDetail(10L, 5L)).thenReturn(Optional.of(post));
        when(receipts.findPublicIntakes(List.of(10L))).thenReturn(List.of());
        when(mapper.toDetail(post, 5L, null, false)).thenReturn(expected);

        assertEquals(expected, service.getDetailForViewer(10L, 5L));
    }

    // UTC070 - getDetailForViewer throws POST_NOT_VISIBLE when PENDING post is viewed by non-owner
    @Test
    void getDetailForViewer_whenPendingViewedByOther_throwsPostNotVisible() {
        when(posts.findVisibleDetail(10L)).thenReturn(Optional.empty());
        when(posts.findOwnDetail(10L, 6L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getDetailForViewer(10L, 6L));
        assertEquals(ErrorCode.POST_NOT_VISIBLE, ex.getErrorCode());
    }

    // UTC071, UTC072 - getDetailForViewer checks receipt existence when owner and handover-eligible
    @Test
    void getDetailForViewer_whenOwnerAndHandoverEligible_checksReceiptExistence() {
        Account owner = account(5L);
        Post post = foundPost(10L, owner, category(3L, "Wallet"));
        PostDetailDto dtoTrue = mock(PostDetailDto.class);
        PostDetailDto dtoFalse = mock(PostDetailDto.class);
        when(posts.findVisibleDetail(10L)).thenReturn(Optional.of(post));
        when(receipts.findPublicIntakes(List.of(10L))).thenReturn(List.of());
        when(receipts.existsByPostId(10L)).thenReturn(false, true);
        when(mapper.toDetail(post, 5L, null, true)).thenReturn(dtoTrue);
        when(mapper.toDetail(post, 5L, null, false)).thenReturn(dtoFalse);

        // UTC071 - no existing receipt returns canRequestHandover=true
        assertEquals(dtoTrue, service.getDetailForViewer(10L, 5L));
        // UTC072 - existing receipt returns canRequestHandover=false
        assertEquals(dtoFalse, service.getDetailForViewer(10L, 5L));
    }

    // UTC073 - getDetailForViewer throws POST_ID_INVALID when postId <= 0
    @Test
    void getDetailForViewer_whenInvalidId_throwsPostIdInvalid() {
        AppException ex = assertThrows(AppException.class, () -> service.getDetailForViewer(-1L, 5L));
        assertEquals(ErrorCode.POST_ID_INVALID, ex.getErrorCode());
        assertFalse(false);
    }

    private static Account account(Long id) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@fpt.edu.vn");
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

