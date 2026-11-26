package com.example.base.service.report;

import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.exception.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.chat.*;
import com.example.base.service.claim.ClaimRoutingPolicy;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor
public class ReportService {
    private static final Set<String> OFFICE_HANDOVER_REASONS = Set.of(
            "HANDOVER_WRONG_ITEM", "HANDOVER_MISSING_ITEMS", "HANDOVER_CONDITION", "HANDOVER_ISSUE_OTHER");

    private final ReportRepository reports;
    private final EntityManager em;
    private final ChatAccessPolicy actors;
    private final ClaimRoutingPolicy claimPolicy;
    private final JdbcTemplate jdbc;
    private final WorkflowImages images;
    private final PlatformTransactionManager transactions;
    private final ReportContextService context;
    private final ReportReasonService reasons;
    private final com.example.base.service.notification.NotificationService notifications;
    public record Submit(String targetType,Long targetId,String description,UUID clientRequestId,String reasonCode) {}
    public record Evidence(long id,String url,String name) {}
    public record Activity(String action,String status,String note,OffsetDateTime at) {}
    public record Detail(long id,String kind,String targetType,long targetId,String description,String status,
            String resolutionNote,OffsetDateTime createdAt,List<Evidence> evidence,List<Activity> activities,String targetLabel,
            String reasonCode,String reasonLabel,TargetAccount targetAccount,String reportType) {
        public Detail(long id,String kind,String targetType,long targetId,String description,String status,
                String resolutionNote,OffsetDateTime createdAt,List<Evidence> evidence,List<Activity> activities,String targetLabel) {
            this(id,kind,targetType,targetId,description,status,resolutionNote,createdAt,evidence,activities,targetLabel,null,null,null,
                    "HANDOVER_ISSUE".equals(kind)?kind:targetType);
        }
    }
    public record TargetAccount(Long id,String name) {}
    public record Page(List<Detail> content,int totalPages,long totalElements) {}

