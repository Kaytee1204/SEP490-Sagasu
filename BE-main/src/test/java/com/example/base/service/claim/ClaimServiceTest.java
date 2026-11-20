package com.example.base.service.claim;

import com.example.base.dto.claim.CancelClaimRequest;
import com.example.base.dto.claim.ClaimDetailDto;
import com.example.base.dto.claim.ClaimPageDto;
import com.example.base.dto.claim.ClaimPostGroupDto;
import com.example.base.dto.claim.ClaimQuestionnaireDto;
import com.example.base.dto.claim.ClaimResultDto;
import com.example.base.dto.claim.ReviewClaimRequest;
import com.example.base.dto.claim.SubmitClaimRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Claim;
import com.example.base.entity.ClaimActivity;
import com.example.base.entity.ClaimAnswer;
import com.example.base.entity.ClaimEvidence;
import com.example.base.entity.ClaimQuestion;
import com.example.base.entity.ClaimQuestionTemplate;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.ClaimActivityRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.P2pHandoverRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.notification.NotificationService;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One test per UTC of sheet "Claim" for ClaimService (UTC001-UTC070). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimServiceTest {
    @Mock private ClaimDailyLimit dailyLimit;

    @Test
    void dailyLimitRejectsBeforeSavingP2pDossier() {
        var req = new SubmitClaimRequest("Description", null, "v1", List.of());
        when(posts.findById(p2pPost.getId())).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(questionnaires.validateAndSnapshot(p2pPost, req)).thenReturn(List.of());
        when(validator.validate(any())).thenReturn(List.of());
        doThrow(new AppException(ErrorCode.CLAIM_DAILY_LIMIT)).when(dailyLimit).requireAvailable(20L);
        var error = assertThrows(AppException.class, () -> service.submit(100L, req, List.of(), claimantUser));
        assertEquals(ErrorCode.CLAIM_DAILY_LIMIT, error.getErrorCode());
        verify(claims, never()).saveAndFlush(any());
    }

    @Mock
    private ClaimRepository claims;
    @Mock
    private ClaimActivityRepository activities;
    @Mock
    private PostRepository posts;
    @Mock
    private AccountRepository accounts;
    @Mock
    private ClaimRoutingPolicy routing;
    @Mock
    private P2pHandoverRepository handovers;
    @Mock
    private ImageStorage storage;
    @Mock
    private ImageValidator validator;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ClaimQuestionnaireService questionnaires;
    @Mock
    private ChatConversationService chatConversations;
    @Mock
    private NotificationService notifications;

    @Mock
    private P2pClaimAppealService appeals;

    @InjectMocks
    private ClaimService service;

    private Account finder;
    private Account claimant;
    private Account staff;
    private Account outsider;
    private CustomUserDetails finderUser;
    private CustomUserDetails claimantUser;
    private CustomUserDetails staffUser;
    private CustomUserDetails outsiderUser;
    private Post p2pPost;

    @BeforeEach
    void setUp() {
        finder = account(10L, "Finder", AccountRole.USER);
        claimant = account(20L, "Claimant", AccountRole.USER);
        staff = account(30L, "Staff", AccountRole.APPROVER);
        outsider = account(40L, "Outsider", AccountRole.USER);
        finderUser = new CustomUserDetails(finder);
        claimantUser = new CustomUserDetails(claimant);
        staffUser = new CustomUserDetails(staff);
        outsiderUser = new CustomUserDetails(outsider);
        for (Account a : List.of(finder, claimant, staff, outsider)) {
            when(accounts.findById(a.getId())).thenReturn(Optional.of(a));
        }

        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 3L);
        cat.setName("Wallet");
        p2pPost = Post.found(finder, cat, "Black wallet", "Found at C201", "Hall C",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(p2pPost, "id", 100L);

        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(validator.validate(any())).thenReturn(List.of());
        doAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        }).when(claims).saveAndFlush(any(Claim.class));
    }

    // ---------------------------------------------------------------- submit

    @Test
    void utc001_submit_validP2pClaimSavesEvidenceActivityAndNotifiesFinder() {
        SubmitClaimRequest req = new SubmitClaimRequest(" My brown wallet ", " Has student ID ", "v1", List.of());
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "proof.jpg", "image/jpeg", new byte[]{1, 2, 3}));
        when(validator.validate(files)).thenReturn(List.of(
                new ValidatedImage(new byte[]{1, 2, 3}, "image/jpeg", "jpg", 100, 100, "hash1")));
        when(questionnaires.validateAndSnapshot(p2pPost, req)).thenReturn(List.of(buildAnswer("Q_1", "Brown leather")));

        ClaimResultDto result = service.submit(100L, req, files, claimantUser);

        assertEquals(500L, result.id());
        assertEquals(100L, result.postId());
        assertEquals("PENDING", result.status());
        verify(storage).put(anyString(), eq(new byte[]{1, 2, 3}), eq("image/jpeg"));
        verify(activities).saveAndFlush(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void utc002_submit_nullRequestThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.submit(100L, null, List.of(), claimantUser)));
    }

    @Test
    void utc003_submit_blankDescriptionThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.submit(100L, new SubmitClaimRequest("   ", null, "v1", List.of()), List.of(), claimantUser)));
    }

    @Test
    void utc004_submit_descriptionAtMax5000Succeeds() {
        ClaimResultDto result = service.submit(
                100L, new SubmitClaimRequest("a".repeat(5000), null, "v1", List.of()), List.of(), claimantUser);
        assertEquals(500L, result.id());
        assertEquals("PENDING", result.status());
    }

    @Test
    void utc005_submit_description5001ThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.submit(
                100L, new SubmitClaimRequest("a".repeat(5001), null, "v1", List.of()), List.of(), claimantUser)));
    }

    @Test
    void utc006_submit_evidenceNote5001ThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.submit(
                100L, new SubmitClaimRequest("valid", "b".repeat(5001), "v1", List.of()), List.of(), claimantUser)));
    }

    @Test
    void utc007_submit_finderClaimingOwnPostThrowsSelfNotAllowed() {
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED, code(() -> service.submit(100L, validSubmitReq(), List.of(), finderUser)));
    }

    @Test
    void utc008_submit_postCreatedByClaimantThrowsSelfNotAllowed() {
        ReflectionTestUtils.setField(p2pPost, "createdBy", claimant);
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED, code(() -> service.submit(100L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc009_submit_lostPostThrowsPostUnavailable() {
        Post lostPost = Post.lost(finder, p2pPost.getCategory(), "Lost", "Desc", "Hall A", OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(lostPost, "id", 101L);
        when(posts.findById(101L)).thenReturn(Optional.of(lostPost));
        when(routing.canSubmit(lostPost)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_POST_UNAVAILABLE, code(() -> service.submit(101L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc010_submit_activeDuplicateClaimThrowsDuplicate() {
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_DUPLICATE, code(() -> service.submit(100L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc011_submit_postIdZeroThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.submit(0L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc012_submit_missingPostThrowsClaimNotFound() {
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.submit(999L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc013_submit_nullUserThrowsUnauthorized() {
        assertEquals(ErrorCode.UNAUTHORIZED, code(() -> service.submit(100L, validSubmitReq(), List.of(), null)));
    }

    @Test
    void utc014_submit_claimantSuspendedAfterRefreshThrowsForbidden() {
        doAnswer(inv -> {
            ReflectionTestUtils.setField(claimant, "status", AccountStatus.SUSPENDED);
            return null;
        }).when(entityManager).refresh(claimant);
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.submit(100L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc015_submit_uniqueViolationMapsToDuplicateAndDeletesUploads() {
        List<MultipartFile> files = oneEvidenceFile("proof.jpg");
        doThrow(new DataIntegrityViolationException("uq_active")).when(claims).saveAndFlush(any(Claim.class));

        assertEquals(ErrorCode.CLAIM_DUPLICATE, code(() -> service.submit(100L, validSubmitReq(), files, claimantUser)));
        verify(storage).delete(anyString());
    }

    @Test
    void utc016_submit_storagePutFailureDeletesKeyAndRethrows() {
        List<MultipartFile> files = oneEvidenceFile("proof.jpg");
        doThrow(new RuntimeException("S3 put failed")).when(storage).put(anyString(), any(), anyString());

        assertThrows(RuntimeException.class, () -> service.submit(100L, validSubmitReq(), files, claimantUser));
        verify(storage).delete(anyString());
    }

    @Test
    void utc017_submit_windowsPathStrippedFromEvidenceFilename() {
        service.submit(100L, validSubmitReq(), oneEvidenceFile("C:\\Users\\a\\pic.jpg"), claimantUser);

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claims).saveAndFlush(saved.capture());
        assertEquals("pic.jpg", saved.getValue().getEvidence().get(0).getOriginalName());
    }

    @Test
    void utc018_submit_blankFullNameFallsBackToThanhVien() {
        ReflectionTestUtils.setField(claimant, "fullName", "   ");
        service.submit(100L, validSubmitReq(), List.of(), claimantUser);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyUser(eq(10L), eq("CLAIM_SUBMITTED"), anyString(), body.capture(), anyString(), eq(true));
        assertTrue(body.getValue().startsWith("Thành viên "));
    }

    /** Supplementary: evidence uploaded before a generic transaction failure is cleaned up. */
    @Test
    void utc019_submit_whenTransactionThrowsAfterUpload_deletesUploadedKeys() {
        List<MultipartFile> files = oneEvidenceFile("proof.jpg");
        doThrow(new RuntimeException("DB fail")).when(claims).saveAndFlush(any());

        assertThrows(RuntimeException.class, () -> service.submit(100L, validSubmitReq(), files, claimantUser));
        verify(storage).delete(anyString());
    }

    // ---------------------------------------------------------------- retry

    @Test
    void utc020_retry_rejectedOwnedClaimResubmits() {
        when(claims.findById(499L)).thenReturn(Optional.of(p2pClaim(499L, "REJECTED", 0L)));
        ClaimResultDto result = service.retry(499L, validSubmitReq(), List.of(), claimantUser);
        assertEquals(500L, result.id());
        assertEquals("PENDING", result.status());
    }

    @Test
    void utc021_retry_cancelledClaimCanBeRetried() {
        when(claims.findById(480L)).thenReturn(Optional.of(p2pClaim(480L, "CANCELLED", 1L)));
        assertEquals(500L, service.retry(480L, validSubmitReq(), List.of(), claimantUser).id());
    }

    @Test
    void utc022_retry_pendingPreviousThrowsStateInvalid() {
        when(claims.findById(499L)).thenReturn(Optional.of(p2pClaim(499L, "PENDING", 0L)));
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, code(() -> service.retry(499L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc023_retry_claimOwnedByAnotherUserThrowsNotFound() {
        Claim others = Claim.submit(p2pPost, outsider, "Other", null);
        ReflectionTestUtils.setField(others, "id", 482L);
        ReflectionTestUtils.setField(others, "status", "REJECTED");
        when(claims.findById(482L)).thenReturn(Optional.of(others));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.retry(482L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc024_retry_officeClaimThrowsNotFound() {
        Claim office = officeClaim(483L);
        ReflectionTestUtils.setField(office, "status", "REJECTED");
        when(claims.findById(483L)).thenReturn(Optional.of(office));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.retry(483L, validSubmitReq(), List.of(), claimantUser)));
    }

    @Test
    void utc025_retry_custodyChangedToOfficeRejectsRetry() {
        when(claims.findById(484L)).thenReturn(Optional.of(p2pClaim(484L, "REJECTED", 1L)));
        ReflectionTestUtils.setField(p2pPost, "custodyMode", "OFFICE");

        when(routing.canSubmit(p2pPost)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_POST_UNAVAILABLE, code(() -> service.retry(484L, validSubmitReq(), List.of(), claimantUser)));

        when(routing.canSubmit(p2pPost)).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, code(() -> service.retry(484L, validSubmitReq(), List.of(), claimantUser)));
    }

    // ---------------------------------------------------------------- questionnaire / latestOnPost

    @Test
    void utc026_questionnaire_returnsCategoryQuestionnaire() {
        ClaimQuestionnaireDto dto = new ClaimQuestionnaireDto("v1", 3L, "Wallet", List.of(), List.of());
        when(questionnaires.forCategory(p2pPost.getCategory())).thenReturn(dto);
        assertEquals(dto, service.questionnaire(100L, claimantUser));
    }

    @Test
    void utc027_questionnaire_postOwnerThrowsSelfNotAllowed() {
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED, code(() -> service.questionnaire(100L, finderUser)));
    }

    @Test
    void utc028_latestOnPost_returnsMostRecentClaim() {
        Claim latest = Claim.submit(p2pPost, claimant, "Mine", null);
        ReflectionTestUtils.setField(latest, "id", 510L);
        when(claims.findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(100L, 20L, "P2P"))
                .thenReturn(Optional.of(latest));
        assertEquals(510L, service.latestOnPost(100L, claimantUser).id());
    }

    @Test
    void utc029_latestOnPost_noClaimReturnsNull() {
        assertNull(service.latestOnPost(100L, claimantUser));
    }

    @Test
    void utc030_latestOnPost_postIdZeroThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.latestOnPost(0L, claimantUser)));
    }

    // ---------------------------------------------------------------- listings

    @Test
    void utc031_mine_mapsSummaryWithThumbnail() {
        Claim c = p2pClaim(520L, "PENDING", 0L);
        PostImage img = new PostImage("posts/10/thumb.jpg", 0, "image/jpeg", 50L, 10, 10, "h");
        ReflectionTestUtils.setField(img, "post", p2pPost);
        when(claims.findMine(eq(20L), eq("HISTORY"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1));
        stubThumbnails(List.of(img));
        when(storage.readUrl("posts/10/thumb.jpg", null)).thenReturn("https://cdn/thumb.jpg");

        ClaimPageDto page = service.mine(1, 10, "HISTORY", claimantUser);

        assertEquals(1, page.content().size());
        assertEquals(520L, page.content().get(0).id());
        assertEquals("https://cdn/thumb.jpg", page.content().get(0).postThumbnailUrl());
        assertNotNull(page.content().get(0).descriptionPreview());
    }

    @Test
    void utc032_mine_unknownStatusThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.mine(1, 10, "UNKNOWN", claimantUser)));
    }

    @Test
    void utc033_mine_nullStatusThrowsInputInvalid() {
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.mine(1, 10, null, claimantUser)));
    }

    @Test
    void utc034_mine_pageZeroThrowsPaginationInvalid() {
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, code(() -> service.mine(0, 10, "ALL", claimantUser)));
    }

    @Test
    void utc035_mine_size101ThrowsPaginationInvalid() {
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, code(() -> service.mine(1, 101, "ALL", claimantUser)));
    }

    @Test
    void utc036_received_returnsPendingClaimsForOwner() {
        stubThumbnails(List.of());
        when(claims.findReceived(10L, "PENDING", PageRequest.of(0, 10))).thenReturn(new PageImpl<>(
                List.of(p2pClaim(521L, "PENDING", 0L), p2pClaim(522L, "PENDING", 0L)), PageRequest.of(0, 10), 2));
        assertEquals(2, service.received(1, 10, "PENDING", finderUser).content().size());
    }

    @Test
    void utc037_office_userRoleThrowsForbidden() {
        assertEquals(ErrorCode.FORBIDDEN, code(() -> service.office(1, 10, "ALL", claimantUser)));
    }

    @Test
    void utc038_office_approverQueriesOfficeClaims() {
        when(claims.findOffice(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        assertTrue(service.office(1, 10, "PENDING", staffUser).content().isEmpty());
        verify(claims).findOffice(eq("PENDING"), any(PageRequest.class));
    }

    @Test
    void utc039_onPost_outsiderThrowsNotFound() {
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.onPost(100L, 1, 10, "ALL", outsiderUser)));
    }

    @Test
    void utc040_onPost_ownerQueriesNonApproverView() {
        when(claims.findOnPost(eq(100L), eq(10L), eq(false), eq("ALL"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        assertTrue(service.onPost(100L, 1, 10, "ALL", finderUser).content().isEmpty());
        verify(claims).findOnPost(eq(100L), eq(10L), eq(false), eq("ALL"), any(PageRequest.class));
    }

    /** Supplementary: grouped() returns claim summaries grouped by post. */
    @Test
    @SuppressWarnings("unchecked")
    void utc041_grouped_whenMatchingPostsExist_returnsGroupedClaims() {
        Claim c = p2pClaim(530L, "PENDING", 0L);
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        TypedQuery<Long> idsQuery = mock(TypedQuery.class);
        TypedQuery<Claim> claimsQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery, idsQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(idsQuery.setParameter(anyString(), any())).thenReturn(idsQuery);
        when(idsQuery.setFirstResult(anyInt())).thenReturn(idsQuery);
        when(idsQuery.setMaxResults(anyInt())).thenReturn(idsQuery);
        when(idsQuery.getResultList()).thenReturn(List.of(100L));
        when(entityManager.createQuery(anyString(), eq(Claim.class))).thenReturn(claimsQuery);
        when(claimsQuery.setParameter(anyString(), any())).thenReturn(claimsQuery);
        when(claimsQuery.getResultList()).thenReturn(List.of(c));
        stubThumbnails(List.of());

        ClaimPostGroupDto.PageData grouped = service.grouped(1, 10, "HISTORY", true, null, claimantUser);

        assertEquals(1, grouped.content().size());
        assertEquals(100L, grouped.content().get(0).postId());
        assertEquals(1, grouped.content().get(0).claims().size());
    }

    // ---------------------------------------------------------------- detail / detailOnPost

    @Test
    void utc042_detail_claimantSeesOwnPendingClaim() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", "ID card");
        ReflectionTestUtils.setField(c, "id", 540L);
        c.addEvidence(new ClaimEvidence("claims/540/1.jpg", "1.jpg", "image/jpeg", 5L, "h", 0));
        when(claims.findById(540L)).thenReturn(Optional.of(c));
        when(routing.matches(eq(c), any())).thenReturn(true);
        when(storage.readUrl("claims/540/1.jpg", null)).thenReturn("https://cdn/1.jpg");

        ClaimDetailDto dto = service.detail(540L, claimantUser);
        assertEquals(540L, dto.id());
        assertTrue(dto.canCancel());
        assertFalse(dto.canReview());
        assertEquals(540L, service.detailOnPost(100L, 540L, claimantUser).id());
    }

    @Test
    void utc043_detail_ownerSeesCanReview() {
        Claim pending = p2pClaim(541L, "PENDING", 0L);
        when(claims.findById(541L)).thenReturn(Optional.of(pending));
        when(routing.reviewer(pending, finder)).thenReturn(true);
        when(routing.matches(pending, Set.of("OPEN"))).thenReturn(true);
        assertTrue(service.detail(541L, finderUser).canReview());
    }

    @Test
    void utc044_detail_outsiderThrowsNotFound() {
        when(claims.findById(540L)).thenReturn(Optional.of(p2pClaim(540L, "PENDING", 0L)));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.detail(540L, outsiderUser)));
    }

    @Test
    void utc045_detail_rejectedWithoutOtherActiveClaimCanRetry() {
        stubRejectedDetail();
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(false);
        assertTrue(service.detail(542L, claimantUser).canRetry());
    }

    @Test
    void utc046_detail_rejectedWithOtherActiveClaimCannotRetry() {
        stubRejectedDetail();
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(true);
        assertFalse(service.detail(542L, claimantUser).canRetry());
    }

    @Test
    void utc047_detailOnPost_mismatchedPostIdThrowsNotFound() {
        stubRejectedDetail();
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.detailOnPost(101L, 542L, claimantUser)));
    }

    @Test
    void utc048_detail_officeClaimThrowsNotFound() {
        when(claims.findById(543L)).thenReturn(Optional.of(officeClaim(543L)));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.detail(543L, claimantUser)));
    }

    // ---------------------------------------------------------------- review

    @Test
    void utc049_review_approvedOpensChatAndNotifies() {
        Claim c = reviewableClaim(550L);
        ClaimResultDto result = service.review(550L, new ReviewClaimRequest("APPROVED", "Matched details", 0L), finderUser);

        assertEquals("APPROVED", result.status());
        verify(chatConversations).forApprovedClaim(c);
        verify(activities).save(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(20L), anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    @Test
    void utc050_review_rejectedSavesActivityAndNotifiesWithReason() {
        reviewableClaim(553L);
        ClaimResultDto result = service.review(553L, new ReviewClaimRequest("REJECTED", "Wrong colour", 0L), finderUser);

        assertEquals("REJECTED", result.status());
        verify(activities).save(any(ClaimActivity.class));
        verify(chatConversations, never()).forApprovedClaim(any());
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyUser(eq(20L), eq("CLAIM_REJECTED"), anyString(), msg.capture(), anyString(), eq(true));
        assertTrue(msg.getValue().contains("Wrong colour"));
    }

    @Test
    void utc051_review_officeClaimOnP2pEndpointThrowsNotFound() {
        stubLockForAction(551L, officeClaim(551L));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND,
                code(() -> service.review(551L, new ReviewClaimRequest("APPROVED", "Reason", 0L), staffUser)));
        verifyNoInteractions(chatConversations);
    }

    @Test
    void utc052_review_nonOwnerThrowsNotFound() {
        stubLockForAction(554L, p2pClaim(554L, "PENDING", 0L));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "ok", 0L), outsiderUser)));
    }

    @Test
    void utc053_review_claimantReviewingOwnClaimThrowsNotFound() {
        stubLockForAction(554L, p2pClaim(554L, "PENDING", 0L));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "ok", 0L), claimantUser)));
    }

    @Test
    void utc054_review_unsupportedDecisionThrowsInputInvalid() {
        reviewableClaim(554L);
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("MAYBE", "Reason", 0L), finderUser)));
    }

    @Test
    void utc055_review_emptyReasonThrowsInputInvalid() {
        reviewableClaim(554L);
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "", 0L), finderUser)));
    }

    @Test
    void utc056_review_reason2001ThrowsInputInvalid() {
        reviewableClaim(554L);
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "x".repeat(2001), 0L), finderUser)));
    }

    @Test
    void utc057_review_reason2000Accepted() {
        reviewableClaim(556L);
        assertEquals("APPROVED",
                service.review(556L, new ReviewClaimRequest("APPROVED", "r".repeat(2000), 0L), finderUser).status());
    }

    @Test
    void utc058_review_versionMismatchThrowsStale() {
        Claim c = reviewableClaim(552L);
        ReflectionTestUtils.setField(c, "version", 2L);
        assertEquals(ErrorCode.CLAIM_STALE,
                code(() -> service.review(552L, new ReviewClaimRequest("REJECTED", "Mismatch", 0L), finderUser)));
    }

    @Test
    void utc059_review_nullExpectedVersionThrowsInputInvalid() {
        reviewableClaim(554L);
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", null), finderUser)));
    }

    @Test
    void utc060_review_negativeExpectedVersionThrowsInputInvalid() {
        reviewableClaim(554L);
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", -1L), finderUser)));
    }

    @Test
    void utc061_review_alreadyApprovedThrowsStateInvalid() {
        Claim c = reviewableClaim(555L);
        ReflectionTestUtils.setField(c, "status", "APPROVED");
        assertEquals(ErrorCode.CLAIM_STATE_INVALID,
                code(() -> service.review(555L, new ReviewClaimRequest("REJECTED", "Late", 0L), finderUser)));
    }

    @Test
    void utc062_review_anotherApprovedClaimOnPostThrowsStateInvalid() {
        reviewableClaim(554L);
        when(claims.existsByPost_IdAndStatusIn(100L, Set.of("APPROVED"))).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID,
                code(() -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", 0L), finderUser)));
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void utc063_cancel_pendingByClaimantNotifiesFinder() {
        Claim c = cancellableClaim(560L, "PENDING");
        ClaimResultDto result = service.cancel(560L, new CancelClaimRequest(1L, "Found mine"), claimantUser);

        assertEquals("CANCELLED", result.status());
        verify(chatConversations).notifyChanged(c);
        verify(activities).save(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void utc064_cancel_approvedClaimReopensPost() {
        p2pPost.reserveForClaim();
        cancellableClaim(562L, "APPROVED");
        assertEquals("CANCELLED", service.cancel(562L, new CancelClaimRequest(1L, "Changed my mind"), claimantUser).status());
        assertEquals("OPEN", p2pPost.getStatus());
    }

    @Test
    void utc065_cancel_approvedWithHandoverThrowsStateInvalid() {
        cancellableClaim(561L, "APPROVED");
        when(handovers.existsByClaimId(561L)).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID,
                code(() -> service.cancel(561L, new CancelClaimRequest(1L, "No longer needed"), claimantUser)));
    }

    @Test
    void utc066_cancel_rejectedClaimThrowsStateInvalid() {
        cancellableClaim(563L, "REJECTED");
        assertEquals(ErrorCode.CLAIM_STATE_INVALID,
                code(() -> service.cancel(563L, new CancelClaimRequest(1L, "No longer needed"), claimantUser)));
    }

    @Test
    void utc067_cancel_nonClaimantThrowsNotFound() {
        cancellableClaim(563L, "PENDING");
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, code(() -> service.cancel(563L, new CancelClaimRequest(1L, "No"), outsiderUser)));
    }

    @Test
    void utc068_cancel_nullRequestThrowsInputInvalid() {
        cancellableClaim(563L, "PENDING");
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, code(() -> service.cancel(563L, null, claimantUser)));
    }

    @Test
    void utc069_cancel_nullReasonAccepted() {
        cancellableClaim(564L, "PENDING");
        assertEquals("CANCELLED", service.cancel(564L, new CancelClaimRequest(1L, null), claimantUser).status());
    }

    @Test
    void utc070_cancel_reason2001ThrowsInputInvalid() {
        cancellableClaim(563L, "PENDING");
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID,
                code(() -> service.cancel(563L, new CancelClaimRequest(1L, "z".repeat(2001)), claimantUser)));
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorCode code(Executable call) {
        return assertThrows(AppException.class, call).getErrorCode();
    }

    private static SubmitClaimRequest validSubmitReq() {
        return new SubmitClaimRequest("Brown leather wallet", null, "v1", List.of());
    }

    private List<MultipartFile> oneEvidenceFile(String filename) {
        List<MultipartFile> files = List.of(new MockMultipartFile("files", filename, "image/jpeg", new byte[]{1, 2}));
        when(validator.validate(files)).thenReturn(List.of(
                new ValidatedImage(new byte[]{1, 2}, "image/jpeg", "jpg", 10, 10, "h1")));
        return files;
    }

    @SuppressWarnings("unchecked")
    private void stubThumbnails(List<PostImage> images) {
        TypedQuery<PostImage> q = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(PostImage.class))).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.setParameter(eq("postIds"), anyList())).thenReturn(q);
        when(q.getResultList()).thenReturn(images);
    }

    private void stubRejectedDetail() {
        Claim rejected = p2pClaim(542L, "REJECTED", 0L);
        when(claims.findById(542L)).thenReturn(Optional.of(rejected));
        when(routing.matches(rejected, Set.of("OPEN"))).thenReturn(true);
    }

    private void stubLockForAction(long claimId, Claim claim) {
        when(claims.findPostId(claimId)).thenReturn(Optional.of(100L));
        when(claims.findForUpdate(claimId)).thenReturn(Optional.of(claim));
    }

    /** PENDING claim the finder may review (version 0, post OPEN). */
    private Claim reviewableClaim(long id) {
        Claim c = p2pClaim(id, "PENDING", 0L);
        stubLockForAction(id, c);
        when(routing.reviewer(c, finder)).thenReturn(true);
        when(routing.matches(c, Set.of("OPEN"))).thenReturn(true);
        return c;
    }

    /** Claim in the given status that the claimant may try to cancel (version 1). */
    private Claim cancellableClaim(long id, String status) {
        Claim c = p2pClaim(id, status, 1L);
        stubLockForAction(id, c);
        when(routing.matches(c, Set.of("OPEN", "RESERVED"))).thenReturn(true);
        return c;
    }

    private Claim p2pClaim(long id, String status, long version) {
        Claim c = Claim.submit(p2pPost, claimant, "Wallet description", null);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "status", status);
        ReflectionTestUtils.setField(c, "version", version);
        ReflectionTestUtils.setField(c, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(c, "updatedAt", OffsetDateTime.now());
        return c;
    }

    private Claim officeClaim(long id) {
        Claim c = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(c, "id", id);
        ReflectionTestUtils.setField(c, "claimType", "OFFICE");
        ReflectionTestUtils.setField(c, "claimant", claimant);
        ReflectionTestUtils.setField(c, "post", p2pPost);
        return c;
    }

    private static Account account(Long id, String name, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setEmail(name.toLowerCase() + "@gmail.com");
        a.setFullName(name);
        a.setRole(role);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private static ClaimAnswer buildAnswer(String code, String answerText) {
        ClaimQuestionTemplate template = BeanUtils.instantiateClass(ClaimQuestionTemplate.class);
        ReflectionTestUtils.setField(template, "code", "COMMON");
        ReflectionTestUtils.setField(template, "title", "Common");
        ReflectionTestUtils.setField(template, "version", 1);
        ClaimQuestion q = BeanUtils.instantiateClass(ClaimQuestion.class);
        ReflectionTestUtils.setField(q, "template", template);
        ReflectionTestUtils.setField(q, "code", code);
        ReflectionTestUtils.setField(q, "label", "Color");
        return new ClaimAnswer(q, answerText, 1);
    }
}
