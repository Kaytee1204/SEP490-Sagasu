package com.example.base.service.claim;

import com.example.base.entity.AccountRole;
import com.example.base.entity.Claim;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ClaimRepository;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.ChatAccessPolicy;
import com.example.base.service.chat.ChatConversationService;
import com.example.base.service.notification.NotificationService;
import com.example.base.service.report.ReportService;
import com.example.base.service.report.WorkflowImages;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class P2pClaimAppealService {
    private static final DateTimeFormatter LOCAL_DEADLINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final Set<String> RESERVED = Set.of("RESERVED");
    private final JdbcTemplate jdbc;
    private final ClaimRepository claims;
    private final PostRepository posts;
    private final ClaimRoutingPolicy routing;
    private final ChatAccessPolicy actors;
    private final ChatConversationService chat;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactions;
    private final WorkflowImages images;
    private final ReportService reports;
    private final NotificationService notifications;

    public enum State { OPEN, UNDER_REVIEW, REVIEW_REQUIRED, CLEARED }
    public record HandoverWindow(OffsetDateTime deadline, State state) {
        public boolean blocksHandover() { return state != State.CLEARED; }
    }
    public record Status(long approvalId, OffsetDateTime deadline, State state, boolean canAppeal,
                         Long reportId, String reportStatus) {}
    public record Submit(Long approvalId, String reason, UUID clientRequestId, String reasonCode) {
        public Submit(Long approvalId,String reason,UUID clientRequestId) { this(approvalId,reason,clientRequestId,null); }
    }
    public record Submitted(long reportId) {}
    private record Approval(long id, long selectedClaimId, OffsetDateTime deadline, boolean pending, boolean upheld) {
        State stateAt(OffsetDateTime now) {
            if (pending) return State.UNDER_REVIEW;
            if (upheld) return State.REVIEW_REQUIRED;
            return now.isBefore(deadline) ? State.OPEN : State.CLEARED;
        }
    }
    private record ExistingAppeal(long id, String status, UUID requestId, String hash) {}

    // Approval, appeals and handovers acquire the same post lock before changing their state.
    public OffsetDateTime open(Claim selected) {
        var waiting = competingClaims(selected);
        if (waiting.isEmpty()) return null;
        OffsetDateTime deadline = OffsetDateTime.now().plusHours(24);
        if (selected.isOffice()) {
            jdbc.update("insert into p2p_claim_approval(approved_claim_id, appeal_until, custody_mode) values (?, ?, 'OFFICE')",
                    selected.getId(), deadline);
        } else {
            jdbc.update("insert into p2p_claim_approval(approved_claim_id, appeal_until) values (?, ?)",
                    selected.getId(), deadline);
        }
        for (Claim claimant : waiting) {
            notifications.notifyUser(claimant.getClaimant().getId(), "CLAIM_APPEAL_WINDOW",
                    "Một yêu cầu nhận đồ khác đã được chấp thuận",
                    "Một yêu cầu khác cho bài «" + selected.getPost().getTitle()
                            + "» đã được chấp thuận. Nếu bạn có căn cứ sở hữu, hãy gửi tranh chấp trước "
                            + LOCAL_DEADLINE.format(deadline) + ".",
                    claimPath(claimant), false);
        }
        return deadline;
    }

    public void onPendingClaimWithdrawn(Claim claim) {
        var reportIds = jdbc.queryForList("select * from withdraw_p2p_appeal(?)", Long.class, claim.getId());
        for (long reportId : reportIds) {
            chat.notifyAppealChanged(reportId);
            var recipients = jdbc.queryForList("""
                select r.assigned_to from report r where r.report_id=? and r.assigned_to is not null
                union select chosen.claimant_id from p2p_claim_appeal e join p2p_claim_approval a using(approval_id)
                join claim chosen on chosen.claim_id=a.approved_claim_id where e.report_id=? and chosen.claimant_id is not null
                union select account_id from post where post_id=?
                """, Long.class, reportId, reportId, claim.getPost().getId());
            for (long recipient : recipients) {
                boolean manager = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select assigned_to is not distinct from ? from report where report_id=?", Boolean.class, recipient, reportId));
                if (!manager) jdbc.update("insert into report_recipient(report_id,account_id) values(?,?) on conflict do nothing", reportId, recipient);
                notifications.notifyUser(recipient, "REPORT_RESOLVED", "Người gửi đã rút tranh chấp",
                    "Yêu cầu nhận đồ đã được rút. Các tranh chấp khác vẫn được xử lý riêng.",
                    (manager ? "/admin/reports/" : "/bao-cao/") + reportId, false);
            }
        }
        releaseWindowIfUncontested(claim.getPost().getId());
    }

    public void releaseWindowIfUncontested(long postId) {
        var released = jdbc.queryForList("""
            update p2p_claim_approval a set appeal_until=now()
            from claim chosen where chosen.claim_id=a.approved_claim_id and chosen.post_id=?
              and a.custody_mode='P2P' and a.closed_at is null and a.appeal_until>now()
              and not exists (select 1 from claim other where other.post_id=chosen.post_id
                  and other.claim_type='P2P' and other.status='PENDING' and other.claimant_id is not null)
              and not exists (select 1 from p2p_claim_appeal e join report r using(report_id)
                  where e.approval_id=a.approval_id and r.status in ('OPEN','IN_REVIEW'))
            returning a.approved_claim_id
            """, Long.class, postId);
        released.forEach(chat::notifyHandoverChanged);
    }

    public boolean close(Claim selected) {
        return jdbc.update("update p2p_claim_approval set closed_at=now() where approved_claim_id=? and closed_at is null",
                selected.getId()) > 0;
    }

    public void reopen(Claim selected) {
        if (!close(selected)) return;
        for (Claim pending : competingClaims(selected)) {
            notifications.notifyUser(pending.getClaimant().getId(), "CLAIM_REVIEW_REOPENED",
                    "Yêu cầu nhận đồ được xét duyệt lại",
                    "Bài «" + selected.getPost().getTitle() + "» đã mở lại. Hồ sơ đang chờ của bạn tiếp tục được xét duyệt.",
                    claimPath(pending), false);
        }
    }

    public boolean hasUnresolvedAppeal(long postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                  select 1 from claim c join p2p_claim_approval a on a.approved_claim_id=c.claim_id
                  join p2p_claim_appeal p on p.approval_id=a.approval_id join report r on r.report_id=p.report_id
                  where c.post_id=? and a.closed_at is null and c.claim_type=a.custody_mode and r.status in ('OPEN','IN_REVIEW')
                )
                """, Boolean.class, postId));
    }

    public HandoverWindow handoverWindow(Claim selected) {
        Approval approval = activeApproval(selected.getPost().getId());
        if (approval == null || approval.selectedClaimId() != selected.getId()) return null;
        return new HandoverWindow(approval.deadline(), approval.stateAt(OffsetDateTime.now()));
    }

    public boolean canReopenFromDecision(Claim selected, long approvalId) {
        Approval approval = activeApproval(selected.getPost().getId());
        return approval != null && approval.id() == approvalId && approval.selectedClaimId() == selected.getId()
                && approval.stateAt(OffsetDateTime.now()) == State.REVIEW_REQUIRED;
    }

    public boolean hasCompetitors(Claim selected) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from claim
              where post_id=? and claim_id<>? and claim_type=? and status='PENDING' and claimant_id is not null)
            """,Boolean.class,selected.getPost().getId(),selected.getId(),selected.getClaimType()));
    }

    public void requireOfficeClearance(Claim claim) {
        var window = handoverWindow(claim);
        if (window != null && window.blocksHandover()) {
            throw new AppException(ErrorCode.CLAIM_STATE_INVALID,
                "Chưa thể lập lịch hoặc trao trả khi còn trong thời hạn gửi tranh chấp hoặc đang chờ xử lý tranh chấp.");
        }
    }

    @Transactional(readOnly = true)
    public Status status(long claimId, CustomUserDetails user) {
        long actor = actors.actor(user).getId();
        Claim claim = claims.findById(claimId).orElseThrow(this::missing);
        if (claim.getClaimant() == null) throw missing();
        boolean ownClaim = claim.getClaimant().getId() == actor;
        if (!ownClaim && (claim.isOffice() || claim.getPost().getAccount().getId() != actor)) throw missing();
        if (!routing.matches(claim, RESERVED)) return claim.isOffice() ? latestOfficeAppeal(claimId) : null;
        Approval approval = activeApproval(claim.getPost().getId());
        if (approval == null) return claim.isOffice() ? latestOfficeAppeal(claimId) : null;
        OffsetDateTime now = OffsetDateTime.now();
        State state = approval.stateAt(now);
        if (approval.selectedClaimId() == claimId) {
            if (claim.isOffice()) return latestOfficeAppeal(claimId);
            return new Status(approval.id(), approval.deadline(), state, false, null, null);
        }
        if (!ownClaim) return null;
        ExistingAppeal existing = existingAppeal(approval.id(), claimId);
        if (existing == null && (!"PENDING".equals(claim.getStatus())
                || (!claim.isOffice() && ClaimRepository.disputeEndsReview(claims.findLatestDisputeOutcome(claimId))))) return null;
        boolean canAppeal = existing == null && now.isBefore(approval.deadline());
        return new Status(approval.id(), approval.deadline(), state, canAppeal,
                existing == null ? null : existing.id(), existing == null ? null : existing.status());
    }

    public Status latestOfficeAppeal(long claimId) {
        return jdbc.query("""
            select p.approval_id,a.appeal_until,r.report_id,r.status
            from p2p_claim_appeal p join p2p_claim_approval a using (approval_id)
            join report r using (report_id) where p.appellant_claim_id=? and a.custody_mode='OFFICE'
            order by p.approval_id desc limit 1
            """, (rs, n) -> new Status(rs.getLong(1), rs.getObject(2, OffsetDateTime.class), State.CLEARED,
                false, rs.getLong(3), rs.getString(4)), claimId).stream().findFirst().orElse(null);
    }

    public Submitted submit(long claimId, Submit request, List<MultipartFile> files, CustomUserDetails user) {
        if (request == null || request.approvalId() == null || request.approvalId() < 1
                || request.clientRequestId() == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 4500) {
            throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        }
        long actor = actors.actor(user).getId();
        String reason = request.reason().strip();
        String reasonCode = request.reasonCode();
        if (reasonCode != null && (reasonCode.isBlank() || reasonCode.length() > 64))
            throw new AppException(ErrorCode.REPORT_REASON_INVALID);
        String payload = "P2P_APPEAL:" + claimId + ":" + request.approvalId() + ":";
        var prepared = images.prepare(payload + (reasonCode == null ? "" : reasonCode + ":") + reason, files);
        var tx = new TransactionTemplate(transactions);
        Submitted retry = tx.execute(status -> {
            Claim claim = claims.findById(claimId).orElseThrow(this::missing);
            Submitted existing = checkSubmission(claim, actor, request, prepared.hash());
            if (existing == null && reasonCode != null) reports.validateReason("CLAIM_APPEAL", reasonCode, reason);
            return existing;
        });
        if (retry != null) return retry;

        var uploaded = images.upload("reports/" + actor, prepared);
        boolean[] used = {false};
        try {
            Submitted result = tx.execute(status -> {
                long postId = claims.findPostId(claimId).orElseThrow(this::missing);
                Post post = posts.findForClaimUpdate(postId).orElseThrow(this::missing);
                entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
                routing.lockReceipt(post);
                Claim claim = claims.findForUpdate(claimId).orElseThrow(this::missing);
                entityManager.refresh(claim, LockModeType.PESSIMISTIC_WRITE);
                actors.actor(user);
                Submitted duplicate = checkSubmission(claim, actor, request, prepared.hash());
                if (duplicate != null) return duplicate;

                var report = reports.insertAppeal(actor, claimId, reason, reasonCode,
                        request.clientRequestId(), prepared.hash(), uploaded);
                jdbc.update("insert into p2p_claim_appeal(approval_id, appellant_claim_id, report_id) values (?, ?, ?)",
                        request.approvalId(), claimId, report.getId());
                used[0] = true;
                if (!claim.isOffice()) chat.notifyAppealChanged(report.getId());
                notifyAppeal(post, actor, report.getId());
                return new Submitted(report.getId());
            });
            if (!used[0]) images.cleanup(uploaded);
            return result;
        } catch (RuntimeException failure) {
            images.cleanup(uploaded);
            throw failure;
        }
    }

    private Submitted checkSubmission(Claim claim, long actor, Submit request, String hash) {
        if (claim.getClaimant() == null || claim.getClaimant().getId() != actor) throw missing();
        ExistingAppeal existing = existingAppeal(request.approvalId(), claim.getId());
        if (existing != null) {
            if (existing.requestId().equals(request.clientRequestId())) {
                if (!existing.hash().equals(hash)) throw new AppException(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD);
                return new Submitted(existing.id());
            }
            throw new AppException(ErrorCode.REPORT_DUPLICATE, "Bạn đã gửi tranh chấp cho lần chấp thuận này.");
        }
        Approval approval = activeApproval(claim.getPost().getId());
        if (approval == null || approval.id() != request.approvalId() || approval.selectedClaimId() == claim.getId()
                || !routing.matches(claim, RESERVED) || !"PENDING".equals(claim.getStatus())
                || (!claim.isOffice() && ClaimRepository.disputeEndsReview(claims.findLatestDisputeOutcome(claim.getId())))
                || !OffsetDateTime.now().isBefore(approval.deadline())) {
            throw new AppException(ErrorCode.CLAIM_STATE_INVALID, "Hồ sơ đã thay đổi hoặc hết thời hạn gửi tranh chấp.");
        }
        return null;
    }

    private Approval activeApproval(long postId) {
        return jdbc.query("""
                select a.approval_id, a.approved_claim_id, a.appeal_until,
                  coalesce(bool_or(r.status in ('OPEN','IN_REVIEW')), false) as pending,
                  coalesce(bool_or(r.status='RESOLVED' and not exists(select 1 from report_decision d where d.report_id=r.report_id and d.action='APPEAL_WITHDRAWN')), false) as upheld
                from p2p_claim_approval a join claim c on c.claim_id=a.approved_claim_id
                join post item on item.post_id=c.post_id and item.custody_mode=c.claim_type
                left join p2p_claim_appeal p on p.approval_id=a.approval_id
                left join report r on r.report_id=p.report_id
                where c.post_id=? and c.status='APPROVED' and a.closed_at is null and a.custody_mode=c.claim_type
                group by a.approval_id
                """, (rs, row) -> new Approval(rs.getLong("approval_id"), rs.getLong("approved_claim_id"),
                rs.getObject("appeal_until", OffsetDateTime.class), rs.getBoolean("pending"), rs.getBoolean("upheld")),
                postId).stream().findFirst().orElse(null);
    }

    private ExistingAppeal existingAppeal(long approvalId, long appellantClaimId) {
        return jdbc.query("""
                select r.report_id, r.status, r.client_request_id, r.request_hash
                from p2p_claim_appeal p join report r on r.report_id=p.report_id
                where p.approval_id=? and p.appellant_claim_id=?
                """, (rs, row) -> new ExistingAppeal(rs.getLong(1), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getString(4)), approvalId, appellantClaimId).stream().findFirst().orElse(null);
    }

    private void notifyAppeal(Post post, long actor, long reportId) {
        notifications.notifyRoles(List.of(AccountRole.MANAGER), actor, "REPORT_CREATED",
                "Có tranh chấp kết quả nhận đồ (#" + reportId + ")",
                "Đối chiếu hồ sơ rồi chọn giữ kết quả hoặc ghi kết luận cần xét lại người nhận.",
                "/admin/reports/" + reportId + "?kind=CLAIM_APPEAL", false);
        claims.findFirstByPost_IdAndStatusOrderByCreatedAtDescIdDesc(post.getId(), "APPROVED").ifPresent(selected -> {
            String title = "Bàn giao được tạm dừng để xử lý tranh chấp";
            String message = "Có tranh chấp cho bài «" + post.getTitle() + "». Hãy chờ kết quả trước khi giao đồ.";
            if (selected.isOffice()) {
                notifications.notifyRoles(List.of(AccountRole.APPROVER), actor, "CLAIM_APPEAL_FILED", title, message,
                    "/approver/office-claims/" + selected.getId(), false);
            } else {
                notifications.notifyUser(post.getAccount().getId(), "CLAIM_APPEAL_FILED", title, message, claimPath(selected), false);
            }
            notifications.notifyUser(selected.getClaimant().getId(), "CLAIM_APPEAL_FILED", title, message, claimPath(selected), false);
        });
    }

    private List<Claim> competingClaims(Claim selected) {
        var waiting = claims.findWaitingForReturn(selected.getPost().getId()).stream()
            .filter(claim -> claim.isOffice() == selected.isOffice() && claim.getClaimant() != null
                && !Objects.equals(claim.getId(), selected.getId())).toList();
        if (selected.isOffice() || waiting.isEmpty()) return waiting;
        var closed = new HashSet<Long>();
        for (Object[] outcome : claims.findLatestDisputeOutcomes(waiting.stream().map(Claim::getId).toList())) {
            if (ClaimRepository.disputeEndsReview((String) outcome[1])) {
                closed.add(((Number) outcome[0]).longValue());
            }
        }
        return waiting.stream().filter(claim -> !closed.contains(claim.getId())).toList();
    }

    private String claimPath(Claim claim) {
        return claim.isOffice() ? "/van-phong/claims/" + claim.getId()
            : "/bai-dang/" + claim.getPost().getId() + "/claims/" + claim.getId();
    }

    private AppException missing() {
        return new AppException(ErrorCode.CLAIM_NOT_FOUND);
    }
}
