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
    private static final Set<String> STAGES=Set.of("ALL","REVIEW","NEEDS_INFO","EMAIL_UNSENT","AWAITING_REPLY","REPLY_EXPIRED","COUNTER_VERIFICATION","VERIFICATION_FAILED","READY_TO_HANDOVER","OFFICE_COMPLETION","COMPLETED","DISPUTED","REJECTED","CANCELLED","CLOSED");
    private static final String BASE="""
        select c.claim_id,c.status,c.claimant_id,c.claimant_name,c.created_at,p.title,a.scheduled_at,
        case when h.status='DISPUTED' then 'DISPUTED'
             when c.status='COMPLETED' or h.status='COMPLETED' then 'COMPLETED'
             when h.status='WAITING_CONFIRMATION' then 'OFFICE_COMPLETION'
             when c.status='PENDING' then 'REVIEW'
             when c.status='NEEDS_INFO' then 'NEEDS_INFO'
             when c.status<>'APPROVED' then c.status
             when a.appointment_id is null or a.response_status in ('UNSENT','LEGACY') then 'EMAIL_UNSENT'
             when a.response_status='PENDING' and a.response_deadline<=now() then 'REPLY_EXPIRED'
             when a.response_status<>'CONFIRMED' then 'AWAITING_REPLY'
             when v.result='FAILED' then 'VERIFICATION_FAILED'
             when v.result='PASSED' then 'READY_TO_HANDOVER'
             else 'COUNTER_VERIFICATION' end as stage
        from claim c join post p on p.post_id=c.post_id
        left join lateral (select * from appointment where claim_id=c.claim_id and status='SCHEDULED' order by appointment_id desc limit 1) a on true
        left join lateral (select * from office_claim_verification where claim_id=c.claim_id and appointment_id=a.appointment_id order by verification_id desc limit 1) v on true
        left join office_handover h on h.claim_id=c.claim_id
        where c.claim_type='OFFICE'
        """;
    public PageData<Progress> list(int page,String status,String stage,String q,Long claimantId) {
        if(page<1 || !STAGES.contains(stage))throw bad("Bộ lọc tiến trình không hợp lệ.");
        String filter=" from ("+BASE+") x where (?='ALL' or status=?) and (?='ALL' or stage=?) and (?=0 or claimant_id=?) and (title ilike ? or coalesce(claimant_name,'') ilike ?)";
        long actor=claimantId==null?0:claimantId;String keyword="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        Object[] args={status,status,stage,stage,actor,actor,keyword,keyword};
        long count=Objects.requireNonNull(jdbc.queryForObject("select count(*)"+filter,Long.class,args));
        List<Object> paged=new ArrayList<>(Arrays.asList(args));paged.add((page-1)*12L);
        var rows=jdbc.query("select claim_id,stage,scheduled_at"+filter+" order by created_at desc,claim_id desc limit 12 offset ?",
                (rs,n)->new Progress(rs.getLong("claim_id"),rs.getString("stage"),rs.getObject("scheduled_at",OffsetDateTime.class)),paged.toArray());
        return new PageData<>(rows,page,count,Math.max(1,(int)Math.ceil(count/12.0)));
    }
    public Progress of(long id) {
        return jdbc.query("select claim_id,stage,scheduled_at from ("+BASE+") x where claim_id=?",(rs,n)->new Progress(rs.getLong("claim_id"),rs.getString("stage"),rs.getObject("scheduled_at",OffsetDateTime.class)),id).stream().findFirst().orElseThrow(()->bad("Không tìm thấy yêu cầu."));
    }
}
