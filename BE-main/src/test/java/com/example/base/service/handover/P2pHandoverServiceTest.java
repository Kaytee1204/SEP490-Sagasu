package com.example.base.service.handover;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.Claim;
import com.example.base.entity.ClaimActivity;
import com.example.base.entity.P2pHandover;
import com.example.base.entity.Post;
import com.example.base.entity.Report;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ClaimActivityRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.P2pHandoverRepository;
import com.example.base.repository.PostRepository;
import com.example.base.repository.ReportRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.claim.ClaimRoutingPolicy;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.ReportService;
import com.example.base.service.report.WorkflowImages;
import com.example.base.storage.ImageValidator.ValidatedImage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class P2pHandoverServiceTest {

        @Mock
        private P2pHandoverRepository handovers;
        @Mock
        private ClaimRepository claims;
        @Mock
        private PostRepository posts;
        @Mock
        private ClaimActivityRepository activities;
        @Mock
        private ChatAccessPolicy actors;
        @Mock
        private ClaimRoutingPolicy routing;
        @Mock
        private ChatConversationService conversations;
        @Mock
        private WorkflowImages images;
        @Mock
        private ReportService reports;
        @Mock
        private ReportRepository reportRepository;
        @Mock
        private EntityManager em;
        @Mock
        private JdbcTemplate jdbc;
        @Mock
        private PlatformTransactionManager transactions;
        @Mock
        private NotificationService notifications;

        @InjectMocks
        private P2pHandoverService service;

        private Account finder;
        private Account claimant;
        private Account outsider;
        private CustomUserDetails finderUser;
        private CustomUserDetails claimantUser;
        private CustomUserDetails outsiderUser;
        private Post post;
        private Claim approvedClaim;

        @BeforeEach
        void setUp() {
                finder = account(10L, "Finder");
                claimant = account(20L, "Claimant");
                outsider = account(30L, "Outsider");
                finderUser = new CustomUserDetails(finder);
                claimantUser = new CustomUserDetails(claimant);
                outsiderUser = new CustomUserDetails(outsider);

                Category cat = new Category();
                ReflectionTestUtils.setField(cat, "categoryId", 1L);
                cat.setName("Electronics");

                post = Post.found(finder, cat, "Found phone", "Black phone", "Hall C",
                                OffsetDateTime.now().minusDays(1), "P2P", null);
                ReflectionTestUtils.setField(post, "id", 100L);

                approvedClaim = BeanUtils.instantiateClass(Claim.class);
                ReflectionTestUtils.setField(approvedClaim, "id", 500L);
                ReflectionTestUtils.setField(approvedClaim, "post", post);
                ReflectionTestUtils.setField(approvedClaim, "claimant", claimant);
                ReflectionTestUtils.setField(approvedClaim, "claimType", "P2P");
                ReflectionTestUtils.setField(approvedClaim, "status", "APPROVED");
                ReflectionTestUtils.setField(approvedClaim, "version", 2L);

                lenient().when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        }

        // Supplementary (P2pHandoverService.detail): propagates UNAUTHORIZED when actor is null
        @Test
        void detail_whenActorUnauthorized_propagatesUnauthorized() {
                when(actors.actor(null)).thenThrow(new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized"));

                AppException ex = assertThrows(AppException.class, () -> service.detail(500L, null));
                assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        }

        // UTC005 - detail: missing claim throws HANDOVER_NOT_FOUND
        @Test
        void detail_whenClaimMissing_throwsHandoverNotFound() {
                when(actors.actor(finderUser)).thenReturn(finder);
                when(claims.findById(500L)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class, () -> service.detail(500L, finderUser));
                assertEquals(ErrorCode.HANDOVER_NOT_FOUND, ex.getErrorCode());
        }

        // UTC003 - detail: outsider throws HANDOVER_NOT_FOUND
        @Test
        void detail_whenOutsider_throwsHandoverNotFound() {
                when(actors.actor(outsiderUser)).thenReturn(outsider);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));

                AppException ex = assertThrows(AppException.class, () -> service.detail(500L, outsiderUser));
                assertEquals(ErrorCode.HANDOVER_NOT_FOUND, ex.getErrorCode());
        }

        // UTC001 - detail: post owner with no recorded handover gets NOT_RECORDED & canRecord=true
        @Test
        void detail_whenNoHandoverRecorded_returnsFinderCanRecordOnly() {
                when(actors.actor(finderUser)).thenReturn(finder);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(handovers.findByClaimId(500L)).thenReturn(Optional.empty());

                P2pHandoverService.Detail detail = service.detail(500L, finderUser);

                assertNull(detail.id());
                assertEquals(500L, detail.claimId());
                assertTrue(detail.canRecord());
                assertFalse(detail.canConfirm());
                assertFalse(detail.canReportIssue());
                assertTrue(detail.evidence().isEmpty());
                assertNull(detail.issue());
        }

        // UTC002 - detail: claimant viewing WAITING_CONFIRMATION gets canConfirm=true, canReportIssue=true & evidence
        @Test
        void detail_whenWaitingConfirmationViewedByClaimant_allowsConfirmAndIssue() {
                P2pHandover handover = new P2pHandover(500L, 10L, "Met at canteen", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 700L);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 1L);

                ReportService.Evidence ev = new ReportService.Evidence(1L, "https://cdn/ev1.jpg", "ev1.jpg");

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(700L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(700L)))
                                .thenReturn(List.of(ev));

                P2pHandoverService.Detail detail = service.detail(500L, claimantUser);

                assertEquals(700L, detail.id());
                assertEquals("WAITING_CONFIRMATION", detail.status());
                assertFalse(detail.canRecord());
                assertTrue(detail.canConfirm());
                assertTrue(detail.canReportIssue());
                assertEquals(1, detail.evidence().size());
                assertNull(detail.issue());
        }

        // Supplementary (P2pHandoverService.detail): DISPUTED handover includes linked issue detail & targetLabel
        @Test
        void detail_whenDisputedWithReport_includesIssueDetailWithTargetLabel() {
                P2pHandover handover = new P2pHandover(500L, 10L, "Met at canteen", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 700L);
                ReflectionTestUtils.setField(handover, "status", "DISPUTED");
                ReflectionTestUtils.setField(handover, "version", 3L);

                ReportService.Evidence ev = new ReportService.Evidence(1L, "https://cdn/ev1.jpg", "ev1.jpg");
                Report report = new Report(20L, "HANDOVER_ISSUE", "CLAIM", 500L,
                                "Wrong charger returned", UUID.randomUUID(), "rhash");
                ReflectionTestUtils.setField(report, "id", 900L);
                ReportService.Detail issueDetail = new ReportService.Detail(
                                900L, "HANDOVER_ISSUE", "CLAIM", 500L,
                                "Wrong charger returned", "OPEN", null, OffsetDateTime.now(),
                                List.of(ev), List.of(), "Bàn giao tài sản P2P");

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(700L)))
                                .thenReturn(List.of(900L));
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(700L)))
                                .thenReturn(List.of(ev));
                when(reportRepository.findById(900L)).thenReturn(Optional.of(report));
                when(reports.detail(report)).thenReturn(issueDetail);

                P2pHandoverService.Detail result = service.detail(500L, claimantUser);

                assertEquals(700L, result.id());
                assertEquals("DISPUTED", result.status());
                assertEquals(1, result.evidence().size());
                assertNotNull(result.issue());
                assertEquals("Bàn giao tài sản P2P", result.issue().targetLabel());
                assertFalse(result.canConfirm());
                assertFalse(result.canReportIssue());
        }

        // UTC006 - record: valid finder request saves handover/evidence, activity & notifies claimant
        @Test
        void record_whenValidFinderRequest_storesHandoverEvidenceActivityAndNotifiesClaimant() {
                UUID reqId = UUID.randomUUID();
                List<MultipartFile> files = List.of(
                                new MockMultipartFile("files", "handover.jpg", "image/jpeg", new byte[] { 1, 2, 3 }));
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(
                                List.of(new ValidatedImage(new byte[] { 1, 2, 3 }, "image/jpeg", "jpg", 10, 10,
                                                "img-h")),
                                List.of("handover.jpg"), "img-hash");
                WorkflowImages.Image stored = new WorkflowImages.Image(
                                "handovers/500/1.jpg", "handover.jpg", "image/jpeg", 3L, "img-h", 0);
                ReportService.Evidence ev = new ReportService.Evidence(1L, "https://cdn/1.jpg", "handover.jpg");

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Met at Hall C", files)).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByRecordedByAndClientRequestId(10L, reqId)).thenReturn(Optional.empty());
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(images.upload("handovers/500", prepared)).thenReturn(List.of(stored));
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(em.find(Account.class, 10L)).thenReturn(finder);
                when(handovers.existsByClaimId(500L)).thenReturn(false);
                when(handovers.saveAndFlush(any(P2pHandover.class))).thenAnswer(inv -> {
                        P2pHandover saved = inv.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 701L);
                        return saved;
                });
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(701L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(701L)))
                                .thenReturn(List.of(ev));

                P2pHandoverService.Detail result = service.record(
                                500L, new P2pHandoverService.RecordRequest("Met at Hall C", 2L, reqId), files,
                                finderUser);

                assertEquals(701L, result.id());
                assertEquals("WAITING_CONFIRMATION", result.status());
                assertEquals(1, result.evidence().size());
                verify(activities).save(any(ClaimActivity.class));
                verify(conversations).notifyChanged(approvedClaim);
                verify(notifications).notifyUser(eq(20L), anyString(), anyString(), anyString(), anyString(), eq(true));
                verify(jdbc).update(anyString(), eq(701L), eq("handovers/500/1.jpg"),
                                eq("handover.jpg"), eq("image/jpeg"), eq(3L), eq("img-h"), eq(0));
        }

        // UTC013 - record: claim version mismatch after upload cleans up images & throws HANDOVER_STATE_CHANGED
        @Test
        void record_whenClaimVersionChangesDuringUpload_cleansUpImagesAndThrowsStateChanged() {
                UUID reqId = UUID.randomUUID();
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                WorkflowImages.Image stored = new WorkflowImages.Image(
                                "handovers/500/1.jpg", "a.jpg", "image/jpeg", 3L, "h", 0);

                Claim changedClaim = BeanUtils.instantiateClass(Claim.class);
                ReflectionTestUtils.setField(changedClaim, "id", 500L);
                ReflectionTestUtils.setField(changedClaim, "post", post);
                ReflectionTestUtils.setField(changedClaim, "claimant", claimant);
                ReflectionTestUtils.setField(changedClaim, "claimType", "P2P");
                ReflectionTestUtils.setField(changedClaim, "status", "APPROVED");
                ReflectionTestUtils.setField(changedClaim, "version", 99L);

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Note", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByRecordedByAndClientRequestId(10L, reqId)).thenReturn(Optional.empty());
                when(routing.matches(any(Claim.class), eq(Set.of("RESERVED")))).thenReturn(true);
                when(handovers.existsByClaimId(500L)).thenReturn(false);
                when(images.upload("handovers/500", prepared)).thenReturn(List.of(stored));
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(changedClaim));

                AppException ex = assertThrows(AppException.class, () -> service.record(
                                500L, new P2pHandoverService.RecordRequest("Note", 2L, reqId), List.of(), finderUser));

                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
                verify(images).cleanup(List.of(stored));
                verify(handovers, never()).saveAndFlush(any());
        }

        // UTC016 - record: same clientRequestId with different payload hash throws WORKFLOW_DUPLICATE_PAYLOAD
        @Test
        void record_whenDuplicateClientRequestIdWithMismatchHash_throwsWorkflowDuplicatePayload() {
                UUID reqId = UUID.randomUUID();
                P2pHandover existing = new P2pHandover(500L, 10L, "Note", reqId, "different-hash");
                ReflectionTestUtils.setField(existing, "id", 701L);
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Other note", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByRecordedByAndClientRequestId(10L, reqId)).thenReturn(Optional.of(existing));

                AppException ex = assertThrows(AppException.class, () -> service.record(
                                500L, new P2pHandoverService.RecordRequest("Other note", 2L, reqId), List.of(),
                                finderUser));

                assertEquals(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD, ex.getErrorCode());
                verify(images, never()).upload(anyString(), any());
        }

        // UTC015 - record: same clientRequestId with matching payload hash returns existing detail without upload
        @Test
        void record_whenDuplicateClientRequestIdWithMatchingHash_returnsExistingWithoutUpload() {
                UUID reqId = UUID.randomUUID();
                P2pHandover existing = new P2pHandover(500L, 10L, "Met here", reqId, "img-hash");
                ReflectionTestUtils.setField(existing, "id", 701L);
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Met here", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByRecordedByAndClientRequestId(10L, reqId)).thenReturn(Optional.of(existing));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(701L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(701L)))
                                .thenReturn(List.of());

                P2pHandoverService.Detail result = service.record(
                                500L, new P2pHandoverService.RecordRequest("Met here", 2L, reqId), List.of(),
                                finderUser);

                assertEquals(701L, result.id());
                verify(images, never()).upload(anyString(), any());
                verify(handovers, never()).saveAndFlush(any());
        }

        // UTC018 - confirm: valid claimant request marks handover/claim COMPLETED, post RETURNED & notifies owner
        @Test
        void confirm_whenValidClaimantRequest_marksCompletedAndPostReturned() {
                ReflectionTestUtils.setField(post, "status", "RESERVED");
                P2pHandover handover = new P2pHandover(500L, 10L, "Note", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 702L);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 1L);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(em.find(Account.class, 20L)).thenReturn(claimant);
                when(claims.findWaitingForReturn(100L)).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(702L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(702L)))
                                .thenReturn(List.of());

                P2pHandoverService.Detail result = service.confirm(
                                500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser);

                assertEquals("COMPLETED", result.status());
                assertEquals("COMPLETED", approvedClaim.getStatus());
                assertEquals("RETURNED", post.getStatus());
                verify(em).refresh(handover, LockModeType.PESSIMISTIC_WRITE);
                verify(activities).save(any(ClaimActivity.class));
                verify(conversations).notifyChanged(approvedClaim);
                verify(notifications).notifyUser(eq(10L), anyString(), anyString(), anyString(), anyString(), eq(true));
        }

        // UTC021 - confirm: non-claimant actor throws FORBIDDEN
        @Test
        void confirm_whenNonClaimantCalls_throwsForbidden() {
                when(actors.actor(finderUser)).thenReturn(finder);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));

                AppException ex = assertThrows(AppException.class, () -> service.confirm(
                                500L, new P2pHandoverService.ConfirmRequest(1L), finderUser));
                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        // UTC023 - confirm: handover version mismatch throws HANDOVER_STATE_CHANGED
        @Test
        void confirm_whenVersionMismatch_throwsHandoverStateChanged() {
                P2pHandover handover = new P2pHandover(500L, 10L, "Note", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 702L);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 4L);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);

                AppException ex = assertThrows(AppException.class, () -> service.confirm(
                                500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser));
                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
        }

        // UTC025 - issue: valid claimant request inserts HANDOVER_ISSUE report, links issue & marks DISPUTED
        @Test
        void issue_whenValidClaimantRequest_insertsReportLinksIssueAndMarksDisputed() {
                UUID reqId = UUID.randomUUID();
                P2pHandover handover = new P2pHandover(500L, 10L, "Note", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 703L);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 1L);

                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                Report report = new Report(20L, "HANDOVER_ISSUE", "CLAIM", 500L, "Wrong item", reqId, "rhash");
                ReflectionTestUtils.setField(report, "id", 901L);
                ReportService.Detail issueDetail = new ReportService.Detail(
                                901L, "HANDOVER_ISSUE", "CLAIM", 500L, "Wrong item", "OPEN", null,
                                OffsetDateTime.now(), List.of(), List.of(), "Bàn giao tài sản P2P");

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:Wrong item", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(images.upload("handover-reports/500", prepared)).thenReturn(List.of());
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(em.find(Account.class, 20L)).thenReturn(claimant);
                when(reports.insert(eq(20L), eq("HANDOVER_ISSUE"), eq("CLAIM"), eq(500L),
                                eq("Wrong item"), eq(reqId), anyString(), eq(List.of()))).thenReturn(report);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of(), List.of(), List.of(901L));
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of());
                when(reportRepository.findById(901L)).thenReturn(Optional.of(report));
                when(reports.detail(report)).thenReturn(issueDetail);

                P2pHandoverService.Detail result = service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                claimantUser);

                assertEquals("DISPUTED", result.status());
                assertNotNull(result.issue());
                assertEquals("Bàn giao tài sản P2P", result.issue().targetLabel());
                verify(jdbc).update(anyString(), eq(901L), eq(703L));
                verify(activities).save(any(ClaimActivity.class));
                verify(conversations).notifyChanged(approvedClaim);
        }

        // Supplementary (P2pHandoverService.issue): cleans up uploaded images when DB transaction fails
        @Test
        void issue_whenTransactionFailsAfterUpload_cleansUpImages() {
                UUID reqId = UUID.randomUUID();
                P2pHandover handover = new P2pHandover(500L, 10L, "Note", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", 703L);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 1L);

                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                WorkflowImages.Image uploaded = new WorkflowImages.Image(
                                "handover-reports/500/1.jpg", "1.jpg", "image/jpeg", 5L, "h", 0);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:Wrong item", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(images.upload("handover-reports/500", prepared)).thenReturn(List.of(uploaded));
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of());
                doThrow(new RuntimeException("DB fail")).when(reports).insert(
                                anyLong(), anyString(), anyString(), anyLong(), anyString(), eq(reqId), anyString(),
                                any());

                assertThrows(RuntimeException.class, () -> service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                claimantUser));

                verify(images).cleanup(List.of(uploaded));
        }

        // UTC004 - detail: OFFICE claim throws HANDOVER_NOT_FOUND
        @Test
        void detail_whenOfficeClaim_throwsHandoverNotFound() {
                Claim officeClaim = BeanUtils.instantiateClass(Claim.class);
                ReflectionTestUtils.setField(officeClaim, "id", 500L);
                ReflectionTestUtils.setField(officeClaim, "post", post);
                ReflectionTestUtils.setField(officeClaim, "claimant", claimant);
                ReflectionTestUtils.setField(officeClaim, "claimType", "OFFICE");

                when(actors.actor(finderUser)).thenReturn(finder);
                when(claims.findById(500L)).thenReturn(Optional.of(officeClaim));

                AppException ex = assertThrows(AppException.class, () -> service.detail(500L, finderUser));
                assertEquals(ErrorCode.HANDOVER_NOT_FOUND, ex.getErrorCode());
        }

        // UTC007, UTC008, UTC009, UTC010 - record: rejects null request, null clientRequestId, null expectedClaimVersion & 2001-char note
        @Test
        void record_whenInvalidInput_throwsWorkflowInputInvalid() {
                // UTC007 - request=null
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.record(500L, null, List.of(), finderUser)).getErrorCode());
                // UTC008 - clientRequestId=null
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.record(500L, new P2pHandoverService.RecordRequest("Met", 2L, null),
                                                List.of(), finderUser)).getErrorCode());
                // UTC009 - expectedClaimVersion=null
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.record(500L,
                                                new P2pHandoverService.RecordRequest("Met", null, UUID.randomUUID()),
                                                List.of(), finderUser)).getErrorCode());
                // UTC010 - note 2001 chars
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.record(500L,
                                                new P2pHandoverService.RecordRequest("a".repeat(2001), 2L,
                                                                UUID.randomUUID()),
                                                List.of(), finderUser)).getErrorCode());
        }

        // UTC011 - record: whitespace-only note stored as null
        @Test
        void record_whenBlankNote_storesNullNoteAndCreatesHandover() {
                UUID reqId = UUID.randomUUID();
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                P2pHandover saved = new P2pHandover(500L, 10L, null, reqId, "img-hash");
                ReflectionTestUtils.setField(saved, "id", 701L);
                ReflectionTestUtils.setField(saved, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(saved, "version", 1L);

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(handovers.existsByClaimId(500L)).thenReturn(false);
                when(images.upload("handovers/500", prepared)).thenReturn(List.of());
                when(handovers.saveAndFlush(any(P2pHandover.class))).thenReturn(saved);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(701L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(701L)))
                                .thenReturn(List.of());

                P2pHandoverService.Detail detail = service.record(
                                500L, new P2pHandoverService.RecordRequest("   ", 2L, reqId), List.of(), finderUser);

                ArgumentCaptor<P2pHandover> captor = ArgumentCaptor.forClass(P2pHandover.class);
                verify(handovers).saveAndFlush(captor.capture());
                assertNull(captor.getValue().getNote());
                assertNull(detail.note());
                assertEquals("WAITING_CONFIRMATION", detail.status());
        }

        // UTC012 - record: claimant calling record throws FORBIDDEN
        @Test
        void record_whenClaimantCalls_throwsForbidden() {
                UUID reqId = UUID.randomUUID();
                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("HANDOVER:500:Met", List.of()))
                                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "img-hash"));
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));

                AppException ex = assertThrows(AppException.class, () -> service.record(
                                500L, new P2pHandoverService.RecordRequest("Met", 2L, reqId), List.of(), claimantUser));
                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        // UTC014 - record: existing handover with a new clientRequestId throws HANDOVER_STATE_CHANGED
        @Test
        void record_whenHandoverAlreadyExistsWithNewClientRequestId_throwsHandoverStateChanged() {
                UUID reqId = UUID.randomUUID();
                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Met", List.of()))
                                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "img-hash"));
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByRecordedByAndClientRequestId(10L, reqId)).thenReturn(Optional.empty());
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(handovers.existsByClaimId(500L)).thenReturn(true);

                AppException ex = assertThrows(AppException.class, () -> service.record(
                                500L, new P2pHandoverService.RecordRequest("Met", 2L, reqId), List.of(), finderUser));
                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
        }

        // UTC017 - record: DB failure after image upload cleans up uploaded images
        @Test
        void record_whenDbSaveFailsAfterUpload_cleansUpImages() {
                UUID reqId = UUID.randomUUID();
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                WorkflowImages.Image uploaded = new WorkflowImages.Image(
                                "handovers/500/1.jpg", "1.jpg", "image/jpeg", 5L, "h", 0);

                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("HANDOVER:500:Met", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(handovers.existsByClaimId(500L)).thenReturn(false);
                when(images.upload("handovers/500", prepared)).thenReturn(List.of(uploaded));
                doThrow(new RuntimeException("DB fail")).when(handovers).saveAndFlush(any(P2pHandover.class));

                assertThrows(RuntimeException.class, () -> service.record(
                                500L, new P2pHandoverService.RecordRequest("Met", 2L, reqId), List.of(), finderUser));

                verify(images).cleanup(List.of(uploaded));
        }

        // UTC018 - confirm: completes handover & post and auto-closes waiting claims with ITEM_RETURNED
        @Test
        void confirm_whenWaitingAndOtherClaimsExist_completesAndAutoClosesWaitingClaims() {
                P2pHandover handover = waitingHandover(702L);
                Claim waitingOther = Claim.submit(post, outsider, "Mine", null);
                ReflectionTestUtils.setField(waitingOther, "id", 501L);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(702L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(702L)))
                                .thenReturn(List.of());
                when(claims.findWaitingForReturn(100L)).thenReturn(List.of(waitingOther));

                P2pHandoverService.Detail detail = service.confirm(
                                500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser);

                assertEquals("COMPLETED", detail.status());
                assertEquals("COMPLETED", approvedClaim.getStatus());
                assertEquals("RETURNED", post.getStatus());
                assertEquals("CLOSED", waitingOther.getStatus());
                assertEquals("ITEM_RETURNED", waitingOther.getCloseReason());
                verify(activities, org.mockito.Mockito.times(2)).save(any(ClaimActivity.class));
        }

        // UTC019 - confirm: already COMPLETED handover returns idempotently without writes
        @Test
        void confirm_whenAlreadyCompleted_returnsIdempotentlyWithoutWrites() {
                P2pHandover handover = waitingHandover(702L);
                handover.confirm(20L);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(702L))).thenReturn(List.of());
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(702L)))
                                .thenReturn(List.of());

                P2pHandoverService.Detail detail = service.confirm(
                                500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser);

                assertEquals("COMPLETED", detail.status());
                assertFalse(detail.canConfirm());
                verify(handovers, never()).saveAndFlush(any());
                verify(activities, never()).save(any());
                verify(claims, never()).findWaitingForReturn(anyLong());
        }

        // UTC020 - confirm: null request throws WORKFLOW_INPUT_INVALID
        @Test
        void confirm_whenRequestNull_throwsWorkflowInputInvalid() {
                AppException ex = assertThrows(AppException.class, () -> service.confirm(500L, null, claimantUser));
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, ex.getErrorCode());
        }

        // UTC022 - confirm: missing handover throws HANDOVER_STATE_CHANGED
        @Test
        void confirm_whenHandoverMissing_throwsHandoverStateChanged() {
                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                                () -> service.confirm(500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser));
                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
        }

        // UTC024 - confirm: existing open issue blocks confirmation with HANDOVER_STATE_CHANGED
        @Test
        void confirm_whenOpenIssueExists_throwsHandoverStateChanged() {
                P2pHandover handover = waitingHandover(702L);
                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(702L)))
                                .thenReturn(List.of(901L));

                AppException ex = assertThrows(AppException.class,
                                () -> service.confirm(500L, new P2pHandoverService.ConfirmRequest(1L), claimantUser));
                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
        }

        // UTC026, UTC027 - issue: blank reason and 5001-char reason throw WORKFLOW_INPUT_INVALID
        @Test
        void issue_whenReasonBlankOrTooLong_throwsWorkflowInputInvalid() {
                UUID reqId = UUID.randomUUID();
                // UTC026 - blank reason
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.issue(500L, new P2pHandoverService.IssueRequest("", 1L, reqId),
                                                List.of(), claimantUser)).getErrorCode());
                // UTC027 - reason 5001 chars
                assertEquals(ErrorCode.WORKFLOW_INPUT_INVALID, assertThrows(AppException.class,
                                () -> service.issue(500L,
                                                new P2pHandoverService.IssueRequest("x".repeat(5001), 1L, reqId),
                                                List.of(), claimantUser)).getErrorCode());
        }

        // UTC028 - issue: boundary 5000-char reason is accepted
        @Test
        void issue_whenReasonAtMaxLength5000_succeeds() {
                String maxReason = "x".repeat(5000);
                UUID reqId = UUID.randomUUID();
                P2pHandover handover = waitingHandover(703L);
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                Report report = new Report(20L, "HANDOVER_ISSUE", "CLAIM", 500L, maxReason, reqId, "rhash");
                ReflectionTestUtils.setField(report, "id", 902L);
                ReportService.Detail issueDetail = new ReportService.Detail(
                                902L, "HANDOVER_ISSUE", "CLAIM", 500L, maxReason, "OPEN", null,
                                OffsetDateTime.now(), List.of(), List.of(), "Bàn giao tài sản P2P");

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:" + maxReason, List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);
                when(images.upload("handover-reports/500", prepared)).thenReturn(List.of());
                when(claims.findPostId(500L)).thenReturn(Optional.of(100L));
                when(posts.findForClaimUpdate(100L)).thenReturn(Optional.of(post));
                when(claims.findForUpdate(500L)).thenReturn(Optional.of(approvedClaim));
                when(em.find(Account.class, 20L)).thenReturn(claimant);
                when(reports.insert(eq(20L), eq("HANDOVER_ISSUE"), eq("CLAIM"), eq(500L),
                                eq(maxReason), eq(reqId), anyString(), eq(List.of()))).thenReturn(report);
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of(), List.of(), List.of(902L));
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of());
                when(reportRepository.findById(902L)).thenReturn(Optional.of(report));
                when(reports.detail(report)).thenReturn(issueDetail);

                P2pHandoverService.Detail result = service.issue(
                                500L, new P2pHandoverService.IssueRequest(maxReason, 1L, reqId), List.of(),
                                claimantUser);

                assertEquals("DISPUTED", result.status());
                assertEquals(902L, result.issue().id());
        }

        // UTC029 - issue: post owner calling issue throws FORBIDDEN
        @Test
        void issue_whenFinderCalls_throwsForbidden() {
                UUID reqId = UUID.randomUUID();
                when(actors.actor(finderUser)).thenReturn(finder);
                when(images.prepare("ISSUE:500:Wrong item", List.of()))
                                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "img-hash"));
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));

                AppException ex = assertThrows(AppException.class, () -> service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                finderUser));
                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }

        // UTC030 - issue: already COMPLETED handover throws HANDOVER_STATE_CHANGED
        @Test
        void issue_whenHandoverCompleted_throwsHandoverStateChanged() {
                UUID reqId = UUID.randomUUID();
                P2pHandover completed = waitingHandover(703L);
                completed.confirm(20L);
                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:Wrong item", List.of()))
                                .thenReturn(new WorkflowImages.Prepared(List.of(), List.of(), "img-hash"));
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(completed));
                when(routing.matches(approvedClaim, Set.of("RESERVED"))).thenReturn(true);

                AppException ex = assertThrows(AppException.class, () -> service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                claimantUser));
                assertEquals(ErrorCode.HANDOVER_STATE_CHANGED, ex.getErrorCode());
        }

        // UTC031 - issue: idempotent retry with same clientRequestId returns existing detail without upload
        @Test
        void issue_whenRetryingSameIssueRequest_returnsExistingWithoutUpload() {
                UUID reqId = UUID.randomUUID();
                P2pHandover handover = waitingHandover(703L);
                handover.reportIssue();
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                Report report = new Report(20L, "HANDOVER_ISSUE", "CLAIM", 500L, "Wrong item", reqId, "img-hash");
                ReflectionTestUtils.setField(report, "id", 901L);
                ReportService.Detail issueDetail = new ReportService.Detail(
                                901L, "HANDOVER_ISSUE", "CLAIM", 500L, "Wrong item", "OPEN", null,
                                OffsetDateTime.now(), List.of(), List.of(), "Bàn giao tài sản P2P");

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:Wrong item", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(reports.retry(20L, reqId, "img-hash")).thenReturn(issueDetail);
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));
                when(jdbc.query(contains("p2p_handover_issue"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of(901L));
                when(jdbc.query(contains("p2p_handover_evidence"), any(RowMapper.class), eq(703L)))
                                .thenReturn(List.of());
                when(reportRepository.findById(901L)).thenReturn(Optional.of(report));
                when(reports.detail(report)).thenReturn(issueDetail);

                P2pHandoverService.Detail result = service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                claimantUser);

                assertEquals("DISPUTED", result.status());
                assertEquals(901L, result.issue().id());
                verify(images, never()).upload(anyString(), any());
                verify(reports, never()).insert(anyLong(), anyString(), anyString(), anyLong(), anyString(), any(),
                                anyString(), any());
        }

        // UTC032 - issue: clientRequestId already bound to a different report throws WORKFLOW_DUPLICATE_PAYLOAD
        @Test
        void issue_whenClientRequestIdBelongsToDifferentReport_throwsWorkflowDuplicatePayload() {
                UUID reqId = UUID.randomUUID();
                P2pHandover handover = waitingHandover(703L);
                WorkflowImages.Prepared prepared = new WorkflowImages.Prepared(List.of(), List.of(), "img-hash");
                ReportService.Detail otherReport = new ReportService.Detail(
                                888L, "GENERAL", "POST", 100L, "Spam", "OPEN", null,
                                OffsetDateTime.now(), List.of(), List.of(), null);

                when(actors.actor(claimantUser)).thenReturn(claimant);
                when(images.prepare("ISSUE:500:Wrong item", List.of())).thenReturn(prepared);
                when(claims.findById(500L)).thenReturn(Optional.of(approvedClaim));
                when(reports.retry(20L, reqId, "img-hash")).thenReturn(otherReport);
                when(handovers.findByClaimId(500L)).thenReturn(Optional.of(handover));

                AppException ex = assertThrows(AppException.class, () -> service.issue(
                                500L, new P2pHandoverService.IssueRequest("Wrong item", 1L, reqId), List.of(),
                                claimantUser));
                assertEquals(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD, ex.getErrorCode());
        }

        private static P2pHandover waitingHandover(long id) {
                P2pHandover handover = new P2pHandover(500L, 10L, "Note", UUID.randomUUID(), "hash");
                ReflectionTestUtils.setField(handover, "id", id);
                ReflectionTestUtils.setField(handover, "status", "WAITING_CONFIRMATION");
                ReflectionTestUtils.setField(handover, "version", 1L);
                return handover;
        }

        private static Account account(Long id, String name) {
                Account a = new Account();
                a.setId(id);
                a.setEmail(name.toLowerCase() + "@fpt.edu.vn");
                a.setFullName(name);
                a.setRole(AccountRole.USER);
                a.setStatus(AccountStatus.ACTIVE);
                return a;
        }
}