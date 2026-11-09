package com.example.base.service.claim;

import com.example.base.dto.claim.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class ClaimService {
    private static final Set<String> FILTERS = Set.of("ALL", "PENDING", "APPROVED", "REJECTED", "CANCELLED", "COMPLETED", "CLOSED");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "APPROVED");
    private static final Set<String> RETRYABLE_STATUSES = Set.of("REJECTED", "CANCELLED");
    private final ClaimRepository claims;
    private final ClaimActivityRepository activities;
    private final PostRepository posts;
    private final AccountRepository accounts;
    private final ClaimRoutingPolicy routing;
    private final P2pHandoverRepository handovers;
    private final ImageStorage storage;
    private final ImageValidator validator;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final ClaimQuestionnaireService questionnaires;
    private final com.example.base.service.chat.ChatConversationService chatConversations;
    private final com.example.base.service.notification.NotificationService notifications;

    public ClaimResultDto submit(long postId, SubmitClaimRequest request, List<MultipartFile> files, CustomUserDetails user) {
        return create(postId, null, request, files, user);
    }

    /** A retry creates another dossier; rejected/cancelled claims and evidence are immutable. */
    public ClaimResultDto retry(long previousId, SubmitClaimRequest request, List<MultipartFile> files, CustomUserDetails user) {
        Long postId = new TransactionTemplate(transactionManager).execute(tx -> {
            Account actor = activeActor(user);
            Claim old = claim(previousId);
            requireRetryOwner(old, actor.getId());
            return old.getPost().getId();
        });
        return create(postId, previousId, request, files, user);
    }

    private ClaimResultDto create(long postId, Long previousId, SubmitClaimRequest request,
                                  List<MultipartFile> files, CustomUserDetails user) {
        validateSubmission(request);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Fail before uploading, then recheck under the post lock after network I/O.
        long actorId = transaction.execute(tx -> {
            Account actor = activeActor(user);
            Post post = post(postId);
            validateNewClaim(post, actor.getId(), previousId);
            questionnaires.validateAndSnapshot(post, request);
            return actor.getId();
        });
        var images = validator.validate(files);
        List<String> uploaded = new ArrayList<>();
        try {
            for (var image : images) {
                String key = "claims/" + actorId + "/" + UUID.randomUUID() + "." + image.extension();
                uploaded.add(key); // Include uncertain PUTs so failed submissions can be compensated.
                storage.put(key, image.bytes(), image.mime());
            }
            return transaction.execute(tx -> {
                Post post = lockPost(postId);
                Account actor = activeActor(user);
                ItemReceipt receipt = routing.lockReceipt(post);
                validateNewClaim(post, actor.getId(), previousId);
                Claim claim = Claim.submit(post, actor, request.description(), request.evidenceNote(), receipt);
                questionnaires.validateAndSnapshot(post, request).forEach(claim::addAnswer);
                for (int i = 0; i < images.size(); i++) {
                    var image = images.get(i);
                    claim.addEvidence(new ClaimEvidence(uploaded.get(i), safeName(files.get(i).getOriginalFilename()),
                            image.mime(), image.bytes().length, image.hash(), i));
                }
                claims.saveAndFlush(claim);
                activities.saveAndFlush(new ClaimActivity(claim, actor, "SUBMIT", null, null));
                notifications.notifyUser(
                        post.getAccount().getId(),
                        "CLAIM_SUBMITTED",
                        "Có yêu cầu nhận đồ mới (#" + claim.getId() + ")",
                        name(actor) + " vừa gửi hồ sơ xác minh quyền sở hữu cho bài đăng \"" + post.getTitle() + "\". Vui lòng kiểm tra và xét duyệt.",
                        "/bai-dang/" + post.getId() + "/claims/" + claim.getId(),
                        true
                );
                return result(claim);
            });
        } catch (RuntimeException failure) {
            for (String key : uploaded) {
                try { storage.delete(key); }
                catch (RuntimeException cleanupFailure) { log.error("Could not clean up claim evidence {}", key); }
            }
            if (failure instanceof DataIntegrityViolationException) throw new AppException(ErrorCode.CLAIM_DUPLICATE);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public ClaimQuestionnaireDto questionnaire(long postId, CustomUserDetails user) {
        Account actor = activeActor(user);
        Post post = post(postId);
        validateNewClaim(post, actor.getId(), null);
        return questionnaires.forCategory(post.getCategory());
    }

    @Transactional(readOnly = true)
    public ClaimResultDto latestOnPost(long postId, CustomUserDetails user) {
        Account actor = activeActor(user);
        if (postId <= 0) throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        Post post = post(postId);
        return claims.findFirstByPost_IdAndClaimant_IdAndClaimTypeOrderByCreatedAtDescIdDesc(postId, actor.getId(), post.getCustodyMode())
                .map(this::result).orElse(null);
    }

    @Transactional(readOnly = true)
    public ClaimPageDto mine(int page, int size, String status, CustomUserDetails user) {
        Account actor = activeActor(user);
        return page(claims.findMine(actor.getId(), filter(status), pageable(page, size)), page, size);
    }

    @Transactional(readOnly = true)
    public ClaimPageDto received(int page, int size, String status, CustomUserDetails user) {
        Account actor = activeActor(user);
        return page(claims.findReceived(actor.getId(), filter(status), pageable(page, size)), page, size);
    }

    @Transactional(readOnly = true)
    public ClaimPageDto office(int page, int size, String status, CustomUserDetails user) {
        Account actor = activeActor(user);
        // DaiHV integration: office dossier access follows the Approver role.
        if (actor.getRole() != AccountRole.APPROVER) throw new AppException(ErrorCode.FORBIDDEN);
        return page(claims.findOffice(filter(status), pageable(page, size)), page, size);
    }

    @Transactional(readOnly = true)
    public ClaimPageDto onPost(long postId, int page, int size, String status, CustomUserDetails user) {
        Account actor = activeActor(user);
        Post post = post(postId);
        boolean approver = actor.getRole() == AccountRole.APPROVER; // Office reviewer; repository parameter retains its legacy name.
        if (!Objects.equals(post.getAccount().getId(), actor.getId()) && !approver) throw missing();
        return page(claims.findOnPost(postId, actor.getId(), approver, filter(status), pageable(page, size)), page, size);
    }

    @Transactional(readOnly = true)
    public ClaimDetailDto detail(long id, CustomUserDetails user) {
        return readDetail(id, null, user);
    }

    @Transactional(readOnly = true)
    public ClaimDetailDto detailOnPost(long postId, long id, CustomUserDetails user) {
        return readDetail(id, postId, user);
    }

    private ClaimDetailDto readDetail(long id, Long expectedPostId, CustomUserDetails user) {
        Account actor = activeActor(user);
        Claim claim = claim(id);
        Post post = claim.getPost();
        if (expectedPostId != null && !Objects.equals(post.getId(), expectedPostId)) throw missing();
        boolean claimant = Objects.equals(claim.getClaimant().getId(), actor.getId());
        boolean reviewer = routing.reviewer(claim, actor);
        if (!claimant && !reviewer) throw missing();
        // Never generate evidence URLs until participation has been checked.
        var evidence = claim.getEvidence().stream().map(image -> new ClaimDetailDto.EvidenceDto(
                image.getId(), storage.readUrl(image.getStorageKey(), null), image.getOriginalName(),
                image.getMimeType(), image.getSizeBytes())).toList();
        var timeline = activities.findByClaim_IdOrderByCreatedAtAscIdAsc(id).stream()
                .map(event -> new ClaimDetailDto.ActivityDto(event.getId(), event.getAction(), name(event.getActor()),
                        event.getFromStatus(), event.getToStatus(), event.getReason(), event.getCreatedAt())).toList();
        boolean open = routing.matches(claim, Set.of("OPEN"));
        boolean retry = claimant && RETRYABLE_STATUSES.contains(claim.getStatus()) && open
                && !claims.existsByPost_IdAndClaimant_IdAndStatusIn(post.getId(), actor.getId(), ACTIVE_STATUSES);
        return new ClaimDetailDto(claim.getId(), post.getId(), post.getTitle(), post.getStatus(),
                claim.getClaimant().getId(), name(claim.getClaimant()), name(post.getAccount()), claim.getStatus(),
                claim.getDescription(), claim.getEvidenceNote(), claim.getCreatedAt(), claim.getUpdatedAt(), claim.getVersion(),
                reviewer && open && "PENDING".equals(claim.getStatus()), claimant && cancellable(claim), retry, evidence, timeline,
                claim.getAnswers().stream().map(answer -> new ClaimDetailDto.AnswerDto(answer.getQuestionCode(),
                        answer.getQuestionLabel(), answer.getAnswerText(), answer.getSectionTitle(),
                        answer.getTemplateCode(), answer.getTemplateVersion())).toList(), chatConversations.canOpen(claim),
                claim.getClaimType(), claim.getReceipt() == null || claim.getReceipt().getStorageLocation() == null
                        ? null : claim.getReceipt().getStorageLocation().getName(),claim.getCloseReason(),claim.getClosedAt());
    }

    @Transactional
    public ClaimResultDto review(long id, ReviewClaimRequest request, CustomUserDetails user) {
        Account actor = activeActor(user);
        Post post = lockPost(claimPostId(id));
        routing.lockReceipt(post);
        Claim claim = lockClaim(id);
        // DaiHV integration: office approval must use OfficeClaimService, which creates the appointment.
        if (claim.isOffice()) throw new AppException(ErrorCode.BAD_REQUEST, "Duyệt hồ sơ tại mục Yêu cầu nhận đồ của văn phòng.");
        if (!routing.reviewer(claim, actor)) throw missing();
        if (request == null || !Set.of("APPROVED", "REJECTED").contains(Objects.toString(request.decision(), ""))
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 2000) {
            throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        }
        requireVersion(claim, request.expectedVersion());
        if (!routing.matches(claim, Set.of("OPEN")) || !"PENDING".equals(claim.getStatus())) throw stateChanged();
        if ("APPROVED".equals(request.decision())) {
            if (claims.existsByPost_IdAndStatusIn(post.getId(), Set.of("APPROVED"))) throw stateChanged();
            post.reserveForClaim();
        }
        claim.review(request.decision());
        if ("APPROVED".equals(request.decision()) && !claim.isOffice()) chatConversations.forApprovedClaim(claim);
        activities.save(new ClaimActivity(claim, actor,
                "APPROVED".equals(request.decision()) ? "APPROVE" : "REJECT", "PENDING", request.reason()));
        claims.flush();
        if ("APPROVED".equals(request.decision())) {
            notifications.notifyUser(
                    claim.getClaimant().getId(),
                    "CLAIM_APPROVED",
                    "Yêu cầu nhận đồ #" + claim.getId() + " đã được chấp nhận",
                    "Chủ bài đăng \"" + post.getTitle() + "\" đã chấp nhận yêu cầu nhận đồ của bạn. Bạn có thể nhắn tin trao đổi trực tiếp để hẹn nhận lại đồ.",
                    "/bai-dang/" + post.getId() + "/claims/" + claim.getId(),
                    true
            );
        } else {
            notifications.notifyUser(
                    claim.getClaimant().getId(),
                    "CLAIM_REJECTED",
                    "Yêu cầu nhận đồ #" + claim.getId() + " bị từ chối",
                    "Chủ bài đăng \"" + post.getTitle() + "\" đã từ chối yêu cầu nhận đồ của bạn. Lý do: " + request.reason(),
                    "/bai-dang/" + post.getId() + "/claims/" + claim.getId(),
                    true
            );
        }
        return result(claim);
    }

    @Transactional
    public ClaimResultDto cancel(long id, CancelClaimRequest request, CustomUserDetails user) {
        Account actor = activeActor(user);
        Post post = lockPost(claimPostId(id));
        routing.lockReceipt(post);
        Claim claim = lockClaim(id);
        if (!Objects.equals(claim.getClaimant().getId(), actor.getId())) throw missing();
        if (request == null || (request.reason() != null && request.reason().length() > 2000)) {
            throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        }
        requireVersion(claim, request.expectedVersion());
        if (!cancellable(claim)) throw stateChanged();
        String previous = claim.getStatus();
        claim.cancel();
        chatConversations.notifyChanged(claim);
        activities.save(new ClaimActivity(claim, actor, "CANCEL", previous, request.reason()));
        claims.flush();
        if ("APPROVED".equals(previous) && "RESERVED".equals(post.getStatus())
                && !claims.existsByPost_IdAndStatusIn(post.getId(), Set.of("APPROVED"))) {
            post.reopenAfterClaimCancellation();
        }
        notifications.notifyUser(
                post.getAccount().getId(),
                "CLAIM_CANCELLED",
                "Người yêu cầu đã hủy hồ sơ nhận đồ #" + claim.getId(),
                name(actor) + " đã rút lại yêu cầu nhận đồ cho bài đăng \"" + post.getTitle() + "\".",
                "/bai-dang/" + post.getId() + "/claims/" + claim.getId(),
                false
        );
        return result(claim);
    }

    private void validateNewClaim(Post post, long actorId, Long previousId) {
        if (Objects.equals(post.getAccount().getId(), actorId) || (post.getCreatedBy() != null && Objects.equals(post.getCreatedBy().getId(), actorId))) {
            throw new AppException(ErrorCode.CLAIM_SELF_NOT_ALLOWED);
        }
        if (!routing.canSubmit(post)) throw new AppException(ErrorCode.CLAIM_POST_UNAVAILABLE);
        if (previousId != null) {
            Claim old = claim(previousId);
            requireRetryOwner(old, actorId);
            if (!Objects.equals(old.getPost().getId(), post.getId())) throw missing();
            if (!Objects.equals(old.getClaimType(), post.getCustodyMode())) throw stateChanged();
        }
        if (claims.existsByPost_IdAndClaimant_IdAndStatusIn(post.getId(), actorId, ACTIVE_STATUSES)) {
            throw new AppException(ErrorCode.CLAIM_DUPLICATE);
        }
    }

    private void requireRetryOwner(Claim old, long actorId) {
        if (!Objects.equals(old.getClaimant().getId(), actorId)) throw missing();
        if (!RETRYABLE_STATUSES.contains(old.getStatus())) throw stateChanged();
    }

    private boolean cancellable(Claim claim) {
        // DaiHV integration: office cancellation must also invalidate its appointment/invitation.
        return !claim.isOffice() && Set.of("PENDING", "APPROVED").contains(claim.getStatus())
                && !handovers.existsByClaimId(claim.getId())
                && routing.matches(claim, Set.of("OPEN", "RESERVED"));
    }

    private Account activeActor(CustomUserDetails user) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        Account actor = accounts.findById(user.getId()).orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        entityManager.refresh(actor);
        CustomUserDetails current = new CustomUserDetails(actor);
        if (actor.getRole() == null || actor.getStatus() != AccountStatus.ACTIVE
                || !current.isAccountNonExpired() || !current.isAccountNonLocked()) throw new AppException(ErrorCode.FORBIDDEN);
        return actor;
    }

    private Post post(long id) {
        if (id <= 0) throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        return posts.findById(id).orElseThrow(this::missing);
    }
    private Claim claim(long id) {
        Claim c = claims.findById(id).orElseThrow(this::missing);
        if (!"P2P".equals(c.getClaimType()) || c.getClaimant() == null) throw missing();
        return c;
    }
    private long claimPostId(long id) { return claims.findPostId(id).orElseThrow(this::missing); }
    private Post lockPost(long id) {
        Post post = posts.findForClaimUpdate(id).orElseThrow(this::missing);
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
        return post;
    }
    private Claim lockClaim(long id) {
        Claim claim = claims.findForUpdate(id).orElseThrow(this::missing);
        entityManager.refresh(claim, LockModeType.PESSIMISTIC_WRITE);
        if (!"P2P".equals(claim.getClaimType()) || claim.getClaimant() == null) throw missing();
        return claim;
    }
    private void requireVersion(Claim claim, Long version) {
        if (version == null || version < 0) throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        if (claim.getVersion() != version) throw new AppException(ErrorCode.CLAIM_STALE);
    }
    private void validateSubmission(SubmitClaimRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()
                || request.description().length() > 5000 || (request.evidenceNote() != null && request.evidenceNote().length() > 5000)) {
            throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        }
    }
    private String filter(String status) {
        if (status == null || !FILTERS.contains(status)) throw new AppException(ErrorCode.CLAIM_INPUT_INVALID);
        return status;
    }
    private PageRequest pageable(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new AppException(ErrorCode.POST_PAGINATION_INVALID);
        return PageRequest.of(page - 1, size);
    }
    private ClaimPageDto page(Page<Claim> result, int page, int size) {
        return new ClaimPageDto(result.getContent().stream().map(c -> new ClaimSummaryDto(c.getId(), c.getPost().getId(),
                c.getPost().getTitle(), name(c.getClaimant()), name(c.getPost().getAccount()), c.getStatus(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getClaimType(),c.getCloseReason())).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    private String name(Account account) { return account.getFullName() == null || account.getFullName().isBlank() ? "Thành viên" : account.getFullName(); }
    private ClaimResultDto result(Claim claim) { return new ClaimResultDto(claim.getId(), claim.getPost().getId(), claim.getStatus(), claim.getVersion()); }
    private AppException missing() { return new AppException(ErrorCode.CLAIM_NOT_FOUND); }
    private AppException stateChanged() { return new AppException(ErrorCode.CLAIM_STATE_INVALID); }
    private String safeName(String name) {
        if (name == null) return null;
        String clean = name.replace('\\', '/'); clean = clean.substring(clean.lastIndexOf('/') + 1);
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }
}
