package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.service.office.OfficeAccess.bad;

/** A single progress definition is shared by list labels and server-side filters. */
@Component @RequiredArgsConstructor
public class OfficeClaimProgress {
    private final JdbcTemplate jdbc;
    public record Progress(long id,String stage,OffsetDateTime scheduledAt) {}
    private static final Set<String> STAGES=Set.of("ALL","APPOINTMENT_REVIEW","REVIEW","NEEDS_INFO","EMAIL_UNSENT","AWAITING_REPLY","REPLY_EXPIRED","COUNTER_VERIFICATION","VERIFICATION_FAILED","READY_TO_HANDOVER","OFFICE_COMPLETION","COMPLETED","DISPUTED","REJECTED","CANCELLED","CLOSED","APPEAL_WAIT","APPEAL_REVIEW","APPEAL_REASSESS","SCHEDULE_REQUIRED");
    private static final String BASE="""
        select c.claim_id,c.post_id,c.submission_channel,c.status,c.claimant_id,c.claimant_name,c.created_at,p.title,a.scheduled_at,
        case when h.status='DISPUTED' then 'DISPUTED'
             when c.status='COMPLETED' or h.status='COMPLETED' then 'COMPLETED'
             when h.status='WAITING_CONFIRMATION' then 'OFFICE_COMPLETION'
             when c.status='PENDING' then 'REVIEW'
             when c.status='NEEDS_INFO' then 'NEEDS_INFO'
             when c.status<>'APPROVED' then c.status
             when approval.pending then 'APPEAL_REVIEW'
             when approval.upheld then 'APPEAL_REASSESS'
             when approval.appeal_until>now() then 'APPEAL_WAIT'
             when a.appointment_id is null then 'SCHEDULE_REQUIRED'
             when a.response_status in ('LEGACY','UNSENT') and a.scheduled_at<=now() then 'APPOINTMENT_REVIEW'
             when a.response_status in ('UNSENT','LEGACY') then 'EMAIL_UNSENT'
             when a.response_status='PENDING' and a.response_deadline<=now() then 'REPLY_EXPIRED'
             when a.response_status<>'CONFIRMED' then 'AWAITING_REPLY'
             when v.result='FAILED' then 'VERIFICATION_FAILED'
             when v.result='PASSED' then 'READY_TO_HANDOVER'
             else 'COUNTER_VERIFICATION' end as stage
        from claim c join post p on p.post_id=c.post_id
        left join lateral (select * from appointment where claim_id=c.claim_id and status='SCHEDULED' order by appointment_id desc limit 1) a on true
        left join lateral (select * from office_claim_verification where claim_id=c.claim_id and appointment_id=a.appointment_id order by verification_id desc limit 1) v on true
        left join office_handover h on h.claim_id=c.claim_id
        left join lateral (
          select round.appeal_until,bool_or(r.status in ('OPEN','IN_REVIEW')) as pending,
            bool_or(r.status='RESOLVED' and not exists (
              select 1 from report_decision d where d.report_id=r.report_id and d.action='APPEAL_WITHDRAWN'
            )) as upheld
          from p2p_claim_approval round left join p2p_claim_appeal appeal using(approval_id)
          left join report r on r.report_id=appeal.report_id
          where round.approved_claim_id=c.claim_id and round.closed_at is null and round.custody_mode='OFFICE'
          group by round.approval_id
        ) approval on c.status='APPROVED'
        where c.claim_type='OFFICE'
        """;
    public PageData<Progress> list(int page,String status,String stage,String q,Long claimantId) {
        return list(page,status,stage,q,claimantId,null,"ALL",null,false);
    }
    public PageData<Progress> list(int page,String status,String stage,String q,Long claimantId,Long postId,
                                  String channel,OffsetDateTime since,boolean oldest) {
        if(page<1 || !STAGES.contains(stage))throw bad("Bộ lọc tiến trình không hợp lệ.");
        String filter="""
             from (%s) x where (?='ALL' or status=?) and (?='ALL' or stage=?) and (?=0 or claimant_id=?)
               and (?=0 or post_id=?) and (?='ALL' or submission_channel=?)
               and (cast(? as timestamptz) is null or created_at>=?)
               and (title ilike ? or coalesce(claimant_name,'') ilike ? or cast(claim_id as text)=?)
            """.formatted(BASE);
        long actor=claimantId==null?0:claimantId;String keyword="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        long selected=postId==null?0:postId;
        Object[] args={status,status,stage,stage,actor,actor,selected,selected,channel,channel,since,since,keyword,keyword,q.replaceFirst("^#","")};
        long count=Objects.requireNonNull(jdbc.queryForObject("select count(*)"+filter,Long.class,args));
        List<Object> paged=new ArrayList<>(Arrays.asList(args));paged.add((page-1)*12L);
        String order=oldest?"asc":"desc";
        var rows=jdbc.query("select claim_id,stage,scheduled_at"+filter+" order by created_at "+order+",claim_id "+order+" limit 12 offset ?",
                (rs,n)->new Progress(rs.getLong("claim_id"),rs.getString("stage"),rs.getObject("scheduled_at",OffsetDateTime.class)),paged.toArray());
        return new PageData<>(rows,page,count,Math.max(1,(int)Math.ceil(count/12.0)));
    }

