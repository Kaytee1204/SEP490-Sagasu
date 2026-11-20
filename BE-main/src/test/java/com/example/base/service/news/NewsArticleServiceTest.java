package com.example.base.service.news;

import com.example.base.dto.PageResponse;
import com.example.base.dto.news.NewsArticleDto;
import com.example.base.dto.news.NewsArticleRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.NewsArticle;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.NewsArticleRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsArticleServiceTest {

    @Mock
    private NewsArticleRepository repository;
    @Mock
    private AccountRepository accounts;
    @Mock
    private ImageStorage imageStorage;
    @Mock
    private ImageValidator imageValidator;

    @InjectMocks
    private NewsArticleService service;

    private Account account(Long id, AccountRole role, String name) {
        Account a = new Account();
        a.setId(id);
        a.setRole(role);
        a.setFullName(name);
        a.setEmail(name.toLowerCase().replace(" ", "") + "@fpt.edu.vn");
        return a;
    }

    @Test
    void approverCanCreateNewsWithCoverImage() {
        Account approver = account(8L, AccountRole.APPROVER, "Tuệ Lâm");
        when(accounts.findById(8L)).thenReturn(Optional.of(approver));
        MockMultipartFile file = new MockMultipartFile("coverImage", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(imageValidator.validate(any())).thenReturn(List.of(
                new ImageValidator.ValidatedImage(new byte[]{1, 2, 3}, "image/jpeg", "jpg", 800, 450, "hash")
        ));
        when(imageStorage.readUrl(anyString(), any())).thenReturn("https://cdn.example.com/news/cover.jpg");
        when(repository.save(any(NewsArticle.class))).thenAnswer(inv -> {
            NewsArticle n = inv.getArgument(0);
            n.setId(101L);
            n.setCreatedAt(OffsetDateTime.now());
            n.setUpdatedAt(OffsetDateTime.now());
            return n;
        });

        NewsArticleDto created = service.create(
                new NewsArticleRequest("Cập nhật mới", "Tóm tắt ngắn", "Nội dung chi tiết", "UPDATE", true, false),
                file,
                new CustomUserDetails(approver)
        );

        assertEquals(101L, created.id());
        assertEquals("Cập nhật mới", created.title());
        assertEquals("Cập nhật hệ thống", created.categoryLabel());
        assertEquals("https://cdn.example.com/news/cover.jpg", created.coverImageUrl());
        assertEquals("Tuệ Lâm", created.authorName());
        assertTrue(created.published());
        verify(imageStorage).put(startsWith("news/"), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void regularUserCannotCreateNews() {
        Account regular = account(7L, AccountRole.USER, "Sinh viên");
        when(accounts.findById(7L)).thenReturn(Optional.of(regular));

        AppException ex = assertThrows(AppException.class, () -> service.create(
                new NewsArticleRequest("Tin mới", "Tóm tắt", "Nội dung", "UPDATE", true, false),
                null,
                new CustomUserDetails(regular)
        ));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void createRejectsBlankTitleOrInvalidCategory() {
        Account approver = account(8L, AccountRole.APPROVER, "Tuệ Lâm");
        when(accounts.findById(8L)).thenReturn(Optional.of(approver));

        AppException blankTitle = assertThrows(AppException.class, () -> service.create(
                new NewsArticleRequest("   ", "Tóm tắt", "Nội dung", "UPDATE", true, false),
                null,
                new CustomUserDetails(approver)
        ));
        assertEquals(ErrorCode.BAD_REQUEST, blankTitle.getErrorCode());

        AppException badCat = assertThrows(AppException.class, () -> service.create(
                new NewsArticleRequest("Tiêu đề", "Tóm tắt", "Nội dung", "INVALID", true, false),
                null,
                new CustomUserDetails(approver)
        ));
        assertEquals(ErrorCode.BAD_REQUEST, badCat.getErrorCode());
    }

    @Test
    void listPublishedReturnsPagedNews() {
        NewsArticle n = new NewsArticle();
        n.setId(1L);
        n.setTitle("Thông báo mới");
        n.setSummary("Tóm tắt");
        n.setContent("Nội dung");
        n.setCategory("ANNOUNCEMENT");
        n.setPublished(true);
        n.setCreatedAt(OffsetDateTime.now());
        n.setUpdatedAt(OffsetDateTime.now());

        when(repository.findPublished(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        PageResponse<NewsArticleDto> page = service.listPublished(1, 8);
        assertEquals(1, page.getTotalElements());
        assertEquals("Thông báo", page.getContent().get(0).categoryLabel());
    }

    @Test
    void approverCanDeleteNewsAndCleansUpCoverImage() {
        Account approver = account(8L, AccountRole.APPROVER, "Tuệ Lâm");
        when(accounts.findById(8L)).thenReturn(Optional.of(approver));
        NewsArticle existing = new NewsArticle();
        existing.setId(5L);
        existing.setCoverImageKey("news/old-cover.png");
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        service.delete(5L, new CustomUserDetails(approver));

        verify(repository).delete(existing);
        verify(imageStorage).delete("news/old-cover.png");
    }
}