    public Detail create(Submit request,List<MultipartFile> files,CustomUserDetails user) {
        validate(request); long actor=actors.actor(user).getId();
        if (request.reasonCode().startsWith("P2P_HANDOVER_")) {
            throw new AppException(ErrorCode.REPORT_REASON_INVALID);
        }
        String text=Objects.toString(request.description(),"").strip();
        String payload=request.targetType()+":"+request.targetId()+":"+request.reasonCode()+":"+text;
        var prepared=images.prepare(payload,files);
        var tx=new TransactionTemplate(transactions);
        Detail retry=tx.execute(status->{
            Detail old=retry(actor,request.clientRequestId(),prepared.hash());
            if(old==null) submissionReason(checkTarget(request,actor),request.reasonCode(),text);
            return old;
        });
        if(retry!=null)return retry;
        var uploaded=images.upload("reports/"+actor,prepared);
        boolean[] inserted={false};
        try {
            Detail result=tx.execute(status->{
                // Serialize office incident submission with the recipient's confirmation.
                if ("CLAIM".equals(request.targetType())) jdbc.queryForList("""
                    select p.post_id from post p join claim c using(post_id)
                    where c.claim_id=? for update of p
                    """,Long.class,request.targetId());
                em.find(Account.class,actor,LockModeType.PESSIMISTIC_WRITE);
                actors.actor(user);
                Detail duplicate=retry(actor,request.clientRequestId(),prepared.hash());
                if(duplicate!=null)return duplicate;
                String group=checkTarget(request,actor);
                String column="POST".equals(request.targetType())?"post_id":"claim_id";
                if(jdbc.queryForObject("select count(*) from report where reporter_id=? and status in ('OPEN','IN_REVIEW') and "+column+"=?",Long.class,actor,request.targetId())>0)
                    throw new AppException(ErrorCode.REPORT_DUPLICATE);
                boolean officeHandover = "HANDOVER_ISSUE".equals(group);
                var reason = submissionReason(group,request.reasonCode(),text);
                Report created = persist(actor,officeHandover?"HANDOVER_ISSUE":"GENERAL",request.targetType(),request.targetId(),
                    text,reason,request.clientRequestId(),prepared.hash(),uploaded,officeHandover);
                inserted[0]=true;
                String notificationTitle = officeHandover ? "Sự cố bàn giao tại văn phòng" : "Báo cáo vi phạm mới";
                String notificationText = officeHandover ? "Người nhận đã gửi báo cáo về việc bàn giao tài sản của yêu cầu #" + request.targetId() + "."
                    : "Người dùng vừa gửi báo cáo đối với " + request.targetType() + " #" + request.targetId() + ": " + created.getDescription();
                notifications.notifyRoles(List.of(AccountRole.MANAGER),actor,"REPORT_CREATED",
                    notificationTitle + " (#" + created.getId() + ")",notificationText,
                    "/admin/reports/" + created.getId() + "?kind=" + created.getKind(),false);
                return detail(created);
            });
            // A concurrent identical request won after our upload. Delete only our unused objects.
            if(!inserted[0])images.cleanup(uploaded);
            return result;
        }catch(RuntimeException ex){images.cleanup(uploaded);throw ex;}
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Report insert(long actor,String kind,String type,long target,String text,UUID request,String hash,List<WorkflowImages.Image> uploaded) {
        // Compatibility for internal callers that supplied only free text before reason codes existed.
        String group="HANDOVER_ISSUE".equals(kind)?"HANDOVER_ISSUE":type;
        return insert(actor,kind,type,target,text,group+"_OTHER",request,hash,uploaded);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Report insert(long actor,String kind,String type,long target,String text,String reasonCode,UUID request,String hash,List<WorkflowImages.Image> uploaded) {
        String group="HANDOVER_ISSUE".equals(kind)?"HANDOVER_ISSUE":type;
        var reason=reasons.require(group,reasonCode,text);
        return persist(actor,kind,type,target,text,reason,request,hash,uploaded,"HANDOVER_ISSUE".equals(group));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Report insertAppeal(long actor,long claimId,String text,String reasonCode,UUID request,String hash,List<WorkflowImages.Image> uploaded) {
        var reason = reasonCode == null ? new ReportReasonService.Reason("APPEAL_LEGACY","Tranh chấp kết quả nhận đồ",true)
                : reasons.require("CLAIM_APPEAL",reasonCode,text);
        return persist(actor,"HANDOVER_ISSUE","CLAIM",claimId,text,reason,request,hash,uploaded,false);
    }
    private Report persist(long actor,String kind,String type,long target,String text,ReportReasonService.Reason reason,
                           UUID request,String hash,List<WorkflowImages.Image> uploaded,boolean linkOfficeHandover) {
        // Preserve a complete summary for existing consumers, while exposing the selected reason separately.
        String summary=reason.label()+(text.isBlank()?"":"\n\n"+text);
        if(summary.length()>5000)throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        Report report=new Report(actor,kind,type,target,summary,request,hash);
        report.selectReason(reason.code(),reason.label());
        report=reports.saveAndFlush(report);
        for(var image:uploaded)jdbc.update("insert into report_evidence(report_id,storage_key,original_name,mime_type,size_bytes,content_hash,sort_order) values(?,?,?,?,?,?,?)",report.getId(),image.key(),image.name(),image.mime(),image.size(),image.hash(),image.order());
        jdbc.update("insert into report_activity(report_id,actor_id,action,to_status) values(?,?,'CREATE','OPEN')",report.getId(),actor);
        if("CLAIM".equals(type) && linkOfficeHandover)
            jdbc.update("""
                insert into office_handover_issue(report_id,handover_id)
                select ?, h.handover_id from office_handover h join claim c using(claim_id)
                where h.claim_id=? and c.claim_type='OFFICE' and h.recipient_id=?
                  and h.status='COMPLETED' and h.completion_method='RECIPIENT'
                """,report.getId(),target,actor);
        context.capture(report.getId(),"SUBMISSION");
        return report;
    }
    public Detail retry(long actor,UUID request,String hash) {
        return reports.findByReporterIdAndClientRequestId(actor,request).map(report->{
            if(!report.getRequestHash().equals(hash))throw new AppException(ErrorCode.WORKFLOW_DUPLICATE_PAYLOAD);
            return detail(report);
        }).orElse(null);
    }
    @Transactional(readOnly=true)
    public Page mine(int page,String status,CustomUserDetails user) {
        return mine(page,status,"ALL",user);
    }
    @Transactional(readOnly=true)
    public Page mine(int page,String status,String type,CustomUserDetails user) {
        long actor=actors.actor(user).getId();
        if(page<1 || page>1000000 || !Set.of("ALL","OPEN","IN_REVIEW","RESOLVED","REJECTED").contains(Objects.toString(status,""))
                || !Set.of("ALL","POST","USER","CLAIM","MESSAGE","HANDOVER_ISSUE","CLAIM_APPEAL").contains(Objects.toString(type,"")))throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        var paging=PageRequest.of(page-1,12);
        var result=reports.findMine(actor,status,type,paging);
        return new Page(result.stream().map(this::detail).toList(),result.getTotalPages(),result.getTotalElements());
    }
    @Transactional(readOnly=true)
    public ReportReasonService.Options reasonOptions(String targetType,long targetId,boolean handoverIssue,CustomUserDetails user) {
        if ("CLAIM_APPEAL".equals(targetType)) {
            validateTarget("CLAIM",targetId);
            long actor=actors.actor(user).getId();
            Claim claim=em.find(Claim.class,targetId);
            if(claim==null || claim.getClaimant()==null || claim.getClaimant().getId()!=actor)
                throw new AppException(ErrorCode.REPORT_TARGET_UNAVAILABLE);
            return reasons.options("CLAIM_APPEAL");
        }
        validateTarget(targetType,targetId);
        long actor=actors.actor(user).getId();
        if (handoverIssue && "CLAIM".equals(targetType)) {
            Claim claim=em.find(Claim.class,targetId);
            if(claim!=null && !claim.isOffice() && claim.getClaimant()!=null
                && (claim.getClaimant().getId()==actor || claim.getPost().getAccount().getId()==actor))
                return reasons.options("HANDOVER_ISSUE");
        }
        if(handoverIssue && !"CLAIM".equals(targetType))throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        String group = checkTarget(new Submit(targetType,targetId,null,null,null),actor);
        var options = reasons.options(group);
        if (!"HANDOVER_ISSUE".equals(group)) return options;
        return new ReportReasonService.Options(group, options.reasons().stream()
                .filter(reason -> OFFICE_HANDOVER_REASONS.contains(reason.code())).toList());
    }
    public void validateReason(String group,String code,String description) {
        reasons.require(group,code,description);
    }
    private ReportReasonService.Reason submissionReason(String group,String code,String description) {
        if ("HANDOVER_ISSUE".equals(group) && !OFFICE_HANDOVER_REASONS.contains(code))
            throw new AppException(ErrorCode.REPORT_REASON_INVALID);
        return reasons.require(group,code,description);
    }
    @Transactional(readOnly=true)
    public Detail mineDetail(long id,CustomUserDetails user) {
        long actor=actors.actor(user).getId(); Report report=reports.findById(id).orElseThrow(()->new AppException(ErrorCode.REPORT_NOT_FOUND));
        if(report.getReporterId()!=actor)throw new AppException(ErrorCode.REPORT_NOT_FOUND);
        return detail(report);
    }
    public Detail detail(Report report) {
        var evidence=jdbc.query("select * from report_evidence where report_id=? order by sort_order",(rs,n)->new Evidence(rs.getLong("evidence_id"),images.read(rs.getString("storage_key")),rs.getString("original_name")),report.getId());
        var activities=jdbc.query("select action,to_status,public_note,created_at from report_activity where report_id=? and (public_note is not null or action='CREATE') order by activity_id",(rs,n)->new Activity(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class)),report.getId());
        return new Detail(report.getId(),report.getKind(),report.targetType(),report.targetId(),report.additionalDescription(),report.getStatus(),report.getResolutionNote(),report.getCreatedAt(),evidence,activities,targetLabel(report),report.getReasonCode(),report.getReasonLabelSnapshot(),targetAccount(report),reportType(report));
    }

    private String reportType(Report report) {
        if (!"HANDOVER_ISSUE".equals(report.getKind())) return report.targetType();
        boolean appeal = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from p2p_claim_appeal where report_id=?)", Boolean.class, report.getId()));
        return appeal ? "CLAIM_APPEAL" : "HANDOVER_ISSUE";
    }
    private TargetAccount targetAccount(Report report) {
        String sql = switch (report.targetType()) {
            case "POST" -> "select a.id,a.full_name from post p join accounts a on a.id=p.account_id where p.post_id=?";
            case "CLAIM" -> """
                select a.id,a.full_name from claim c join post p on p.post_id=c.post_id
                left join office_handover_issue i on i.report_id=%d
                left join office_handover h on h.handover_id=i.handover_id
                join accounts a on a.id=coalesce(h.recorded_by,
                    case when c.claimant_id=%d then p.account_id else c.claimant_id end)
                where c.claim_id=?
                """.formatted(report.getId(),report.getReporterId());
            case "MESSAGE" -> "select a.id,a.full_name from chat_message m join accounts a on a.id=m.sender_id where m.message_id=?";
            default -> "select id,full_name from accounts where id=?";
        };
        return jdbc.query(sql, (rs, row) -> new TargetAccount(rs.getLong(1),rs.getString(2)), report.targetId())
                .stream().findFirst().orElse(null);
    }
    private String targetLabel(Report report) {
        String sql = switch (report.targetType()) {
            case "POST" -> "select title from post where post_id=?";
            case "CLAIM" -> "select p.title from claim c join post p on p.post_id=c.post_id where c.claim_id=?";
            case "MESSAGE" -> "select p.title from chat_message m join chat_conversation c on c.conversation_id=m.conversation_id join post p on p.post_id=c.post_id where m.message_id=?";
            default -> "select full_name from accounts where id=?";
        };
        return jdbc.query(sql, (rs, row) -> rs.getString(1), report.targetId()).stream()
                .filter(Objects::nonNull).findFirst().orElse("Đối tượng #" + report.targetId());
    }

    private void validate(Submit r) {
        if(r==null || r.clientRequestId()==null || r.description()!=null && r.description().length()>4800)throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        validateTarget(r.targetType(),r.targetId());
        if(r.reasonCode()==null || r.reasonCode().isBlank() || r.reasonCode().length()>64)throw new AppException(ErrorCode.REPORT_REASON_INVALID);
    }
    private void validateTarget(String type,Long id) {
        if(id==null || id<1 || !Set.of("POST","CLAIM").contains(Objects.toString(type,"")))throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
    }

    public static OffsetDateTime officeHandoverReportDeadline(OffsetDateTime confirmedAt) {
        return confirmedAt.plusDays(7);
    }

    private String checkTarget(Submit r,long actor) {
        String group=switch(r.targetType()) {
            case "POST" -> {
                Post post=em.find(Post.class,r.targetId());
                if(post!=null && (post.getAccount().getId()==actor
                        || post.getCreatedBy()!=null && post.getCreatedBy().getId()==actor))
                    throw new AppException(ErrorCode.REPORT_SELF_POST_NOT_ALLOWED);
                yield post!=null && post.getDeletedAt()==null && "VISIBLE".equals(post.getModerationStatus()) ? "POST" : null;
            }
            case "CLAIM" -> {
                Claim claim=em.find(Claim.class,r.targetId());
                if (claim==null) yield null;
                if (claim.isOffice()) {
                    if (claim.getClaimant()==null || claim.getClaimant().getId()!=actor) yield null;
                    requireOfficeHandoverReportWindow(r.targetId(),actor);
                    yield "HANDOVER_ISSUE";
                }
                yield claimPolicy.reviewer(claim,em.find(Account.class,actor)) ? "CLAIM" : null;
            }
            default -> null;
        };
        if(group==null)throw new AppException(ErrorCode.REPORT_TARGET_UNAVAILABLE);
        return group;
    }

    private void requireOfficeHandoverReportWindow(long claimId,long recipientId) {
        OffsetDateTime confirmed = jdbc.query("""
            select confirmed_at from office_handover
            where claim_id=? and recipient_id=? and status='COMPLETED'
              and completion_method='RECIPIENT' and confirmed_at<=clock_timestamp()
            """,(row,index)->row.getObject("confirmed_at",OffsetDateTime.class),claimId,recipientId)
                .stream().findFirst().orElseThrow(() -> new AppException(ErrorCode.REPORT_TARGET_UNAVAILABLE,
                        "Quý vị có thể gửi phản ánh sau khi xác nhận biên bản bàn giao."));
        if (!OffsetDateTime.now().isBefore(officeHandoverReportDeadline(confirmed)))
            throw new AppException(ErrorCode.REPORT_HANDOVER_EXPIRED);
    }
}
