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
    private final ReportRepository reports;
    private final EntityManager em;
    private final ChatAccessPolicy actors;
    private final ChatService chats;
    private final ClaimRoutingPolicy claimPolicy;
    private final JdbcTemplate jdbc;
    private final WorkflowImages images;
    private final PlatformTransactionManager transactions;
    private final com.example.base.service.notification.NotificationService notifications;
    public record Submit(String targetType,Long targetId,String description,UUID clientRequestId) {}
    public record Evidence(long id,String url,String name) {}
    public record Activity(String action,String status,String note,OffsetDateTime at) {}
    public record Detail(long id,String kind,String targetType,long targetId,String description,String status,
            String resolutionNote,OffsetDateTime createdAt,List<Evidence> evidence,List<Activity> activities) {}
    public record Page(List<Detail> content,int totalPages,long totalElements) {}

    public Detail create(Submit request,List<MultipartFile> files,CustomUserDetails user) {
        validate(request); long actor=actors.actor(user).getId();
        String text=request.description().strip(); String payload=request.targetType()+":"+request.targetId()+":"+text;
        var prepared=images.prepare(payload,files);
        var tx=new TransactionTemplate(transactions);
        Detail retry=tx.execute(status->{Detail old=retry(actor,request.clientRequestId(),prepared.hash()); if(old==null)checkTarget(request,actor); return old;});
        if(retry!=null)return retry;
        var uploaded=images.upload("reports/"+actor,prepared);
        boolean[] inserted={false};
        try {
            Detail result=tx.execute(status->{
                em.find(Account.class,actor,LockModeType.PESSIMISTIC_WRITE);
                actors.actor(user); checkTarget(request,actor);
                Detail duplicate=retry(actor,request.clientRequestId(),prepared.hash());
                if(duplicate!=null)return duplicate;
                String column=switch(request.targetType()){case "POST"->"post_id";case "CLAIM"->"claim_id";case "MESSAGE"->"message_id";default->"reported_account_id";};
                if(jdbc.queryForObject("select count(*) from report where reporter_id=? and kind='GENERAL' and status in ('OPEN','IN_REVIEW') and "+column+"=?",Long.class,actor,request.targetId())>0)
                    throw new AppException(ErrorCode.REPORT_DUPLICATE);
                inserted[0]=true;
                Report created = insert(actor,"GENERAL",request.targetType(),request.targetId(),text,request.clientRequestId(),prepared.hash(),uploaded);
                notifications.notifyRoles(List.of(AccountRole.ADMIN, AccountRole.MANAGER),actor,"REPORT_CREATED",
                    "Báo cáo vi phạm mới (#" + created.getId() + ")",
                    "Người dùng vừa gửi báo cáo đối với " + request.targetType() + " #" + request.targetId() + ": " + text,
                    "/admin/reports",false);
                return detail(created);
            });
            // A concurrent identical request won after our upload. Delete only our unused objects.
            if(!inserted[0])images.cleanup(uploaded);
            return result;
        }catch(RuntimeException ex){images.cleanup(uploaded);throw ex;}
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Report insert(long actor,String kind,String type,long target,String text,UUID request,String hash,List<WorkflowImages.Image> uploaded) {
        Report report=reports.saveAndFlush(new Report(actor,kind,type,target,text,request,hash));
        for(var image:uploaded)jdbc.update("insert into report_evidence(report_id,storage_key,original_name,mime_type,size_bytes,content_hash,sort_order) values(?,?,?,?,?,?,?)",report.getId(),image.key(),image.name(),image.mime(),image.size(),image.hash(),image.order());
        jdbc.update("insert into report_activity(report_id,actor_id,action,to_status) values(?,?,'CREATE','OPEN')",report.getId(),actor);
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
        long actor=actors.actor(user).getId();
        if(page<1 || page>1000000 || !Set.of("ALL","OPEN","IN_REVIEW","RESOLVED","REJECTED").contains(status))throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
        var paging=PageRequest.of(page-1,12);
        var result="ALL".equals(status)?reports.findByReporterIdOrderByCreatedAtDescIdDesc(actor,paging):reports.findByReporterIdAndStatusOrderByCreatedAtDescIdDesc(actor,status,paging);
        return new Page(result.stream().map(this::detail).toList(),result.getTotalPages(),result.getTotalElements());
    }
    @Transactional(readOnly=true)
    public Detail mineDetail(long id,CustomUserDetails user) {
        long actor=actors.actor(user).getId(); Report report=reports.findById(id).orElseThrow(()->new AppException(ErrorCode.REPORT_NOT_FOUND));
        if(report.getReporterId()!=actor)throw new AppException(ErrorCode.REPORT_NOT_FOUND);
        return detail(report);
    }
    public Detail detail(Report report) {
        var evidence=jdbc.query("select * from report_evidence where report_id=? order by sort_order",(rs,n)->new Evidence(rs.getLong("evidence_id"),images.read(rs.getString("storage_key")),rs.getString("original_name")),report.getId());
        var activities=jdbc.query("select action,to_status,public_note,created_at from report_activity where report_id=? order by activity_id",(rs,n)->new Activity(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class)),report.getId());
        return new Detail(report.getId(),report.getKind(),report.targetType(),report.targetId(),report.getDescription(),report.getStatus(),report.getResolutionNote(),report.getCreatedAt(),evidence,activities);
    }
    private void validate(Submit r) {
        if(r==null || r.clientRequestId()==null || r.targetId()==null || r.targetId()<1 || !Set.of("POST","CLAIM","MESSAGE","USER").contains(Objects.toString(r.targetType(),"")) || r.description()==null || r.description().isBlank() || r.description().length()>5000)throw new AppException(ErrorCode.WORKFLOW_INPUT_INVALID);
    }
    private void checkTarget(Submit r,long actor) {
        boolean allowed=switch(r.targetType()) {
            case "POST" -> {Post p=em.find(Post.class,r.targetId()); yield p!=null && p.getDeletedAt()==null && ("VISIBLE".equals(p.getModerationStatus()) || p.getAccount().getId()==actor);}
            case "CLAIM" -> {Claim c=em.find(Claim.class,r.targetId()); yield c!=null && (c.getClaimant().getId()==actor || claimPolicy.reviewer(c,em.find(Account.class,actor)));}
            case "MESSAGE" -> {ChatMessage m=em.find(ChatMessage.class,r.targetId()); if(m!=null)chats.requireMember(m.getConversationId(),actor); yield m!=null;}
            case "USER" -> r.targetId()!=actor && em.find(Account.class,r.targetId())!=null && jdbc.queryForObject("select count(*) from post where account_id=? and deleted_at is null and moderation_status='VISIBLE'",Long.class,r.targetId())>0
                || r.targetId()!=actor && jdbc.queryForObject("select count(*) from chat_participant a join chat_participant b using(conversation_id) where a.account_id=? and b.account_id=?",Long.class,actor,r.targetId())>0;
            default -> false;
        };
        if(!allowed)throw new AppException(ErrorCode.REPORT_TARGET_UNAVAILABLE);
    }
}
