package com.example.base.service.post;

import com.example.base.dto.post.PrivateIdentifiersDto;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostPrivateIdentifiersServiceTest {

    @Mock
    private PostRepository posts;

    @InjectMocks
    private PostPrivateIdentifiersService service;

    // UTC074 - read: post owner receives stored privateIdentifiers value
    @Test
    void read_whenOwnerWithValue_returnsValue() {
        Account owner = account(5L, AccountStatus.ACTIVE);
        Post post = samplePost(10L, owner);
        post.updatePrivateIdentifiers("IMEI 123");
        when(posts.findByIdAndAccount_IdAndDeletedAtIsNull(10L, 5L)).thenReturn(Optional.of(post));

        PrivateIdentifiersDto dto = service.read(10L, new CustomUserDetails(owner));

        assertEquals("IMEI 123", dto.privateIdentifiers());
    }

    // UTC075 - read: null privateIdentifiers returns empty string
    @Test
    void read_whenOwnerWithNullIdentifiers_returnsEmptyString() {
        Account owner = account(5L, AccountStatus.ACTIVE);
        Post post = samplePost(10L, owner);
        when(posts.findByIdAndAccount_IdAndDeletedAtIsNull(10L, 5L)).thenReturn(Optional.of(post));

        PrivateIdentifiersDto dto = service.read(10L, new CustomUserDetails(owner));

        assertEquals("", dto.privateIdentifiers());
    }

    // UTC076 - read: non-owner throws POST_OWNER_REQUIRED
    @Test
    void read_whenNotOwner_throwsPostOwnerRequired() {
        Account other = account(6L, AccountStatus.ACTIVE);
        when(posts.findByIdAndAccount_IdAndDeletedAtIsNull(10L, 6L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.read(10L, new CustomUserDetails(other)));

        assertEquals(ErrorCode.POST_OWNER_REQUIRED, ex.getErrorCode());
    }

    // UTC079 - read: SUSPENDED account throws FORBIDDEN
    @Test
    void read_whenUserSuspended_throwsForbidden() {
        Account suspended = account(5L, AccountStatus.SUSPENDED);

        AppException ex = assertThrows(AppException.class,
                () -> service.read(10L, new CustomUserDetails(suspended)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // UTC077 - update: post owner updates trimmed privateIdentifiers and flushes
    @Test
    void update_whenOwner_updatesIdentifiersAndFlushes() {
        Account owner = account(5L, AccountStatus.ACTIVE);
        Post post = samplePost(10L, owner);
        when(posts.findOwnedForUpdate(10L, 5L)).thenReturn(Optional.of(post));

        service.update(10L, new PrivateIdentifiersDto("  New secret  "), new CustomUserDetails(owner));

        assertEquals("New secret", post.getPrivateIdentifiers());
        verify(posts).flush();
    }

    // UTC078 - update: non-owner throws POST_OWNER_REQUIRED
    @Test
    void update_whenNotOwner_throwsPostOwnerRequired() {
        Account other = account(6L, AccountStatus.ACTIVE);
        when(posts.findOwnedForUpdate(10L, 6L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.update(10L, new PrivateIdentifiersDto("x"), new CustomUserDetails(other)));

        assertEquals(ErrorCode.POST_OWNER_REQUIRED, ex.getErrorCode());
    }

    // Supplementary (PostPrivateIdentifiersService.update): non-positive postId throws POST_ID_INVALID
    @Test
    void update_whenInvalidPostId_throwsPostIdInvalid() {
        Account owner = account(5L, AccountStatus.ACTIVE);

        AppException ex = assertThrows(AppException.class,
                () -> service.update(0L, new PrivateIdentifiersDto("x"), new CustomUserDetails(owner)));

        assertEquals(ErrorCode.POST_ID_INVALID, ex.getErrorCode());
    }

    private static Account account(Long id, AccountStatus status) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("u" + id + "@fpt.edu.vn");
        a.setFullName("User " + id);
        a.setRole(AccountRole.USER);
        a.setStatus(status);
        return a;
    }

    private static Post samplePost(Long id, Account owner) {
        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 3L);
        cat.setName("Wallet");
        Post post = Post.lost(owner, cat, "Lost wallet", "Black", "Hall A", OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}

