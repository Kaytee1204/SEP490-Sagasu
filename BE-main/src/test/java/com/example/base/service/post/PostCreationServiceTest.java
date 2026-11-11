package com.example.base.service.post;

import com.example.base.dto.post.CreateFoundPostRequest;
import com.example.base.dto.post.CreateLostPostRequest;
import com.example.base.dto.post.CreatedPostDto;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.CategoryRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCreationServiceTest {

    @Mock
    private PostRepository posts;
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private CategoryRepository categories;
    @Mock
    private AccountRepository accounts;
    @Mock
    private ImageStorage storage;
    @Mock
    private ImageValidator validator;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PostCreationService service;

    private Account author;
    private CustomUserDetails user;
    private Category category;

    @BeforeEach
    void setUp() {
        author = new Account();
        author.setId(7L);
        author.setEmail("author@fpt.edu.vn");
        author.setFullName("Author");
        author.setRole(AccountRole.USER);
        author.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(author);

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 3L);
        category.setName("Wallet");

        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void createLost_whenValidWithTwoImages_createsTrimmedPostWithOrderedImages() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, " Lost wallet ", " Black leather ", 3L, " Hall A ",
                OffsetDateTime.now().minusDays(1), null);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2})
        );
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 100, 100, "h1"),
                new ValidatedImage(new byte[]{2}, "image/jpeg", "jpg", 100, 100, "h2")
        );

        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(files)).thenReturn(validated);
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(author));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });

        CreatedPostDto result = service.createLost(request, files, user);

        assertEquals(100L, result.id());
        assertEquals("LOST", result.postType());
        assertEquals("OPEN", result.status());
        assertEquals("VISIBLE", result.moderationStatus());

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(posts).saveAndFlush(postCaptor.capture());
        Post saved = postCaptor.getValue();
        assertEquals("Lost wallet", saved.getTitle());
        assertEquals("Black leather", saved.getDescription());
        assertEquals("Hall A", saved.getLocationText());
        assertEquals(2, saved.getImages().size());
        assertEquals(0, saved.getImages().get(0).getSortOrder());
        assertEquals(1, saved.getImages().get(1).getSortOrder());
        assertTrue(saved.getImages().get(0).getStorageKey().matches("posts/7/[0-9a-f\\-]+\\.jpg"));
        assertTrue(saved.getImages().get(1).getStorageKey().matches("posts/7/[0-9a-f\\-]+\\.jpg"));
    }

    @Test
    void createLost_whenNoImages_createsPostWithZeroImages() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost keys", "Silver", 3L, "Hall B",
                OffsetDateTime.now().minusHours(5), null);
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(List.of())).thenReturn(List.of());
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(author));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 101L);
            return p;
        });

        CreatedPostDto result = service.createLost(request, List.of(), user);

        assertEquals(101L, result.id());
        verifyNoInteractions(storage);
    }

    @Test
    void createLost_whenCategoryInactive_throwsWithoutUploading() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> service.createLost(request, List.of(), user));

        assertEquals(ErrorCode.POST_CATEGORY_UNAVAILABLE, ex.getErrorCode());
        verifyNoInteractions(validator, storage);
    }

    @Test
    void createLost_whenValidatorRejectsFile_propagatesException() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("images", "doc.pdf", "application/pdf", new byte[]{1})
        );
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(files)).thenThrow(new AppException(ErrorCode.IMAGE_FORMAT_UNSUPPORTED));

        AppException ex = assertThrows(AppException.class,
                () -> service.createLost(request, files, user));

        assertEquals(ErrorCode.IMAGE_FORMAT_UNSUPPORTED, ex.getErrorCode());
        verifyNoInteractions(storage);
    }

    @Test
    void createLost_whenSecondStoragePutFails_cleansUpBothKeysAndRethrows() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 100, 100, "h1"),
                new ValidatedImage(new byte[]{2}, "image/jpeg", "jpg", 100, 100, "h2")
        );
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(validated);
        org.mockito.Mockito.doNothing().doThrow(new RuntimeException("R2 down"))
                .when(storage).put(anyString(), any(), anyString());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createLost(request, List.of(), user));

        assertEquals("R2 down", ex.getMessage());
        verify(storage, times(2)).delete(anyString());
    }

    @Test
    void createLost_whenAccountLockedAfterUpload_throwsForbiddenAndDeletesUploadedImages() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 100, 100, "h1")
        );
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(validated);
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.createLost(request, List.of(), user));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(storage, times(1)).delete(anyString());
    }

    @Test
    void createLost_whenUserNull_throwsUnauthorized() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);

        AppException ex = assertThrows(AppException.class,
                () -> service.createLost(request, List.of(), null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void createLost_whenPrivateIdentifiersProvided_storesTrimmedIdentifiers() {
        CreateLostPostRequest request = new CreateLostPostRequest(
                null, "Lost phone", "Blue", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), "  Serial ABC123  ");
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of());
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(author));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 102L);
            return p;
        });

        service.createLost(request, List.of(), user);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(posts).saveAndFlush(captor.capture());
        assertEquals("Serial ABC123", captor.getValue().getPrivateIdentifiers());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLost_whenRequestIdAlreadyProcessed_returnsExistingWithoutUpload() {
        UUID reqId = UUID.randomUUID();
        CreateLostPostRequest request = new CreateLostPostRequest(
                reqId, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        CreatedPostDto existing = new CreatedPostDto(55L, "LOST", "OPEN", "VISIBLE");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(reqId)))
                .thenReturn(List.of(existing));

        CreatedPostDto result = service.createLost(request, List.of(), user);

        assertEquals(existing, result);
        verifyNoInteractions(categories, validator, storage, posts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createLost_whenConcurrentRequestIdWinsInsideTransaction_reusesExistingAndDeletesUploadedImages() {
        UUID reqId = UUID.randomUUID();
        CreateLostPostRequest request = new CreateLostPostRequest(
                reqId, "Lost wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusDays(1), null);
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[]{1}, "image/jpeg", "jpg", 100, 100, "h1")
        );
        CreatedPostDto existing = new CreatedPostDto(56L, "LOST", "OPEN", "VISIBLE");

        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(reqId)))
                .thenReturn(List.of())
                .thenReturn(List.of(existing));
        doNothing().when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq("7:" + reqId));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(validated);

        CreatedPostDto result = service.createLost(request, List.of(), user);

        assertEquals(existing, result);
        verify(storage, times(1)).delete(anyString());
        verify(posts, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createFound_whenValidWithNullCustodyAndNewRequestId_createsP2pFoundPostAndRecordsRequest() {
        UUID reqId = UUID.randomUUID();
        CreateFoundPostRequest request = new CreateFoundPostRequest(
                reqId, " Found card ", " Student ID ", 3L, " Library ",
                OffsetDateTime.now().minusHours(2), null, null, null);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(7L), eq(reqId)))
                .thenReturn(List.of(), List.of());
        doNothing().when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq("7:" + reqId));
        when(categories.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of());
        when(accounts.findActiveAccountForPostCreation(7L)).thenReturn(Optional.of(author));
        when(categories.findActiveForPostCreation(3L)).thenReturn(Optional.of(category));
        when(posts.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 103L);
            return p;
        });

        CreatedPostDto result = service.createFound(request, List.of(), user);

        assertEquals(103L, result.id());
        assertEquals("FOUND", result.postType());
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(posts).saveAndFlush(captor.capture());
        assertEquals("P2P", captor.getValue().getCustodyMode());
        verify(jdbc).update(anyString(), eq(7L), eq(reqId), eq(103L));
    }

    @Test
    void createFound_whenCustodyOffice_throwsPostCustodyInvalid() {
        CreateFoundPostRequest request = new CreateFoundPostRequest(
                null, "Found wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusHours(2), "OFFICE", null, null);

        AppException ex = assertThrows(AppException.class,
                () -> service.createFound(request, List.of(), user));

        assertEquals(ErrorCode.POST_CUSTODY_INVALID, ex.getErrorCode());
    }

    @Test
    void createFound_whenHandoverRequestedAtProvided_throwsPostHandoverTimeDisabled() {
        CreateFoundPostRequest request = new CreateFoundPostRequest(
                null, "Found wallet", "Black", 3L, "Hall A",
                OffsetDateTime.now().minusHours(2), "P2P", null, OffsetDateTime.now().plusDays(1));

        AppException ex = assertThrows(AppException.class,
                () -> service.createFound(request, List.of(), user));

        assertEquals(ErrorCode.POST_HANDOVER_TIME_DISABLED, ex.getErrorCode());
    }
}

