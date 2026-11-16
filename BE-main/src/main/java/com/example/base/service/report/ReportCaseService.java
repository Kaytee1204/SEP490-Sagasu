package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;
import com.example.base.entity.AccountRole;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
import com.example.base.service.notification.NotificationService;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cases coordinate review; reports, evidence, inquiries and conclusions remain separate. */
@Service @RequiredArgsConstructor
public class ReportCaseService {
    private final JdbcTemplate jdbc;
    private final OfficeAccess access;
    private final ReportReviewService reviews;
    private final ReportContextService context;
    private final NotificationService notifications;

    public record Link(@NotNull @PositiveOrZero Long version, @Positive Long caseId,
        @Size(max=200) String title, @NotBlank @Size(max=2000) String reason) {}
    public record Unlink(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=2000) String reason) {}

    @Transactional(readOnly=true)
    public Map<String,Object> detail(long reportId, CustomUserDetails user) {
        return detail(reportId,1,user);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> detail(long reportId, int page, CustomUserDetails user) {
        if(page<1 || page>100000) throw bad("Trang không hợp lệ.");
        var actor = access.require(user, AccountRole.MANAGER);
        var report = reviews.report(reportId);
        @SuppressWarnings("unchecked")
        var post = (Map<String,Object>) context.context(report).getOrDefault("post",Map.of());
        var caseRow = context.one("select c.*,a.full_name as owner from report_case c join accounts a on a.id=c.owner_id where c.case_id=?", report.get("case_id"));
        var out = new LinkedHashMap<String,Object>();
        out.put("case", caseRow);
        long count=caseRow.isEmpty()?0:jdbc.queryForObject("select count(*) from report where case_id=?",Long.class,caseRow.get("case_id"));
        long pending=caseRow.isEmpty()?0:jdbc.queryForObject("select count(*) from report where case_id=? and status in ('OPEN','IN_REVIEW')",Long.class,caseRow.get("case_id"));
        out.put("totalMembers",count); out.put("pendingMembers",pending);
        int pages=(int)Math.max(1,(count+19)/20), currentPage=Math.min(page,pages);
        out.put("page",currentPage); out.put("totalPages",pages);
        out.put("members", caseRow.isEmpty() ? List.of() : jdbc.queryForList(
            "select report_id,reason_label_snapshot,description,status,created_at,resolution_note from report where case_id=? order by case when status in ('OPEN','IN_REVIEW') then 0 else 1 end,created_at,report_id limit 20 offset ?", caseRow.get("case_id"),(currentPage-1)*20));
        out.put("canLink", report.get("case_id")==null && reviews.open(report) && !reviews.participantsFor(report).contains(actor.getId()) &&
            (report.get("assigned_to")==null || Objects.equals(id(report,"assigned_to"),actor.getId())));
        out.put("canUnlink", !caseRow.isEmpty() && reviews.open(report) && !reviews.participantsFor(report).contains(actor.getId()) && Objects.equals(id(caseRow,"owner_id"),actor.getId()));
        // Candidates share a target or the same underlying post, but never become members automatically.
        out.put("candidates", jdbc.queryForList("""
            select r.report_id,r.case_id,r.reason_label_snapshot,r.description,r.status,r.created_at,
              c.title as case_title,a.full_name as owner,c.owner_id
            from report r left join report_case c on c.case_id=r.case_id left join accounts a on a.id=c.owner_id
            where r.report_id<>? and (
              (r.post_id is not null and r.post_id=?) or (r.claim_id is not null and r.claim_id=?) or
              (r.message_id is not null and r.message_id=?) or (r.reported_account_id is not null and r.reported_account_id=?) or
              coalesce(r.post_id,(select post_id from claim where claim_id=r.claim_id),
                (select c.post_id from chat_message m join chat_conversation c on c.conversation_id=m.conversation_id where m.message_id=r.message_id)) = ?)
            order by r.created_at desc,r.report_id desc limit 50
            """,reportId,report.get("post_id"),report.get("claim_id"),report.get("message_id"),report.get("reported_account_id"),post.get("post_id")));
        return out;
    }

    @Transactional
    public Map<String,Object> link(long reportId, Link request, CustomUserDetails user) {
        var actor = access.require(user,AccountRole.MANAGER);
        // Lock case first, then this report. Other review actions only lock report/post, never case.
        Map<String,Object> existing = request.caseId()==null ? Map.of() : context.one("select * from report_case where case_id=? for update",request.caseId());
        if(request.caseId()!=null && (existing.isEmpty() || !Objects.equals(id(existing,"owner_id"),actor.getId())))
            throw bad("Chỉ người phụ trách vụ việc được thêm báo cáo. Nhờ người phụ trách liên kết báo cáo này.");
        var report=reviews.lock(reportId);
        if(!reviews.open(report) || !Objects.equals(id(report,"version"),request.version()) || report.get("case_id")!=null)
            throw bad("Báo cáo đã thay đổi hoặc đã thuộc một vụ việc. Hãy tải lại.");
        if(reviews.participantsFor(report).contains(actor.getId())) throw bad("Bạn có liên quan đến báo cáo này; cần quản lý khác xử lý.");
        if(report.get("assigned_to")!=null && !Objects.equals(id(report,"assigned_to"),actor.getId())) throw bad("Báo cáo đã có quản lý khác phụ trách.");
        Long caseId=request.caseId();
        if(caseId==null) {
            if(request.title()==null || request.title().isBlank()) throw bad("Nhập tên vụ việc để phân biệt nội dung cần xử lý.");
            caseId=jdbc.queryForObject("insert into report_case(title,owner_id) values(?,?) returning case_id",Long.class,request.title().strip(),actor.getId());
        }
        jdbc.update("update report set case_id=?,assigned_to=?,assigned_at=coalesce(assigned_at,now()),status='IN_REVIEW',version=version+1,updated_at=now() where report_id=?",caseId,actor.getId(),reportId);
        reviews.history(reportId,actor.getId(),"CASE_LINK",(String)report.get("status"),"IN_REVIEW",null,"Liên kết vụ việc #"+caseId+": "+request.reason().strip());
        notifications.notifyUser(actor.getId(),"REPORT_CASE_UPDATED","Vụ việc #"+caseId+" có báo cáo mới","Đã liên kết báo cáo #"+reportId+". Các báo cáo vẫn cần kết luận riêng.","/admin/reports?id="+reportId,false);
        return detail(reportId,user);
    }

    @Transactional
    public Map<String,Object> unlink(long reportId, Unlink request, CustomUserDetails user) {
        var actor=access.require(user,AccountRole.MANAGER);
        var report=reviews.lock(reportId);
        reviews.processing(report,actor,request.version());
        if(report.get("case_id")==null) throw bad("Báo cáo chưa thuộc vụ việc nào.");
        jdbc.update("update report set case_id=null,version=version+1,updated_at=now() where report_id=?",reportId);
        reviews.history(reportId,actor.getId(),"CASE_UNLINK",(String)report.get("status"),(String)report.get("status"),null,"Tách khỏi vụ việc #"+report.get("case_id")+": "+request.reason().strip());
        return detail(reportId,user);
    }
}
