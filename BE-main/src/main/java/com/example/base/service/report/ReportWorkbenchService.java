package com.example.base.service.report;

import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.service.report.ReportContextService.id;
import com.example.base.entity.AccountRole;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ReportWorkbenchService {
    private final JdbcTemplate jdbc;
    private final OfficeAccess access;
    private final ReportReviewService reviews;
    private final ReportCaseService cases;
    private final ReportContextService context;
    private static final String TARGET="coalesce('P:'||r.post_id,'C:'||r.claim_id,'M:'||r.message_id,'U:'||r.reported_account_id)";
    private static final String STAGE="case when r.status in ('RESOLVED','REJECTED') then 'DONE' when r.assigned_to is null then 'NEW' when exists(select 1 from report_inquiry i where i.report_id=r.report_id and i.responded_at is null) then 'WAITING' when exists(select 1 from report_inquiry i where i.report_id=r.report_id and i.responded_at is not null and i.responded_at>coalesce((select max(a.created_at) from report_activity a where a.report_id=r.report_id and a.actor_id=r.assigned_to),r.created_at)) then 'UPDATED' else 'ACTIVE' end";

    @Transactional(readOnly=true)
    public Map<String,Object> queue(int page,String stage,String q,CustomUserDetails user) {
        long actor=access.require(user,AccountRole.MANAGER).getId();
        if(page<1 || page>100000 || q.length()>200 || !Set.of("NEW","MINE","WAITING","UPDATED","DONE","ALL").contains(stage)) throw bad("Bộ lọc không hợp lệ.");
        String cte="with work as (select r.*,"+TARGET+" as target_key,"+STAGE+" as stage,p.title as item_title from report r left join post p on p.post_id=coalesce(r.post_id,(select post_id from claim where claim_id=r.claim_id))), filtered as (select * from work where (?='ALL' or (?='MINE' and assigned_to=? and stage<>'DONE') or stage=?) and (description ilike ? or item_title ilike ? or report_id::text=?)) ";
        Object[] args={stage,stage,actor,stage,"%"+q.strip()+"%","%"+q.strip()+"%",q.strip()};
        long count=jdbc.queryForObject(cte+"select count(distinct target_key) from filtered",Long.class,args);
        var params=new ArrayList<Object>(Arrays.asList(args));params.add((page-1)*12);
        var rows=jdbc.queryForList(cte+"select target_key,max(item_title) as title,count(*) as report_count,min(created_at) as oldest_at,max(updated_at) as latest_at,bool_or(kind='HANDOVER_ISSUE') as dispute,array_agg(distinct reason_label_snapshot) as reasons from filtered group by target_key order by bool_or(kind='HANDOVER_ISSUE') desc,min(created_at),target_key limit 12 offset ?",params.toArray());
        rows.forEach(row->{try {row.put("reasons",Arrays.asList((Object[])((java.sql.Array)row.get("reasons")).getArray()));}catch(java.sql.SQLException e){throw new IllegalStateException(e);}});
        return Map.of("content",rows,"page",page,"totalElements",count,"totalPages",Math.max(1,(count+11)/12));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> group(String key,int page,CustomUserDetails user) {
        access.require(user,AccountRole.MANAGER);
        if(key==null || !key.matches("[PCMU]:[0-9]{1,18}") || page<1 || page>100000) throw bad("Đối tượng không hợp lệ.");
        long count=jdbc.queryForObject("select count(*) from report r where "+TARGET+"=?",Long.class,key);
        var rows=jdbc.queryForList("select r.*,a.full_name as reporter,m.full_name as assignee,"+STAGE+" as stage from report r join accounts a on a.id=r.reporter_id left join accounts m on m.id=r.assigned_to where "+TARGET+"=? order by case when r.status in ('OPEN','IN_REVIEW') then 0 else 1 end,r.created_at,r.report_id limit 20 offset ?",key,(page-1)*20);
        for(var row:rows) row.put("evidence",context.withUrls(jdbc.queryForList("select evidence_id,storage_key,original_name from report_evidence where report_id=? order by sort_order",row.get("report_id"))));
        Object source=rows.isEmpty()?Map.of():context.withUrls(context.context(rows.getFirst()));
        return Map.of("content",rows,"page",page,"totalElements",count,"totalPages",Math.max(1,(count+19)/20),"context",source);
    }

    public record Selected(@NotNull @Positive Long id,@NotNull @PositiveOrZero Long version) {}
    public record Batch(@NotEmpty @Size(max=20) List<@Valid Selected> reports,@NotNull @Valid ReportReviewService.Action decision) {}

    @Transactional
    public Map<String,Object> conclude(Batch request,CustomUserDetails user) {
        access.require(user,AccountRole.MANAGER);
        var selected=request.reports().stream().sorted(Comparator.comparing(Selected::id)).toList();
        if(selected.stream().map(Selected::id).distinct().count()!=selected.size()) throw bad("Danh sách báo cáo bị lặp.");
        var d=request.decision();
        boolean take="TAKE".equals(d.action());
        if(!take && (!"CONCLUDE".equals(d.action()) || !Set.of("VIOLATION","SETTLED","UNSUBSTANTIATED").contains(Objects.toString(d.outcome(),"")))) throw bad("Tranh chấp trao trả cần đối chiếu và kết luận riêng.");
        String target=null; Long caseId=null;
        for(var item:selected) {
            var row=reviews.lock(item.id());
            if(!reviews.open(row) || !Objects.equals(id(row,"version"),item.version())) throw bad("Có báo cáo đã thay đổi. Tải lại trước khi kết luận.");
            String key=jdbc.queryForObject("select "+TARGET+" from report r where report_id=?",String.class,item.id());
            if(target!=null&&!target.equals(key)) throw bad("Chỉ kết luận chung các báo cáo cùng đối tượng.");target=key;
            if(!take && !"GENERAL".equals(row.get("kind"))) throw bad("Vướng mắc trao trả cần được xem xét riêng.");
            if(row.get("case_id")!=null) {if(caseId!=null&&!caseId.equals(id(row,"case_id")))throw bad("Các báo cáo thuộc các vụ việc khác nhau.");caseId=id(row,"case_id");}
        }
        if(take) {
            for(var item:selected) reviews.act(item.id(),new ReportReviewService.Action(item.version(),"TAKE",null,null,null,false,null,false,null),user);
            return Map.of("completed",selected.size());
        }
        // The existing services keep authorization, conflict checks, decisions and notifications in one transaction.
        for(var item:selected) {
            var row=reviews.report(item.id());
            if(row.get("case_id")==null) {
                var linked=cases.link(item.id(),new ReportCaseService.Link(id(row,"version"),caseId,"Đối chiếu báo cáo "+target,d.basis()),user);
                @SuppressWarnings("unchecked") var c=(Map<String,Object>)linked.get("case");caseId=id(c,"case_id");
            }
        }
        Long measure=d.previousDecisionId();boolean first=true;
        for(var item:selected) {
            var row=reviews.report(item.id());
            reviews.processing(row,access.require(user,AccountRole.MANAGER),id(row,"version"));
            reviews.act(item.id(),new ReportReviewService.Action(id(row,"version"),"CONCLUDE",d.conclusion(),d.basis(),first?d.targetId():null,false,d.outcome(),first&&d.hidePost(),measure),user);
            if(first&&"VIOLATION".equals(d.outcome())) measure=jdbc.queryForObject("select max(decision_id) from report_decision where report_id=?",Long.class,item.id());
            first=false;
        }
        return Map.of("completed",selected.size(),"caseId",caseId);
    }
}
