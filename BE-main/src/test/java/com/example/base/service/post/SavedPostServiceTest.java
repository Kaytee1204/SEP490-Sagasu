package com.example.base.service.post;

import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.mapper.PostMapper;
import com.example.base.repository.ItemReceiptRepository;
import com.example.base.repository.PostRepository;
import com.example.base.repository.SavedPostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.entity.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedPostServiceTest {
    @Mock SavedPostRepository saved;
    @Mock PostRepository posts;
    @Mock ItemReceiptRepository receipts;
    @Mock PostMapper mapper;
    @InjectMocks SavedPostService service;

    private CustomUserDetails viewer(long id) {
        var user = mock(CustomUserDetails.class);
        when(user.getId()).thenReturn(id);
        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isAccountNonLocked()).thenReturn(true);
        return user;
    }

    @Test void saveRejectsUnavailablePostsWithoutWriting() {
        var user = viewer(7);
        when(posts.findVisibleDetail(12)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> service.save(12, user));
        verifyNoInteractions(saved);
    }

    @Test void repeatedSaveUsesConflictSafeInsertForCurrentAccount() {
        var user = viewer(7);
        when(posts.findVisibleDetail(12)).thenReturn(Optional.of(mock(Post.class)));
        when(saved.saveIfAbsent(7, 12)).thenReturn(1, 0);
        service.save(12, user);
        service.save(12, user);
        verify(saved, times(2)).saveIfAbsent(7, 12);
    }

    @Test void removeScopesToViewerAndWorksEvenIfPostIsHidden() {
        service.remove(12, viewer(8));
        verify(saved).removeForAccount(8, 12);
        verifyNoInteractions(posts);
    }

    @Test void rejectsAnonymousAccessBeforeReadingSavedData() {
        assertThrows(AppException.class, () -> service.ids(null));
        assertThrows(AppException.class, () -> service.save(12, null));
        assertThrows(AppException.class, () -> service.remove(12, null));
        verifyNoInteractions(saved, posts);
    }

    @Test void idsOnlyReadsViewerVisibleBookmarks() {
        when(saved.findVisiblePostIds(7)).thenReturn(List.of(12L));
        assertEquals(List.of(12L), service.ids(viewer(7)));
        verify(saved).findVisiblePostIds(7);
    }

    @Test void listPagesSavedIdsBeforeLoadingImagesAndPreservesSavedOrder() {
        var user = viewer(7);
        var first = mock(Post.class);
        var second = mock(Post.class);
        when(first.getId()).thenReturn(20L);
        when(second.getId()).thenReturn(12L);
        when(saved.findVisiblePostIds(7, "FOUND", PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(List.of(20L, 12L), PageRequest.of(1, 2), 4));
        when(posts.findVisibleByIds(List.of(20L, 12L))).thenReturn(List.of(second, first));
        when(receipts.findPublicIntakes(List.of(20L, 12L))).thenReturn(List.of());
        var dto1 = new com.example.base.dto.post.PostSummaryDto(20L, "FOUND", "First", 1L, "Wallet", "A", null, "RETURNED", null, null, "Owner", null);
        var dto2 = new com.example.base.dto.post.PostSummaryDto(12L, "FOUND", "Second", 1L, "Wallet", "A", null, "OPEN", null, null, "Owner", null);
        when(mapper.toSummary(first, null)).thenReturn(dto1);
        when(mapper.toSummary(second, null)).thenReturn(dto2);
        var result = service.list(2, 2, "FOUND", user);
        assertEquals(List.of(dto1, dto2), result.content());
        assertEquals(4, result.totalElements());
        assertEquals("RETURNED", result.content().getFirst().status());
    }

    @Test void rejectsInvalidFiltersAndPaginationBeforeQuerying() {
        var user = viewer(7);
        assertThrows(AppException.class, () -> service.list(1, 12, "HIDDEN", user));
        assertThrows(AppException.class, () -> service.list(0, 12, "", user));
        assertThrows(AppException.class, () -> service.list(1, 101, "", user));
        verifyNoInteractions(saved);
    }
}