    public record Group(long postId,String title,String storageKey,String imageUrl,long claims,long people,long waiting,
                        OffsetDateTime latest,String stage) {}
    public record GroupPage(PageData<Group> page,Map<String,Long> counts) {}
    public GroupPage groups(int page,String status,String stage,String q,String channel,OffsetDateTime since,boolean oldest) {
        if (page<1 || !STAGES.contains(stage)) throw bad("Bộ lọc tiến trình không hợp lệ.");
        String keyword="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String grouped="""
            with scoped as (
              select x.*, ((?='ALL' or stage=?) and (?='ALL' or submission_channel=?)
                and (cast(? as timestamptz) is null or created_at>=?)
                and (title ilike ? or coalesce(claimant_name,'') ilike ? or cast(claim_id as text)=?)) as matches
              from (%s) x
            ), grouped as (
              select post_id,title,count(*) as claims,count(distinct coalesce(claimant_id,-claim_id)) as people,
                count(*) filter (where status='PENDING') as waiting,max(created_at) as latest,
                array_agg(distinct status) filter (where matches) as matching_statuses,
                (array_agg(stage order by case
                  when status='APPROVED' then 0 when status='COMPLETED' then 1
                  when status='PENDING' then 2 when status='NEEDS_INFO' then 3 else 4 end,
                  created_at desc,claim_id desc))[1] as stage
              from scoped group by post_id,title having bool_or(matches)
            )
            """.formatted(BASE);
        Object[] args={stage,stage,channel,channel,since,since,keyword,keyword,q.replaceFirst("^#","")};
        Map<String,Long> counts=new HashMap<>();
        jdbc.query(grouped+"""
            select value,count(*) from grouped cross join lateral unnest(array_append(matching_statuses,'ALL')) value
            group by value
            """,rs -> {counts.put(rs.getString(1),rs.getLong(2));},args);
        List<Object> paged=new ArrayList<>(Arrays.asList(args));
        paged.add(status); paged.add(status); paged.add((page-1)*12L);
        var rows=jdbc.query(grouped+"""
            select g.*,img.storage_key,img.image_url from grouped g
            left join lateral (select storage_key,image_url from post_image where post_id=g.post_id order by sort_order limit 1) img on true
            where (?='ALL' or ?=any(matching_statuses))
            order by latest %s,g.post_id %s limit 12 offset ?
            """.formatted(oldest?"asc":"desc",oldest?"asc":"desc"),
            (rs,n)->new Group(rs.getLong("post_id"),rs.getString("title"),rs.getString("storage_key"),rs.getString("image_url"),
                rs.getLong("claims"),rs.getLong("people"),rs.getLong("waiting"),rs.getObject("latest",OffsetDateTime.class),
                rs.getString("stage")),paged.toArray());
        long count=counts.getOrDefault(status,0L);
        return new GroupPage(new PageData<>(rows,page,count,Math.max(1,(int)Math.ceil(count/12.0))),counts);
    }
    public Progress of(long id) {
        return jdbc.query("select claim_id,stage,scheduled_at from ("+BASE+") x where claim_id=?",(rs,n)->new Progress(rs.getLong("claim_id"),rs.getString("stage"),rs.getObject("scheduled_at",OffsetDateTime.class)),id).stream().findFirst().orElseThrow(()->bad("Không tìm thấy yêu cầu."));
    }
}
