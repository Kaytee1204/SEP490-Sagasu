package com.example.base.service.post;

import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.PostRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.AccountRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostCreationServiceTest {
    PostRepository posts = mock(PostRepository.class);
    CategoryRepository categories = mock(CategoryRepository.class);
    AccountRepository accounts = mock(AccountRepository.class);
    ImageStorage storage = mock(ImageStorage.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    PostCreationService service;
    CustomUserDetails user;
    CreateLostPostRequest request = new CreateLostPostRequest("Title", "Description", 1L, "Hall A",
            OffsetDateTime.now().minusDays(1), null);

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Account account = new Account();
        account.setId(7L);
        account.setEmail("user@test.invalid");
        account.setRole(AccountRole.USER);
        account.setStatus(AccountStatus.ACTIVE);
        when(categories.existsByCategoryIdAndActiveTrue(1L)).thenReturn(true);
        when(categories.findActiveForPostCreation(1L)).thenReturn(Optional.of(mock(Category.class)));
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(account));
        when(posts.saveAndFlush(any())).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(post, "id", 11L);
            return post;
        });
        user = new CustomUserDetails(account);
        service = new PostCreationService(posts, categories, accounts, storage, new ImageValidator(), manager);
    }

    MockMultipartFile png() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("images", "untrusted-name.png", "image/png", bytes.toByteArray());
    }

    @Test
    void createsFoundWithBothCustodyChoicesAndPrivateIdentifiers() {
        for (String custody : List.of("P2P", "OFFICE")) {
            var found = new com.example.base.dto.post.CreateFoundPostRequest("Title", "Description", 1L,
                    "Hall A", request.eventAt(), custody, "  SECRET-SERIAL  ");
            var result = service.createFound(found, null, user);
            assertThat(result.postType()).isEqualTo("FOUND");
            assertThat(result.moderationStatus()).isEqualTo("VISIBLE");
            verify(posts).saveAndFlush(argThat(post -> post.getPostType().equals("FOUND")
                    && post.getCustodyMode().equals(custody)
                    && post.getPrivateIdentifiers().equals("SECRET-SERIAL")));
        }
    }

    @Test
    void invalidCustodyNeverUploadsOrWrites() {
        var found = new com.example.base.dto.post.CreateFoundPostRequest("Title", "Description", 1L,
                "Hall A", request.eventAt(), "INVALID", null);
        assertThatThrownBy(() -> service.createFound(found, null, user)).isInstanceOf(AppException.class);
        verifyNoInteractions(storage, posts, manager);
    }

    @Test
    void failedFoundSaveCompensatesUploadedImages() throws Exception {
        var found = new com.example.base.dto.post.CreateFoundPostRequest("Title", "Description", 1L,
                "Hall A", request.eventAt(), "OFFICE", null);
        doThrow(new IllegalStateException("db failed")).when(posts).saveAndFlush(any());
        assertThatThrownBy(() -> service.createFound(found, List.of(png()), user))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(startsWith("posts/7/"));
        verify(manager).rollback(any());
    }

    @Test
    void createsWithoutImagesAndUsesAuthenticatedOwner() {
        var result = service.createLost(request, null, user);
        assertThat(result.moderationStatus()).isEqualTo("VISIBLE");
        verify(posts)
                .saveAndFlush(argThat(post -> post.getAccount().getId() == 7L && post.getCreatedBy().getId() == 7L));
        verifyNoInteractions(storage);
        verify(manager).commit(any());
    }

    @Test
    void storesGeneratedKeyAndVerifiedImageMetadata() throws Exception {
        var result = service.createLost(request, List.of(png()), user);
        assertThat(result.id()).isEqualTo(11L);
        verify(storage).put(startsWith("posts/7/"), any(), eq("image/png"));
        verify(posts).saveAndFlush(argThat(post -> post.getImages().size() == 1
                && post.getImages().getFirst().getWidth() == 2 && post.getImages().getFirst().getHeight() == 3
                && post.getImages().getFirst().getContentHash().length() == 64));
    }

    @Test
    void cleansUpUploadsWhenDatabaseWriteFails() throws Exception {
        doThrow(new IllegalStateException("db failed")).when(posts).saveAndFlush(any());
        assertThatThrownBy(() -> service.createLost(request, List.of(png()), user))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(startsWith("posts/7/"));
        verify(manager).rollback(any());
    }

    @Test
    void uploadFailureNeverCreatesPostAndCleansUpUncertainWrite() throws Exception {
        doThrow(new AppException(ErrorCode.STORAGE_UNAVAILABLE)).when(storage).put(anyString(), any(), anyString());
        assertThatThrownBy(() -> service.createLost(request, List.of(png()), user)).isInstanceOf(AppException.class);
        verify(posts, never()).saveAndFlush(any());
        verify(storage).delete(anyString());
    }

    @Test
    void invalidOrInactiveCategoryNeverUploads() throws Exception {
        when(categories.existsByCategoryIdAndActiveTrue(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.createLost(request, List.of(png()), user)).isInstanceOf(AppException.class);
        verifyNoInteractions(storage, manager);
    }

    @Test
    void rejectsSpoofedAndOversizedImagesBeforeUpload() {
        var fake = new MockMultipartFile("images", "fake.png", "image/png", "not an image".getBytes());
        var large = new MockMultipartFile("images", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        for (var file : List.of(fake, large)) {
            assertThatThrownBy(() -> service.createLost(request, List.of(file), user)).isInstanceOf(AppException.class);
        }
        verifyNoInteractions(storage, manager);
    }

    @Test
    void rejectsMoreThanFiveImages() throws Exception {
        var file = png();
        assertThatThrownBy(() -> service.createLost(request, java.util.Collections.nCopies(6, file), user))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(storage, manager);
    }

    @Test
    void categoryDeactivatedDuringUploadRollsBackAndCleansUp() throws Exception {
        when(categories.findActiveForPostCreation(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createLost(request, List.of(png()), user)).isInstanceOf(AppException.class);
        verify(posts, never()).saveAndFlush(any());
        verify(storage).delete(startsWith("posts/7/"));
        verify(manager).rollback(any());
    }

    @Test
    void suspendedAccountDuringUploadRollsBackAndCleansUp() throws Exception {
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createLost(request, List.of(png()), user)).isInstanceOf(AppException.class);
        verify(posts, never()).saveAndFlush(any());
        verify(storage).delete(startsWith("posts/7/"));
        verify(manager).rollback(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AccountRole.class, names = {"USER", "CHECKER", "EXTERNAL_USER"})
    void lostAndFoundUseDatabaseAccountRoleForModeration(AccountRole role) {
        Account author = new Account(); author.setId(7L); author.setRole(role); author.setStatus(AccountStatus.ACTIVE);
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(author));
        String expected = role == AccountRole.USER ? "VISIBLE" : "PENDING";
        var principal = new CustomUserDetails(author);
        assertThat(service.createLost(request, null, principal).moderationStatus()).isEqualTo(expected);
        var found = new com.example.base.dto.post.CreateFoundPostRequest("Title", "Description", 1L,
                "Hall A", request.eventAt(), "P2P", null);
        assertThat(service.createFound(found, null, principal).moderationStatus()).isEqualTo(expected);
    }

    @Test
    void roleChangedDuringUploadUsesLatestDatabaseRole() {
        Account external = new Account(); external.setId(7L); external.setRole(AccountRole.EXTERNAL_USER);
        external.setStatus(AccountStatus.ACTIVE);
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(external));
        assertThat(service.createLost(request, null, user).moderationStatus()).isEqualTo("PENDING");
    }

    @Test
    void expiredUserCannotCreatePost() {
        Account account = new Account();
        account.setId(7L);
        account.setRole(AccountRole.USER);
        account.setStatus(AccountStatus.ACTIVE);
        account.setValidUntil(OffsetDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.createLost(request, null, new CustomUserDetails(account)))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(storage, manager);
    }
}
