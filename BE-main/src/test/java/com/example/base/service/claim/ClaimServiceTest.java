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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

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

        Category cat = new Category();
        ReflectionTestUtils.setField(cat, "categoryId", 3L);
        cat.setName("Wallet");

        p2pPost = Post.found(finder, cat, "Black wallet", "Found at C201", "Hall C",
                OffsetDateTime.now().minusDays(1), "P2P", null);
        ReflectionTestUtils.setField(p2pPost, "id", 100L);

        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    // UTC001 - submit valid P2P claim saves Claim, answers, evidence in R2, SUBMIT activity, and notifies owner
    @Test
    void submit_whenValidP2pClaimWithEvidence_savesClaimAnswersActivityAndNotifiesFinder() {
        SubmitClaimRequest req = new SubmitClaimRequest(" My brown wallet ", " Has student ID ", "v1", List.of());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "proof.jpg", "image/jpeg", new byte[] { 1, 2, 3 }));
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[] { 1, 2, 3 }, "image/jpeg", "jpg", 100, 100, "hash1"));
        ClaimAnswer answer = buildAnswer("Q_1", "Brown leather");

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(false);
        when(questionnaires.validateAndSnapshot(p2pPost, req)).thenReturn(List.of(answer));
        when(validator.validate(files)).thenReturn(validated);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.lockReceipt(p2pPost)).thenReturn(null);
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        });

        ClaimResultDto result = service.submit(100L, req, files, claimantUser);

        assertEquals(500L, result.id());
        assertEquals(100L, result.postId());
        assertEquals("PENDING", result.status());
        verify(storage).put(anyString(), eq(new byte[] { 1, 2, 3 }), eq("image/jpeg"));
        verify(activities).saveAndFlush(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    // UTC007, UTC010 - submit throws CLAIM_SELF_NOT_ALLOWED for owner self-claim and CLAIM_DUPLICATE for active duplicate
    @Test
    void submit_whenOwnerClaimsOwnPostOrDuplicateClaim_throwsExpectedError() {
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "v1", List.of());

        // UTC007 - post owner attempting self-claim throws CLAIM_SELF_NOT_ALLOWED
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED,
                assertThrows(AppException.class, () -> service.submit(100L, req, List.of(), finderUser))
                        .getErrorCode());

        // UTC010 - claimant with existing PENDING claim on post throws CLAIM_DUPLICATE
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_DUPLICATE,
                assertThrows(AppException.class, () -> service.submit(100L, req, List.of(), claimantUser))
                        .getErrorCode());
    }

    // Supplementary (ClaimService.submit): deletes uploaded evidence keys when transaction save fails
    @Test
    void submit_whenTransactionThrowsAfterUpload_deletesUploadedKeys() {
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "v1", List.of());
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "proof.jpg", "image/jpeg", new byte[] { 1 }));
        List<ValidatedImage> validated = List.of(
                new ValidatedImage(new byte[] { 1 }, "image/jpeg", "jpg", 10, 10, "h1"));

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(false);
        when(questionnaires.validateAndSnapshot(p2pPost, req)).thenReturn(List.of());
        when(validator.validate(files)).thenReturn(validated);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        doThrow(new RuntimeException("DB fail")).when(claims).saveAndFlush(any());

        assertThrows(RuntimeException.class, () -> service.submit(100L, req, files, claimantUser));

        verify(storage, times(1)).delete(anyString());
    }

    // UTC019 - retry creates new PENDING claim when previous owned claim is REJECTED
    @Test
    void retry_whenRejectedOwnedClaim_resubmitsNewClaim() {
        Claim previous = Claim.submit(p2pPost, claimant, "Old desc", null);
        ReflectionTestUtils.setField(previous, "id", 499L);
        previous.review("REJECTED");
        SubmitClaimRequest req = new SubmitClaimRequest("New detailed desc", null, "v1", List.of());

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findById(499L)).thenReturn(Optional.of(previous));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any())).thenReturn(false);
        when(questionnaires.validateAndSnapshot(p2pPost, req)).thenReturn(List.of());
        when(validator.validate(any())).thenReturn(List.of());
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 501L);
            return c;
        });

        ClaimResultDto result = service.retry(499L, req, List.of(), claimantUser);
        assertEquals(501L, result.id());
        assertEquals("PENDING", result.status());
    }

    // UTC021 - retry throws CLAIM_STATE_INVALID when previous claim is PENDING
    @Test
    void retry_whenPreviousNotRejected_throwsClaimStateInvalid() {
        Claim pending = Claim.submit(p2pPost, claimant, "desc", null);
        ReflectionTestUtils.setField(pending, "id", 499L);
        SubmitClaimRequest req = new SubmitClaimRequest("New desc", null, "v1", List.of());

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findById(499L)).thenReturn(Optional.of(pending));

        AppException ex = assertThrows(AppException.class,
                () -> service.retry(499L, req, List.of(), claimantUser));
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, ex.getErrorCode());
    }

    // UTC025, UTC027 - questionnaire returns category DTO and latestOnPost returns most recent ClaimResultDto
    @Test
    void questionnaire_and_latestOnPost_returnExpectedDtos() {
        ClaimQuestionnaireDto qDto = new ClaimQuestionnaireDto("v1", 3L, "Wallet", List.of(), List.of());
        Claim latest = Claim.submit(p2pPost, claimant, "Mine", null);
        ReflectionTestUtils.setField(latest, "id", 510L);

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(routing.canSubmit(p2pPost)).thenReturn(true);
        when(questionnaires.forCategory(p2pPost.getCategory())).thenReturn(qDto);

        // UTC025 - questionnaire returns active questionnaire DTO for claimable post
        assertEquals(qDto, service.questionnaire(100L, claimantUser));

        // UTC027 - latestOnPost returns most recent claim on post
        when(claims.findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(100L, 20L, "P2P"))
                .thenReturn(Optional.of(latest));
        ClaimResultDto res = service.latestOnPost(100L, claimantUser);
        assertEquals(510L, res.id());
    }

    // UTC030 - mine maps summary DTO fields with thumbnails (plus empty received check)
    @Test
    @SuppressWarnings("unchecked")
    void mine_and_received_whenHistoryFilterAndThumbnailsPresent_mapTwelveFieldSummary() {
        Claim c = Claim.submit(p2pPost, claimant, "Black leather wallet with ID inside", null);
        ReflectionTestUtils.setField(c, "id", 520L);
        ReflectionTestUtils.setField(c, "createdAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(c, "updatedAt", OffsetDateTime.now());

        PostImage img = new PostImage("posts/10/thumb.jpg", 0, "image/jpeg", 50L, 10, 10, "h");
        ReflectionTestUtils.setField(img, "post", p2pPost);

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findMine(eq(20L), eq("HISTORY"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 10), 1));

        TypedQuery<PostImage> thumbQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(PostImage.class))).thenReturn(thumbQuery);
        when(thumbQuery.setParameter(anyString(), any())).thenReturn(thumbQuery);
        when(thumbQuery.getResultList()).thenReturn(List.of(img));
        when(storage.readUrl("posts/10/thumb.jpg", null)).thenReturn("https://cdn/thumb.jpg");

        ClaimPageDto page = service.mine(1, 10, "HISTORY", claimantUser);

        assertEquals(1, page.content().size());
        assertEquals(520L, page.content().get(0).id());
        assertEquals("https://cdn/thumb.jpg", page.content().get(0).postThumbnailUrl());
        assertNotNull(page.content().get(0).descriptionPreview());

        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(claims.findReceived(eq(10L), eq("ALL"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        assertTrue(service.received(1, 10, "ALL", finderUser).content().isEmpty());
    }

    // Supplementary (ClaimService.grouped): returns post-grouped claim summaries with matching posts
    @Test
    @SuppressWarnings("unchecked")
    void grouped_whenMatchingPostsExist_returnsGroupedClaims() {
        Claim c = Claim.submit(p2pPost, claimant, "Wallet description", null);
        ReflectionTestUtils.setField(c, "id", 530L);
        ReflectionTestUtils.setField(c, "createdAt", OffsetDateTime.now());

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        TypedQuery<Long> idsQuery = mock(TypedQuery.class);
        TypedQuery<Claim> claimsQuery = mock(TypedQuery.class);
        TypedQuery<PostImage> thumbQuery = mock(TypedQuery.class);

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

        when(entityManager.createQuery(anyString(), eq(PostImage.class))).thenReturn(thumbQuery);
        when(thumbQuery.setParameter(anyString(), any())).thenReturn(thumbQuery);
        when(thumbQuery.getResultList()).thenReturn(List.of());

        ClaimPostGroupDto.PageData grouped = service.grouped(1, 10, "HISTORY", true, null, claimantUser);

        assertEquals(1, grouped.content().size());
        assertEquals(100L, grouped.content().get(0).postId());
        assertEquals(1, grouped.content().get(0).claims().size());
    }

    // UTC036, UTC037, UTC039 - office and onPost enforce APPROVER role and post ownership
    @Test
    void office_and_onPost_enforceRoleAndOwnership() {
        // UTC036 - USER calling office throws FORBIDDEN
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> service.office(1, 10, "ALL", claimantUser)).getErrorCode());

        // UTC037 - APPROVER calling office queries findOffice
        when(accounts.findById(30L)).thenReturn(Optional.of(staff));
        when(claims.findOffice(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        assertTrue(service.office(1, 10, "PENDING", staffUser).content().isEmpty());

        // UTC039 - post owner calling onPost queries findOnPost(approver=false)
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findOnPost(eq(100L), eq(10L), eq(false), eq("ALL"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        assertTrue(service.onPost(100L, 1, 10, "ALL", finderUser).content().isEmpty());
    }

    // UTC040, UTC042 - detail and detailOnPost return details to participant and block third parties
    @Test
    void detail_and_detailOnPost_allowParticipantsAndBlockOutsiders() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", "ID card");
        ReflectionTestUtils.setField(c, "id", 540L);
        c.addEvidence(new ClaimEvidence("claims/540/1.jpg", "1.jpg", "image/jpeg", 5L, "h", 0));

        // UTC040 - claimant views own PENDING claim detail and detailOnPost with evidence URL and canCancel=true
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findById(540L)).thenReturn(Optional.of(c));
        when(routing.reviewer(c, claimant)).thenReturn(false);
        when(routing.matches(eq(c), any())).thenReturn(true);
        when(activities.findByClaim_IdOrderByCreatedAtAscIdAsc(540L)).thenReturn(List.of());
        when(storage.readUrl("claims/540/1.jpg", null)).thenReturn("https://cdn/1.jpg");
        when(chatConversations.canOpen(c)).thenReturn(false);

        ClaimDetailDto dto = service.detail(540L, claimantUser);
        assertEquals(540L, dto.id());
        assertTrue(dto.canCancel());
        assertFalse(dto.canReview());

        assertEquals(540L, service.detailOnPost(100L, 540L, claimantUser).id());

        // UTC042 - third party throws CLAIM_NOT_FOUND
        when(accounts.findById(40L)).thenReturn(Optional.of(outsider));
        when(routing.reviewer(c, outsider)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_NOT_FOUND,
                assertThrows(AppException.class, () -> service.detail(540L, outsiderUser)).getErrorCode());
    }

    // UTC047 - review APPROVED reserves post, creates chat conversation, saves APPROVE activity, and notifies claimant
    @Test
    void review_whenApproved_approvesClaimOpensChatAndNotifies() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", null);
        ReflectionTestUtils.setField(c, "id", 550L);
        ReflectionTestUtils.setField(c, "version", 0L);

        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(claims.findPostId(550L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findForUpdate(550L)).thenReturn(Optional.of(c));
        when(routing.reviewer(c, finder)).thenReturn(true);
        when(routing.matches(c, Set.of("OPEN"))).thenReturn(true);

        ClaimResultDto result = service.review(550L, new ReviewClaimRequest("APPROVED", "Matched details", 0L),
                finderUser);

        assertEquals("APPROVED", result.status());
        verify(chatConversations).forApprovedClaim(c);
        verify(activities).save(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(20L), anyString(), anyString(), anyString(), anyString(), eq(true));
    }

    // UTC049 - review on OFFICE claim (DEF-CLAIM-001: lockClaim runs before office branch and throws CLAIM_NOT_FOUND)
    @Test
    void review_whenOfficeClaimPassedToP2pEndpoint_throwsClaimNotFoundInLockClaim() {
        Claim officeClaim = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(officeClaim, "id", 551L);
        ReflectionTestUtils.setField(officeClaim, "claimType", "OFFICE");
        ReflectionTestUtils.setField(officeClaim, "claimant", claimant);
        ReflectionTestUtils.setField(officeClaim, "post", p2pPost);

        when(accounts.findById(30L)).thenReturn(Optional.of(staff));
        when(claims.findPostId(551L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findForUpdate(551L)).thenReturn(Optional.of(officeClaim));

        AppException ex = assertThrows(AppException.class,
                () -> service.review(551L, new ReviewClaimRequest("APPROVED", "Reason", 0L), staffUser));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(chatConversations);
    }

    // UTC056 - review throws CLAIM_STALE when expectedVersion mismatches claim.version
    @Test
    void review_whenVersionMismatch_throwsClaimStale() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", null);
        ReflectionTestUtils.setField(c, "id", 552L);
        ReflectionTestUtils.setField(c, "version", 2L);

        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(claims.findPostId(552L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findForUpdate(552L)).thenReturn(Optional.of(c));
        when(routing.reviewer(c, finder)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.review(552L, new ReviewClaimRequest("REJECTED", "Mismatch", 0L), finderUser));
        assertEquals(ErrorCode.CLAIM_STALE, ex.getErrorCode());
    }

    // UTC061 - cancel PENDING claim by claimant updates status, notifies chat and owner
    @Test
    void cancel_whenPendingByClaimant_cancelsClaimAndNotifiesFinder() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", null);
        ReflectionTestUtils.setField(c, "id", 560L);
        ReflectionTestUtils.setField(c, "version", 1L);

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findPostId(560L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findForUpdate(560L)).thenReturn(Optional.of(c));
        when(routing.matches(c, Set.of("OPEN", "RESERVED"))).thenReturn(true);

        ClaimResultDto result = service.cancel(560L, new CancelClaimRequest(1L, "Found mine"), claimantUser);

        assertEquals("CANCELLED", result.status());
        verify(chatConversations).notifyChanged(c);
        verify(activities).save(any(ClaimActivity.class));
        verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(), eq(false));
    }

    // UTC063 - cancel throws CLAIM_STATE_INVALID when claim is APPROVED and handover already exists
    @Test
    void cancel_whenApprovedWithHandoverRecorded_throwsClaimStateInvalid() {
        Claim c = Claim.submit(p2pPost, claimant, "My wallet", null);
        ReflectionTestUtils.setField(c, "id", 561L);
        ReflectionTestUtils.setField(c, "version", 1L);
        c.review("APPROVED");

        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findPostId(561L)).thenReturn(Optional.of(100L));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.findForUpdate(561L)).thenReturn(Optional.of(c));
        when(handovers.existsByClaimId(561L)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> service.cancel(561L, new CancelClaimRequest(1L, "No longer needed"), claimantUser));
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, ex.getErrorCode());
    }

    // UTC002, UTC003, UTC005, UTC006 - submit rejects null request, blank description, >5000 char description/evidenceNote
    @Test
    void submit_whenInvalidPayload_throwsClaimInputInvalid() {
        // UTC002 - request=null throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.submit(100L, null, List.of(), claimantUser)).getErrorCode());
        // UTC003 - blank description throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.submit(100L, new SubmitClaimRequest("   ", null, "v1", List.of()), List.of(),
                        claimantUser)).getErrorCode());
        // UTC005 - description length 5001 exceeds max 5000
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.submit(100L, new SubmitClaimRequest("a".repeat(5001), null, "v1", List.of()), List.of(),
                        claimantUser)).getErrorCode());
        // UTC006 - evidenceNote length 5001 exceeds max 5000
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.submit(100L, new SubmitClaimRequest("valid", "b".repeat(5001), "v1", List.of()),
                        List.of(), claimantUser)).getErrorCode());
    }

    // UTC004 - submit accepts boundary 5000-character description
    @Test
    void submit_whenDescriptionAtMaxLength5000_succeeds() {
        stubSubmittablePost(p2pPost, claimant);
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(validator.validate(List.of())).thenReturn(List.of());
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 502L);
            return c;
        });

        ClaimResultDto result = service.submit(
                100L, new SubmitClaimRequest("a".repeat(5000), null, "v1", List.of()), List.of(), claimantUser);

        assertEquals(502L, result.id());
        assertEquals("PENDING", result.status());
    }

    // UTC008, UTC009, UTC011, UTC012, UTC013, UTC014 - submit validates actor state, post existence, createdBy self-claim, and FOUND post type
    @Test
    void submit_whenActorPostOrPostTypeInvalid_throwsExpectedError() {
        // UTC013 - user=null throws UNAUTHORIZED
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(AppException.class,
                () -> service.submit(100L, validSubmitReq(), List.of(), null)).getErrorCode());

        // UTC011 - postId=0 throws CLAIM_INPUT_INVALID
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.submit(0L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        // UTC012 - non-existent post 999 throws CLAIM_NOT_FOUND
        when(posts.findById(999L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.submit(999L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        // UTC008 - post createdBy=claimant throws CLAIM_SELF_NOT_ALLOWED
        ReflectionTestUtils.setField(p2pPost, "createdBy", claimant);
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED, assertThrows(AppException.class,
                () -> service.submit(100L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());
        ReflectionTestUtils.setField(p2pPost, "createdBy", finder);

        // UTC009 - LOST post throws CLAIM_POST_UNAVAILABLE
        Post lostPost = Post.lost(finder, p2pPost.getCategory(), "Lost", "Desc", "Hall A",
                OffsetDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(lostPost, "id", 101L);
        when(posts.findById(101L)).thenReturn(Optional.of(lostPost));
        when(routing.canSubmit(lostPost)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_POST_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.submit(101L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        // UTC014 - claimant SUSPENDED after lock refresh throws FORBIDDEN
        doAnswer(inv -> {
            ReflectionTestUtils.setField(claimant, "status", AccountStatus.SUSPENDED);
            return null;
        }).when(entityManager).refresh(claimant);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(AppException.class,
                () -> service.submit(100L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());
    }

    // UTC015, UTC016 - submit maps DataIntegrityViolationException to CLAIM_DUPLICATE and cleans up uploads on failure
    @Test
    void submit_whenDataIntegrityViolationOrStoragePutFails_deletesUploadedKeys() {
        MockMultipartFile file = new MockMultipartFile("files", "proof.jpg", "image/jpeg", new byte[] { 1, 2 });
        ValidatedImage img = new ValidatedImage(new byte[] { 1, 2 }, "image/jpeg", "jpg", 10, 10, "h1");

        // UTC015 - unique constraint violation translates to CLAIM_DUPLICATE and deletes uploaded evidence
        stubSubmittablePost(p2pPost, claimant);
        when(validator.validate(List.of(file))).thenReturn(List.of(img));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.saveAndFlush(any(Claim.class))).thenThrow(new DataIntegrityViolationException("uq_active"));

        AppException dupEx = assertThrows(AppException.class,
                () -> service.submit(100L, validSubmitReq(), List.of(file), claimantUser));
        assertEquals(ErrorCode.CLAIM_DUPLICATE, dupEx.getErrorCode());
        verify(storage).delete(anyString());

        // UTC016 - storage.put failure cleans up key and rethrows RuntimeException
        doThrow(new RuntimeException("S3 put failed")).when(storage).put(anyString(), any(), anyString());
        assertThrows(RuntimeException.class,
                () -> service.submit(100L, validSubmitReq(), List.of(file), claimantUser));
        verify(storage, times(2)).delete(anyString());
    }

    // UTC017, UTC018 - submit strips Windows path from filename and uses "Thành viên" when claimant fullName is blank
    @Test
    void submit_whenWindowsPathFilenameAndBlankFullName_sanitizesFilenameAndUsesFallbackSenderName() {
        ReflectionTestUtils.setField(claimant, "fullName", "   ");
        // UTC017 - strip Windows folder path from uploaded evidence filename
        MockMultipartFile file = new MockMultipartFile("files", "C:\\Users\\a\\pic.jpg", "image/jpeg",
                new byte[] { 7, 8 });
        ValidatedImage img = new ValidatedImage(new byte[] { 7, 8 }, "image/jpeg", "jpg", 10, 10, "h2");

        stubSubmittablePost(p2pPost, claimant);
        when(validator.validate(List.of(file))).thenReturn(List.of(img));
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 503L);
            return c;
        });

        service.submit(100L, validSubmitReq(), List.of(file), claimantUser);

        ArgumentCaptor<Claim> claimCaptor = ArgumentCaptor.forClass(Claim.class);
        verify(claims).saveAndFlush(claimCaptor.capture());
        assertEquals("pic.jpg", claimCaptor.getValue().getEvidence().get(0).getOriginalName());

        // UTC018 - blank claimant fullName falls back to "Thành viên" in notification body
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyUser(eq(10L), eq("CLAIM_SUBMITTED"), anyString(), bodyCaptor.capture(), anyString(),
                eq(true));
        assertTrue(bodyCaptor.getValue().startsWith("Thành viên "));
    }

    // UTC020, UTC022, UTC023, UTC024 - retry allows CANCELLED claims and rejects foreign, OFFICE, or custody-changed claims
    @Test
    void retry_whenCancelledOrInvalidPreviousOrChangedCustody_handlesAllBranches() {
        // UTC020 - CANCELLED previous claim can be retried
        Claim cancelled = p2pClaim(480L, "CANCELLED", 1L);
        when(claims.findById(480L)).thenReturn(Optional.of(cancelled));
        stubSubmittablePost(p2pPost, claimant);
        when(validator.validate(List.of())).thenReturn(List.of());
        when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(p2pPost));
        when(claims.saveAndFlush(any(Claim.class))).thenAnswer(inv -> {
            Claim c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 481L);
            return c;
        });

        ClaimResultDto retried = service.retry(480L, validSubmitReq(), List.of(), claimantUser);
        assertEquals(481L, retried.id());

        // UTC022 - previous claim owned by another user throws CLAIM_NOT_FOUND
        Claim othersClaim = Claim.submit(p2pPost, outsider, "Other", null);
        ReflectionTestUtils.setField(othersClaim, "id", 482L);
        ReflectionTestUtils.setField(othersClaim, "status", "REJECTED");
        when(claims.findById(482L)).thenReturn(Optional.of(othersClaim));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.retry(482L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        // UTC023 - OFFICE previous claim throws CLAIM_NOT_FOUND
        Claim officeClaim = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(officeClaim, "id", 483L);
        ReflectionTestUtils.setField(officeClaim, "post", p2pPost);
        ReflectionTestUtils.setField(officeClaim, "claimant", claimant);
        ReflectionTestUtils.setField(officeClaim, "claimType", "OFFICE");
        ReflectionTestUtils.setField(officeClaim, "status", "REJECTED");
        when(claims.findById(483L)).thenReturn(Optional.of(officeClaim));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.retry(483L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        // UTC024 - post custodyMode changed to OFFICE rejects retry with CLAIM_POST_UNAVAILABLE / CLAIM_STATE_INVALID
        Claim rejected = p2pClaim(484L, "REJECTED", 1L);
        ReflectionTestUtils.setField(p2pPost, "custodyMode", "OFFICE");
        when(claims.findById(484L)).thenReturn(Optional.of(rejected));
        when(routing.canSubmit(p2pPost)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_POST_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.retry(484L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());

        when(routing.canSubmit(p2pPost)).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, assertThrows(AppException.class,
                () -> service.retry(484L, validSubmitReq(), List.of(), claimantUser)).getErrorCode());
    }

    // UTC026, UTC028, UTC029 - questionnaire rejects post owner and latestOnPost handles absent claims and postId<=0
    @Test
    void questionnaire_and_latestOnPost_coverOwnerNoneAndInvalidPostId() {
        // UTC026 - post owner requesting questionnaire throws CLAIM_SELF_NOT_ALLOWED
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        assertEquals(ErrorCode.CLAIM_SELF_NOT_ALLOWED, assertThrows(AppException.class,
                () -> service.questionnaire(100L, finderUser)).getErrorCode());

        // UTC028 - no prior claim returns null from latestOnPost
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(100L, 20L, "P2P"))
                .thenReturn(Optional.empty());
        assertEquals(null, service.latestOnPost(100L, claimantUser));

        // UTC029 - postId=0 throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.latestOnPost(0L, claimantUser)).getErrorCode());
    }

    // UTC031, UTC032, UTC033, UTC034, UTC035, UTC038 - listing endpoints enforce status, pagination, and post ownership
    @Test
    void mine_received_and_onPost_enforceValidationPaginationAndAccess() {
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        // UTC031 - mine(status="UNKNOWN") throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.mine(1, 10, "UNKNOWN", claimantUser)).getErrorCode());
        // UTC032 - mine(status=null) throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.mine(1, 10, null, claimantUser)).getErrorCode());
        // UTC033 - mine(page=0) throws POST_PAGINATION_INVALID
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, assertThrows(AppException.class,
                () -> service.mine(0, 10, "ALL", claimantUser)).getErrorCode());
        // UTC034 - mine(size=101) throws POST_PAGINATION_INVALID
        assertEquals(ErrorCode.POST_PAGINATION_INVALID, assertThrows(AppException.class,
                () -> service.mine(1, 101, "ALL", claimantUser)).getErrorCode());

        // UTC035 - received returns matching PENDING claims for post owner
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        Claim c1 = p2pClaim(521L, "PENDING", 0L);
        Claim c2 = p2pClaim(522L, "PENDING", 0L);
        TypedQuery<PostImage> thumbQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(PostImage.class))).thenReturn(thumbQuery);
        when(thumbQuery.setParameter(eq("postIds"), anyList())).thenReturn(thumbQuery);
        when(thumbQuery.getResultList()).thenReturn(List.of());
        when(claims.findReceived(10L, "PENDING", PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 10), 2));

        ClaimPageDto receivedPage = service.received(1, 10, "PENDING", finderUser);
        assertEquals(2, receivedPage.content().size());

        // UTC038 - non-owner non-approver calling onPost throws CLAIM_NOT_FOUND
        when(accounts.findById(40L)).thenReturn(Optional.of(outsider));
        when(posts.findById(100L)).thenReturn(Optional.of(p2pPost));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.onPost(100L, 1, 10, "ALL", outsiderUser)).getErrorCode());
    }

    // UTC041, UTC043, UTC044, UTC045, UTC046 - detail and detailOnPost set canReview/canRetry and block mismatches or OFFICE claims
    @Test
    void detail_and_detailOnPost_handleReviewerRetryabilityMismatchAndOfficeClaim() {
        // UTC041 - post owner viewing PENDING claim sees canReview=true
        Claim pending = p2pClaim(541L, "PENDING", 0L);
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(claims.findById(541L)).thenReturn(Optional.of(pending));
        when(routing.reviewer(pending, finder)).thenReturn(true);
        when(routing.matches(pending, Set.of("OPEN"))).thenReturn(true);
        when(activities.findByClaim_IdOrderByCreatedAtAscIdAsc(541L)).thenReturn(List.of());
        assertTrue(service.detail(541L, finderUser).canReview());

        // UTC043, UTC044 - REJECTED claim sets canRetry=true only when no other active claim exists
        Claim rejected = p2pClaim(542L, "REJECTED", 0L);
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(claims.findById(542L)).thenReturn(Optional.of(rejected));
        when(routing.matches(rejected, Set.of("OPEN"))).thenReturn(true);
        when(activities.findByClaim_IdOrderByCreatedAtAscIdAsc(542L)).thenReturn(List.of());
        when(claims.existsByPost_IdAndClaimant_IdAndStatusIn(eq(100L), eq(20L), any()))
                .thenReturn(false, true);
        // UTC043 - no other active claim -> canRetry=true
        assertTrue(service.detail(542L, claimantUser).canRetry());
        // UTC044 - active claim exists -> canRetry=false
        assertFalse(service.detail(542L, claimantUser).canRetry());

        // UTC045 - detailOnPost with mismatched postId throws CLAIM_NOT_FOUND
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.detailOnPost(101L, 542L, claimantUser)).getErrorCode());

        // UTC046 - OFFICE claim passed to P2P detail throws CLAIM_NOT_FOUND
        Claim office = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(office, "id", 543L);
        ReflectionTestUtils.setField(office, "claimType", "OFFICE");
        ReflectionTestUtils.setField(office, "claimant", claimant);
        ReflectionTestUtils.setField(office, "post", p2pPost);
        when(claims.findById(543L)).thenReturn(Optional.of(office));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.detail(543L, claimantUser)).getErrorCode());
    }

    // UTC048 - review REJECTED updates status, saves REJECT activity, and notifies claimant with reason
    @Test
    void review_whenRejected_updatesStatusSavesActivityAndNotifiesWithReason() {
        Claim c = p2pClaim(553L, "PENDING", 0L);
        stubLockForAction(553L, c, p2pPost);
        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(routing.reviewer(c, finder)).thenReturn(true);
        when(routing.matches(c, Set.of("OPEN"))).thenReturn(true);

        ClaimResultDto result = service.review(
                553L, new ReviewClaimRequest("REJECTED", "Wrong colour", 0L), finderUser);

        assertEquals("REJECTED", result.status());
        verify(activities).save(any(ClaimActivity.class));
        verify(chatConversations, never()).forApprovedClaim(any());
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notifications).notifyUser(eq(20L), eq("CLAIM_REJECTED"), anyString(), msgCaptor.capture(), anyString(),
                eq(true));
        assertTrue(msgCaptor.getValue().contains("Wrong colour"));
    }

    // UTC050, UTC051, UTC052, UTC053, UTC054, UTC055, UTC057, UTC058, UTC059, UTC060 - review validates authorization, inputs, and conflicts
    @Test
    void review_whenUnauthorizedOrInvalidInputOrStateConflict_throwsExpectedErrors() {
        Claim pending = p2pClaim(554L, "PENDING", 0L);
        stubLockForAction(554L, pending, p2pPost);

        // UTC050 - actor not post owner throws CLAIM_NOT_FOUND
        when(accounts.findById(40L)).thenReturn(Optional.of(outsider));
        when(routing.reviewer(pending, outsider)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "ok", 0L), outsiderUser))
                .getErrorCode());

        // UTC051 - claimant reviewing own claim throws CLAIM_NOT_FOUND
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(routing.reviewer(pending, claimant)).thenReturn(false);
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "ok", 0L), claimantUser))
                .getErrorCode());

        when(accounts.findById(10L)).thenReturn(Optional.of(finder));
        when(routing.reviewer(pending, finder)).thenReturn(true);
        // UTC052 - unsupported decision "MAYBE" throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("MAYBE", "Reason", 0L), finderUser))
                .getErrorCode());
        // UTC053 - empty reason throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "", 0L), finderUser))
                .getErrorCode());
        // UTC054 - reason > 2000 chars throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "x".repeat(2001), 0L), finderUser))
                .getErrorCode());
        // UTC057 - expectedVersion=null throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", null), finderUser))
                .getErrorCode());
        // UTC058 - expectedVersion=-1 throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", -1L), finderUser))
                .getErrorCode());

        // UTC060 - another claim on post already APPROVED throws CLAIM_STATE_INVALID
        when(routing.matches(pending, Set.of("OPEN"))).thenReturn(true);
        when(claims.existsByPost_IdAndStatusIn(100L, Set.of("APPROVED"))).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, assertThrows(AppException.class,
                () -> service.review(554L, new ReviewClaimRequest("APPROVED", "Reason", 0L), finderUser))
                .getErrorCode());

        // UTC059 - reviewing already APPROVED claim throws CLAIM_STATE_INVALID
        Claim alreadyApproved = p2pClaim(555L, "APPROVED", 0L);
        stubLockForAction(555L, alreadyApproved, p2pPost);
        when(routing.reviewer(alreadyApproved, finder)).thenReturn(true);
        when(routing.matches(alreadyApproved, Set.of("OPEN"))).thenReturn(true);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, assertThrows(AppException.class,
                () -> service.review(555L, new ReviewClaimRequest("REJECTED", "Late", 0L), finderUser))
                .getErrorCode());

        // UTC055 - boundary 2000-character reason accepted
        Claim maxReasonClaim = p2pClaim(556L, "PENDING", 0L);
        stubLockForAction(556L, maxReasonClaim, p2pPost);
        when(routing.reviewer(maxReasonClaim, finder)).thenReturn(true);
        when(routing.matches(maxReasonClaim, Set.of("OPEN"))).thenReturn(true);
        when(claims.existsByPost_IdAndStatusIn(100L, Set.of("APPROVED"))).thenReturn(false);
        ClaimResultDto approvedMaxReason = service.review(
                556L, new ReviewClaimRequest("APPROVED", "r".repeat(2000), 0L), finderUser);
        assertEquals("APPROVED", approvedMaxReason.status());
    }

    // UTC062, UTC064, UTC065, UTC066, UTC067, UTC068 - cancel reopens post for APPROVED claim and validates actor/inputs/state
    @Test
    void cancel_whenApprovedReopensPost_andHandlesNullReasonOrInvalidStates() {
        // UTC062, UTC067 - cancelling APPROVED claim with optional null reason reopens post to OPEN
        p2pPost.reserveForClaim();
        Claim approved = p2pClaim(562L, "APPROVED", 1L);
        stubLockForAction(562L, approved, p2pPost);
        when(accounts.findById(20L)).thenReturn(Optional.of(claimant));
        when(routing.matches(approved, Set.of("OPEN", "RESERVED"))).thenReturn(true);
        when(claims.existsByPost_IdAndStatusIn(100L, Set.of("APPROVED"))).thenReturn(false);

        ClaimResultDto cancelledApproved = service.cancel(562L, new CancelClaimRequest(1L, null), claimantUser);
        assertEquals("CANCELLED", cancelledApproved.status());
        assertEquals("OPEN", p2pPost.getStatus());

        // UTC064 - cancelling REJECTED claim throws CLAIM_STATE_INVALID
        Claim rejected = p2pClaim(563L, "REJECTED", 1L);
        stubLockForAction(563L, rejected, p2pPost);
        assertEquals(ErrorCode.CLAIM_STATE_INVALID, assertThrows(AppException.class,
                () -> service.cancel(563L, new CancelClaimRequest(1L, "No longer needed"), claimantUser))
                .getErrorCode());

        // UTC066 - request=null throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.cancel(563L, null, claimantUser)).getErrorCode());
        // UTC068 - reason > 2000 chars throws CLAIM_INPUT_INVALID
        assertEquals(ErrorCode.CLAIM_INPUT_INVALID, assertThrows(AppException.class,
                () -> service.cancel(563L, new CancelClaimRequest(1L, "z".repeat(2001)), claimantUser))
                .getErrorCode());

        // UTC065 - non-claimant cancelling throws CLAIM_NOT_FOUND
        when(accounts.findById(40L)).thenReturn(Optional.of(outsider));
        assertEquals(ErrorCode.CLAIM_NOT_FOUND, assertThrows(AppException.class,
                () -> service.cancel(563L, new CancelClaimRequest(1L, "No"), outsiderUser)).getErrorCode());
    }

    private static SubmitClaimRequest validSubmitReq() {
        return new SubmitClaimRequest("Brown leather wallet", null, "v1", List.of());
    }

    private void stubSubmittablePost(Post targetPost, Account actor) {
        when(accounts.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(posts.findById(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(routing.canSubmit(targetPost)).thenReturn(true);
    }

    private void stubLockForAction(long claimId, Claim claim, Post targetPost) {
        when(claims.findPostId(claimId)).thenReturn(Optional.of(targetPost.getId()));
        when(posts.findForClaimUpdate(targetPost.getId())).thenReturn(Optional.of(targetPost));
        when(claims.findForUpdate(claimId)).thenReturn(Optional.of(claim));
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

    private static Account account(Long id, String name, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setEmail(name.toLowerCase() + "@fpt.edu.vn");
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
