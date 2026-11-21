package com.example.base.service.handover;

import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.*;
import com.example.base.service.claim.ClaimRoutingPolicy;
import com.example.base.service.claim.P2pClaimAppealService;
import com.example.base.service.report.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor
public class P2pHandoverService {
    // DaiHV integration: Manager holds and resolved disputes must be respected by the existing P2P flow.
    private final ReportHoldGuard reportHolds;
    private final P2pClaimAppealService appeals;
    private final P2pHandoverRepository handovers;
    private final ClaimRepository claims;
    private final PostRepository posts;
    private final ClaimActivityRepository activities;
    private final ChatAccessPolicy actors;
    private final ClaimRoutingPolicy routing;
    private final ChatConversationService conversations;
    private final WorkflowImages images;
    private final ReportService reports;
    private final ReportReasonService reportReasons;
    private final ReportRepository reportRepository;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final com.example.base.service.notification.NotificationService notifications;
    public record RecordRequest(String note,Long expectedClaimVersion,UUID clientRequestId) {}
    public record ConfirmRequest(Long expectedVersion) {}
    public record IssueRequest(String reason,Long expectedVersion,UUID clientRequestId,String reasonCode,
                               Long messageId,String expectedPhase) {
        public IssueRequest(String reason,Long expectedVersion,UUID clientRequestId) {
            this(reason,expectedVersion,clientRequestId,null);
        }
        public IssueRequest(String reason,Long expectedVersion,UUID clientRequestId,String reasonCode) {
            this(reason,expectedVersion,clientRequestId,reasonCode,null,null);
        }
    }
    private static final Map<String, Set<String>> ISSUE_REASONS = Map.of(
        "BEFORE", Set.of("P2P_HANDOVER_NO_CONTACT", "P2P_HANDOVER_NOT_RETURNED",
            "P2P_HANDOVER_PAYMENT", "P2P_HANDOVER_THREAT", "HANDOVER_ISSUE_OTHER"),
        "DURING", Set.of("HANDOVER_NOT_RECEIVED", "HANDOVER_WRONG_RECIPIENT", "HANDOVER_WRONG_ITEM",
            "HANDOVER_MISSING_ITEMS", "HANDOVER_CONDITION", "HANDOVER_CONFIRMATION_ERROR",
            "P2P_HANDOVER_PAYMENT", "P2P_HANDOVER_THREAT", "HANDOVER_ISSUE_OTHER"),
        "AFTER", Set.of("HANDOVER_NOT_RECEIVED", "HANDOVER_WRONG_RECIPIENT", "HANDOVER_WRONG_ITEM",
            "HANDOVER_MISSING_ITEMS", "HANDOVER_CONDITION", "HANDOVER_CONFIRMATION_ERROR", "HANDOVER_ISSUE_OTHER")
    );
    public record Detail(Long id,long claimId,String status,String note,OffsetDateTime recordedAt,OffsetDateTime confirmedAt,
        long version,boolean canRecord,boolean canConfirm,boolean canReportIssue,List<ReportService.Evidence> evidence,ReportService.Detail issue,
        boolean heldForReview,P2pClaimAppealService.HandoverWindow appealWindow,boolean confirmedByManager,String issuePhase,
        String finderName,String claimantName,List<ReportReasonService.Reason> issueReasons,Long submittedReportId) {}
    @Transactional(readOnly=true)
    public Detail detail(long claimId,CustomUserDetails user) {
        long actor=actors.actor(user).getId(); Claim claim=claim(claimId); participant(claim,actor);
        return dto(claim,handovers.findByClaimId(claimId).orElse(null),actor);
    }
    public Detail record(long claimId,RecordRequest request,List<MultipartFile> files,CustomUserDetails user) {
        if(request==null||request.clientRequestId()==null||request.expectedClaimVersion()==null||request.note()!=null&&request.note().length()>2000)throw invalid();
        long actor=actors.actor(user).getId(); String note=request.note()==null||request.note().isBlank()?null:request.note().strip();
        var prepared=images.prepare("HANDOVER:"+claimId+":"+Objects.toString(note,""),files);
        var tx=new TransactionTemplate(transactions);
        Detail old=tx.execute(status->{Claim c=claim(claimId);participant(c,actor); Detail retry=recordRetry(c,actor,request.clientRequestId(),prepared.hash());if(retry==null)requireRecord(c,actor,request.expectedClaimVersion());return retry;});
        if(old!=null)return old;
        if (prepared.images().isEmpty()) throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID, "Vui lòng thêm ít nhất một ảnh bàn giao.");
        var uploaded=images.upload("handovers/"+claimId,prepared); boolean[] inserted={false};
        try {Detail result=tx.execute(status->{
            Claim c=lock(claimId); actors.actor(user);participant(c,actor);
            Detail retry=recordRetry(c,actor,request.clientRequestId(),prepared.hash());if(retry!=null)return retry;
            requireRecord(c,actor,request.expectedClaimVersion());
            P2pHandover handover=handovers.saveAndFlush(new P2pHandover(claimId,actor,note,request.clientRequestId(),prepared.hash()));inserted[0]=true;
            for(var image:uploaded)jdbc.update("insert into p2p_handover_evidence(handover_id,storage_key,original_name,mime_type,size_bytes,content_hash,sort_order) values(?,?,?,?,?,?,?)",handover.getId(),image.key(),image.name(),image.mime(),image.size(),image.hash(),image.order());
            c.touchWorkflow();activities.save(new ClaimActivity(c,em.find(Account.class,actor),"HANDOVER","APPROVED",note));claims.flush();conversations.notifyChanged(c);
            notifications.notifyUser(c.getClaimant().getId(),"HANDOVER_RECORDED",
                "Người nhặt đã lập biên bản bàn giao đồ (#" + c.getId() + ")",
                "Người đăng bài \"" + c.getPost().getTitle() + "\" đã ghi nhận bàn giao đồ. Vui lòng kiểm tra và bấm Xác nhận đã nhận đồ (hoặc Báo cáo sự cố).",
                "/bai-dang/" + c.getPost().getId() + "/claims/" + c.getId(),true);
            return dto(c,handover,actor);
        });if(!inserted[0])images.cleanup(uploaded);return result;}catch(RuntimeException ex){images.cleanup(uploaded);throw ex;}
    }
    private Detail recordRetry(Claim c,long actor,UUID request,String hash) {
        return handovers.findByRecordedByAndClientRequestId(actor,request).map(h->{
            if(h.getClaimId()!=c.getId()||!h.getRequestHash().equals(hash))throw new AppException(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD);
            return dto(c,h,actor);
        }).orElse(null);
    }
    @Transactional
    public Detail confirm(long claimId,ConfirmRequest request,CustomUserDetails user) {
        if(request==null||request.expectedVersion()==null)throw invalid();
        long actor=actors.actor(user).getId();Claim c=lock(claimId);participant(c,actor);
        if(c.getClaimant().getId()!=actor)throw new AppException(ErrorCode.FORBIDDEN);
        P2pHandover h=handovers.findByClaimId(claimId).orElseThrow(this::changed);em.refresh(h,LockModeType.PESSIMISTIC_WRITE);
        if("COMPLETED".equals(h.getStatus()))return dto(c,h,actor);
        reportHolds.requireAvailable(c.getPost().getId());
        if(!ready(c)||!"WAITING_CONFIRMATION".equals(h.getStatus())||h.getVersion()!=request.expectedVersion()||issueId(h.getId())!=null)throw changed();
        h.confirm(actor);c.completeReceipt();c.getPost().markReturnedFromP2pHandover();
        Account account=em.find(Account.class,actor);
        activities.save(new ClaimActivity(c,account,"CONFIRM_RECEIPT","APPROVED",null));
        for(Claim other:claims.findWaitingForReturn(c.getPost().getId())) {
            other.closeAfterReturn();activities.save(new ClaimActivity(other,account,"AUTO_CLOSE","PENDING","ITEM_RETURNED"));
            notifications.notifyUser(other.getClaimant().getId(), "CLAIM_CLOSED",
                "Món đồ đã được trao trả", "Yêu cầu của bạn cho bài «" + c.getPost().getTitle()
                    + "» đã đóng sau khi hoàn tất trao trả.",
                "/bai-dang/" + c.getPost().getId() + "/claims/" + other.getId(), false);
        }
        appeals.close(c);
        claims.flush();handovers.flush();conversations.notifyChanged(c);
        notifications.notifyUser(c.getPost().getAccount().getId(),"HANDOVER_COMPLETED",
            "Người nhận đã xác nhận nhận đồ thành công (#" + c.getId() + ")",
            "Người nhận đã xác nhận nhận đồ từ bài đăng \"" + c.getPost().getTitle() + "\". Quá trình trao trả P2P đã hoàn tất!",
            "/bai-dang/" + c.getPost().getId() + "/claims/" + c.getId(),true);
        return dto(c,h,actor);
    }
    public Detail issue(long claimId,IssueRequest request,List<MultipartFile> files,CustomUserDetails user) {
        boolean hasCode=request!=null&&request.reasonCode()!=null;
        int maxLen=hasCode?4800:5000;
        if(request==null||request.clientRequestId()==null||request.expectedVersion()==null||request.reason()==null||request.reason().isBlank()||request.reason().length()>maxLen)throw invalid();
        if(hasCode&&(request.reasonCode().isBlank()||request.reasonCode().length()>64))throw new AppException(ErrorCode.REPORT_REASON_INVALID);
        if (request.expectedPhase()!=null && (!hasCode || !ISSUE_REASONS.containsKey(request.expectedPhase()))
                || request.messageId()!=null && (request.expectedPhase()==null || request.messageId()<=0)) throw invalid();
        long actor=actors.actor(user).getId();String reason=request.reason().strip();
        String prepKey=hasCode?"ISSUE:"+claimId+":"+request.reasonCode()+":"+reason:"ISSUE:"+claimId+":"+reason;
        if (request.expectedPhase()!=null) {
            prepKey="ISSUE_CURRENT:"+claimId+":"+request.messageId()+":"+request.reasonCode()+":"+reason;
        }
        var prepared=images.prepare(prepKey,files);var tx=new TransactionTemplate(transactions);
        Detail old=tx.execute(status->{Claim c=claim(claimId);participant(c,actor);
            Detail retry=issueRetry(c,actor,request.clientRequestId(),prepared.hash());if(retry!=null)return retry;
            requireIssue(c,handovers.findByClaimId(claimId).orElse(null),request);
            messageSnapshot(c,request.messageId());
            if(hasCode)reports.validateReason("HANDOVER_ISSUE",request.reasonCode(),reason);return null;});
        if(old!=null)return old;
        var uploaded=images.upload("handover-reports/"+claimId,prepared);boolean[] inserted={false};
        try {Detail result=tx.execute(status->{
            Claim c=lock(claimId);actors.actor(user);participant(c,actor);
            P2pHandover h=handovers.findByClaimId(claimId).orElse(null);
            if(h!=null) em.refresh(h,LockModeType.PESSIMISTIC_WRITE);
            Detail retry=issueRetry(c,actor,request.clientRequestId(),prepared.hash());if(retry!=null)return retry;
            String phase=requireIssue(c,h,request);
            String messageSnapshot=messageSnapshot(c,request.messageId());
            Report report=hasCode
                ?reports.insert(actor,"HANDOVER_ISSUE","CLAIM",claimId,reason,request.reasonCode(),request.clientRequestId(),prepared.hash(),uploaded)
                :reports.insert(actor,"HANDOVER_ISSUE","CLAIM",claimId,reason,request.clientRequestId(),prepared.hash(),uploaded);
            inserted[0]=true;
            if (request.expectedPhase()!=null) {
                jdbc.update("""
                    insert into p2p_handover_issue(report_id,handover_id,phase,submission_status,message_snapshot)
                    values(?,?,?,?,cast(? as jsonb))
                    """,report.getId(),h==null?null:h.getId(),phase,
                    h==null?"NOT_RECORDED":h.getStatus(),messageSnapshot);
            } else if ("DURING".equals(phase)) {
                jdbc.update("insert into p2p_handover_issue(report_id,handover_id) values(?,?)",report.getId(),h.getId());
            } else {
                jdbc.update("insert into p2p_handover_issue(report_id,handover_id,phase) values(?,?,?)",
                        report.getId(),h.getId(),phase);
            }
            if ("DURING".equals(phase)) {
                h.reportIssue();
            }
            if (!"AFTER".equals(phase)) {
                c.touchWorkflow();
                activities.save(new ClaimActivity(c,em.find(Account.class,actor),"REPORT_ISSUE","APPROVED","Báo cáo sự cố #"+report.getId()));
            }
            claims.flush();handovers.flush();conversations.notifyChanged(c);
            long other=c.getClaimant().getId()==actor?c.getPost().getAccount().getId():c.getClaimant().getId();
            jdbc.update("insert into report_recipient(report_id,account_id) values(?,?) on conflict do nothing",
                    report.getId(),other);
            notifications.notifyUser(other,"HANDOVER_DISPUTED",
                "Có báo cáo sự cố bàn giao đồ (#" + report.getId() + ")",
                "Có báo cáo sự cố liên quan đến bài đăng \"" + c.getPost().getTitle() + "\". Lý do: " + reason,
                "/bao-cao/" + report.getId(),true);
            notifications.notifyRoles(List.of(AccountRole.MANAGER),actor,"REPORT_CREATED",
                "Báo cáo sự cố bàn giao P2P mới (#" + report.getId() + ")",
                "Có báo cáo sự cố bàn giao cho hồ sơ #" + claimId + ". Lý do: " + reason,
                "/admin/reports/" + report.getId() + "?kind=HANDOVER_ISSUE",false);
            return dto(c,h,actor,report.getId());
        });if(!inserted[0])images.cleanup(uploaded);return result;}catch(RuntimeException ex){images.cleanup(uploaded);throw ex;}
    }
    private String requireIssue(Claim c,P2pHandover h,IssueRequest request) {
        String phase=issuePhase(c,h);
        long version=h==null?c.getVersion():h.getVersion();
        if(phase==null || version!=request.expectedVersion() || activeIssue(c,h)!=null) throw changed();
        if (request.expectedPhase()==null && (h==null || reportHolds.held(c.getPost().getId()))) {
            throw new AppException(ErrorCode.HANDOVER_STATE_CHANGED,
                "Hãy tải lại hồ sơ để gửi báo cáo theo giai đoạn trao trả.");
        }
        if (request.expectedPhase()!=null && !phase.equals(request.expectedPhase())) throw changed();
        if (request.reasonCode()!=null && !ISSUE_REASONS.get(phase).contains(request.reasonCode())) {
            throw new AppException(ErrorCode.REPORT_REASON_INVALID);
        }
        return phase;
    }
    private String messageSnapshot(Claim claim,Long messageId) {
        if (messageId==null) return null;
        return jdbc.query("""
            select jsonb_build_object('id',m.message_id,'sender',a.full_name,'body',m.body,'createdAt',m.created_at)::text
            from chat_message m
            join chat_conversation c on c.conversation_id=m.conversation_id
            join accounts a on a.id=m.sender_id
            where m.message_id=? and c.latest_claim_id=? and m.body is not null
            """,(rs,row)->rs.getString(1),messageId,claim.getId())
            .stream().findFirst().orElseThrow(()->new AppException(ErrorCode.WORKFLOW_INPUT_INVALID,
                "Tin nhắn được chọn phải thuộc cuộc trò chuyện của yêu cầu này."));
    }
    private String issuePhase(Claim c,P2pHandover h) {
        if(h==null) return ready(c) ? "BEFORE" : null;
        if("WAITING_CONFIRMATION".equals(h.getStatus()) && ready(c)) return "DURING";
        if("COMPLETED".equals(h.getStatus()) && "COMPLETED".equals(c.getStatus()) && h.getConfirmedAt()!=null
                && OffsetDateTime.now().isBefore(h.getConfirmedAt().plusDays(7))) return "AFTER";
        return null;
    }
    private Detail issueRetry(Claim c,long actor,UUID request,String hash) {
        var old=reports.retry(actor,request,hash);
        if(old==null)return null;
        P2pHandover h=handovers.findByClaimId(c.getId()).orElse(null);
        if(!"HANDOVER_ISSUE".equals(old.kind())||old.targetId()!=c.getId()||!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from p2p_handover_issue where report_id=?)",Boolean.class,old.id())))
            throw new AppException(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD);
        return dto(c,h,actor,old.id());
    }
    private Long activeIssue(Claim c,P2pHandover h) {
        if(h!=null) return issueId(h.getId());
        return jdbc.query("select i.report_id from p2p_handover_issue i join report r using(report_id) where i.handover_id is null and r.claim_id=? and r.status in ('OPEN','IN_REVIEW') order by i.report_id desc",
                (rs,n)->rs.getLong(1),c.getId()).stream().findFirst().orElse(null);
    }
    private Long issueId(long handover) {
        return jdbc.query("select i.report_id from p2p_handover_issue i join report r using(report_id) where i.handover_id=? and r.status in ('OPEN','IN_REVIEW') order by i.report_id desc",(rs,n)->rs.getLong(1),handover).stream().findFirst().orElse(null);
    }
    private void requireRecord(Claim c,long actor,long version) {
        reportHolds.requireAvailable(c.getPost().getId());
        if(c.getPost().getAccount().getId()!=actor)throw new AppException(ErrorCode.FORBIDDEN);
        if(!ready(c)||c.getVersion()!=version||handovers.existsByClaimId(c.getId())||activeIssue(c,null)!=null)throw changed();
        var window = appeals.handoverWindow(c);
        if (window != null && window.blocksHandover()) {
            throw new AppException(ErrorCode.HANDOVER_STATE_CHANGED, "Bàn giao đang tạm dừng do tranh chấp. Vui lòng kiểm tra tiến trình nhận đồ.");
        }
    }
    private boolean ready(Claim c) {return !c.isOffice()&&"APPROVED".equals(c.getStatus())&&routing.matches(c,Set.of("RESERVED"));}
    private void participant(Claim c,long actor) {
        if(c.isOffice()||c.getClaimant().getId()!=actor&&c.getPost().getAccount().getId()!=actor)throw new AppException(ErrorCode.HANDOVER_NOT_FOUND);
    }
    private Claim claim(long id) {return claims.findById(id).orElseThrow(()->new AppException(ErrorCode.HANDOVER_NOT_FOUND));}
    private Claim lock(long id) {
        long postId=claims.findPostId(id).orElseThrow(()->new AppException(ErrorCode.HANDOVER_NOT_FOUND));
        Post p=posts.findForClaimUpdate(postId).orElseThrow(()->new AppException(ErrorCode.HANDOVER_NOT_FOUND));em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        Claim c=claims.findForUpdate(id).orElseThrow(()->new AppException(ErrorCode.HANDOVER_NOT_FOUND));em.refresh(c,LockModeType.PESSIMISTIC_WRITE);return c;
    }
    private Detail dto(Claim c,P2pHandover h,long actor) {
        return dto(c,h,actor,null);
    }
    private Detail dto(Claim c,P2pHandover h,long actor,Long submittedReportId) {
        Long reportId=activeIssue(c,h);
        ReportService.Detail issue=reportId==null?null:reports.detail(reportRepository.findById(reportId).orElseThrow(this::changed));
        boolean ready = ready(c);
        boolean held = reportHolds.held(c.getPost().getId());
        var appealWindow = h == null && ready ? appeals.handoverWindow(c) : null;
        boolean canRespond=h!=null&&"WAITING_CONFIRMATION".equals(h.getStatus())&&ready&&c.getClaimant().getId()==actor&&reportId==null&&!held;
        String phase=issuePhase(c,h);
        boolean canReport=phase!=null&&reportId==null;
        var issueReasons=canReport ? reportReasons.p2pHandoverOptions().reasons().stream()
            .filter(reason->ISSUE_REASONS.get(phase).contains(reason.code())).toList()
            : List.<ReportReasonService.Reason>of();
        var evidence=h==null?List.<ReportService.Evidence>of():jdbc.query("select * from p2p_handover_evidence where handover_id=? order by sort_order",(rs,n)->new ReportService.Evidence(rs.getLong("evidence_id"),images.read(rs.getString("storage_key")),rs.getString("original_name")),h.getId());
        return new Detail(h==null?null:h.getId(),c.getId(),h==null?"NOT_RECORDED":h.getStatus(),h==null?null:h.getNote(),h==null?null:h.getRecordedAt(),h==null?null:h.getConfirmedAt(),h==null?c.getVersion():h.getVersion(),
            h==null&&reportId==null&&ready&&c.getPost().getAccount().getId()==actor&&!held&&(appealWindow==null||!appealWindow.blocksHandover()),
            canRespond,canReport,evidence,issue,held,appealWindow,
            h!=null&&h.getConfirmedBy()!=null&&!Objects.equals(h.getConfirmedBy(),c.getClaimant().getId()),phase,
            c.getPost().getAccount().getFullName(),c.getClaimant().getFullName(),issueReasons,submittedReportId);
    }
    private AppException invalid(){return new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);}
    private AppException changed(){return new AppException(ErrorCode.HANDOVER_STATE_CHANGED);}
}
