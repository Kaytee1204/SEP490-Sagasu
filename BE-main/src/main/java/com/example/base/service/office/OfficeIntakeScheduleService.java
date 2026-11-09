package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.dto.office.InvitationDtos;
import com.example.base.entity.*;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.entity.AccountRole.APPROVER;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeIntakeScheduleService {
    private final OfficeAccess access;
    private final JdbcTemplate jdbc;
    private final PostRepository posts;
    private final EntityManager em;
    private final PlatformTransactionManager transactions;
    private final AppointmentEmailService email;
    private final com.example.base.service.notification.NotificationService notifications;

    public String schedule(long id,IntakeSchedule request,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            Account actor=access.require(user,APPROVER);Map<String,Object> t=lock(id);
            if(!"PENDING".equals(t.get("status")) || number(t,"version")!=request.expectedVersion()) throw bad("Yêu cầu đã thay đổi. Hãy tải lại.");
            if(number(t,"requested_by")==actor.getId())throw bad("Nhờ nhân viên khác sắp lịch tiếp nhận đồ của bạn.");
            if(!request.scheduledAt().isAfter(OffsetDateTime.now().plusHours(12).plusMinutes(1)))throw bad("Chọn lịch cách hiện tại hơn 12 giờ để người giao có thời gian phản hồi.");
            var previous=jdbc.queryForList("select * from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1",id).stream().findFirst().orElse(null);
            boolean needsReason=previous!=null && "SCHEDULED".equals(previous.get("status")) && !("PENDING".equals(previous.get("response_status")) && time(previous.get("response_deadline"))!=null && !OffsetDateTime.now().isBefore(time(previous.get("response_deadline"))));
            if(needsReason && (request.reason()==null||request.reason().isBlank()))throw bad("Nhập lý do văn phòng đổi lịch.");
            String reason=needsReason?request.reason().strip():"Sắp xếp lịch mới sau khi người giao đề nghị đổi lịch hoặc lịch cũ hết hạn.";
            jdbc.update("update office_intake_appointment set status='CANCELLED',cancellation_reason=?,response_token_hash=null where transfer_id=? and status='SCHEDULED'",reason,id);
            jdbc.update("update office_intake_appointment set response_token_hash=null where transfer_id=?",id);
            jdbc.update("insert into office_intake_appointment(transfer_id,scheduled_at,location,note,created_by) values(?,?,?,?,?)",id,request.scheduledAt(),request.location().strip(),request.note(),actor.getId());
            jdbc.update("update office_transfer set version=version+1 where transfer_id=?",id);
            notifications.notifyUser(number(t,"requested_by"),"INTAKE_APPOINTMENT_SCHEDULED",
                "Văn phòng đã xếp lịch hẹn mang đồ đến bàn giao",
                "Vui lòng kiểm tra và xác nhận lịch mang đồ đến văn phòng tại " + request.location().strip() + " trong vòng 12 giờ.",
                "/van-phong/chuyen-giao",false);
        });
        try { return send(id,user); }
        catch(RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Intake schedule saved but invitation failed for transfer {} ({})",id,failure.getClass().getSimpleName());
            return "FAILED";
        }
    }

    public String send(long id,CustomUserDetails user) {
        return new TransactionTemplate(transactions).execute(tx->{
            Account actor=access.require(user,APPROVER);Map<String,Object> t=lock(id);
            if(!"PENDING".equals(t.get("status")))throw bad("Yêu cầu này đã kết thúc.");
            if(number(t,"requested_by")==actor.getId())throw bad("Nhờ nhân viên khác gửi lịch tiếp nhận đồ của bạn.");
            var a=active(id);
            String response=(String)a.get("response_status");
            if("CONFIRMED".equals(response))throw bad("Người giao đã đồng ý lịch này.");
            OffsetDateTime now=OffsetDateTime.now(),deadline=time(a.get("response_deadline"));
            if(deadline!=null && !now.isBefore(deadline)) {expire(a);return "EXPIRED";}
            if(deadline==null && !time(a.get("scheduled_at")).isAfter(now.plusHours(12).plusMinutes(1)))throw bad("Lịch quá gần. Hãy đổi lịch trước khi gửi thư.");
            var person=jdbc.queryForMap("select a.email,a.full_name,p.title from office_transfer t join accounts a on a.id=t.requested_by join post p on p.post_id=t.post_id where transfer_id=?",id);
            if(!((String)person.get("email")).matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                jdbc.update("update office_intake_appointment set email_status='FAILED' where appointment_id=?",a.get("appointment_id"));return "FAILED";
            }
            String token=InvitationTokens.create();if(deadline==null)deadline=now.plusHours(12);
            boolean sent=email.sendIntake(new AppointmentEmailService.IntakeMail(id,(String)person.get("email"),(String)person.get("full_name"),(String)person.get("title"),time(a.get("scheduled_at")),(String)a.get("location"),(String)a.get("note")),token,deadline);
            if(sent)jdbc.update("update office_intake_appointment set email_status='SENT',emailed_at=now(),email_sent_at=coalesce(email_sent_at,?),response_deadline=?,response_status='PENDING',response_token_hash=? where appointment_id=?",now,deadline,InvitationTokens.hash(token),a.get("appointment_id"));
            else jdbc.update("update office_intake_appointment set email_status='FAILED' where appointment_id=?",a.get("appointment_id"));
            return sent?"SENT":"FAILED";
        });
    }

    @Transactional(readOnly=true)
    public InvitationDtos.View preview(String token) {return view(lookup(InvitationTokens.hash(token)));}

    @Transactional
    public InvitationDtos.View respondOnSite(long id,AppointmentResponse request,CustomUserDetails user) {
        Account actor=access.require(user);var t=lock(id);
        if(number(t,"requested_by")!=actor.getId())throw bad("Chỉ người gửi yêu cầu được phản hồi lịch hẹn này.");
        var a=jdbc.queryForList("select a.*,t.status as transfer_status,p.title from office_intake_appointment a join office_transfer t on t.transfer_id=a.transfer_id join post p on p.post_id=t.post_id where a.transfer_id=? order by a.appointment_id desc limit 1",id).stream().findFirst().orElseThrow(()->bad("Chưa có lịch hẹn."));
        if(number(a,"appointment_id")!=request.appointmentId())throw bad("Lịch hẹn đã thay đổi. Hãy tải lại để xem lịch mới.");
        applyResponse(t,a,request.decision(),request.reason());
        return view(jdbc.queryForMap("select a.*,t.status as transfer_status,p.title from office_intake_appointment a join office_transfer t on t.transfer_id=a.transfer_id join post p on p.post_id=t.post_id where a.appointment_id=?",request.appointmentId()));
    }

    @Transactional
    public InvitationDtos.View respond(InvitationDtos.Response request) {
        String hash=InvitationTokens.hash(request.token());var initial=lookup(hash);
        Map<String,Object> t=lock(number(initial,"transfer_id"));var a=lookup(hash);
        applyResponse(t,a,request.decision(),request.reason());
        return view(lookup(hash));
    }
    private void applyResponse(Map<String,Object> t,Map<String,Object> a,String decision,String reason) {
        if(!Set.of("CONFIRM","DECLINE").contains(decision))throw bad("Lựa chọn không hợp lệ.");
        if(!"PENDING".equals(t.get("status"))||!"SCHEDULED".equals(a.get("status"))||!"PENDING".equals(a.get("response_status")))return;
        if(!OffsetDateTime.now().isBefore(time(a.get("response_deadline"))))expire(a);
        else {
            boolean confirm="CONFIRM".equals(decision);
            if(!confirm && (reason==null||reason.isBlank()))throw bad("Cho văn phòng biết lý do bạn cần đổi lịch.");
            jdbc.update("update office_intake_appointment set response_status=?,responded_at=now(),status=?,cancellation_reason=? where appointment_id=?",
                    confirm?"CONFIRMED":"DECLINED",confirm?"SCHEDULED":"CANCELLED",confirm?null:reason.strip(),a.get("appointment_id"));
        }
        jdbc.update("update office_transfer set version=version+1 where transfer_id=?",t.get("transfer_id"));
    }
    private void expire(Map<String,Object> a) {jdbc.update("update office_intake_appointment set response_status='EXPIRED',responded_at=now(),status='CANCELLED',cancellation_reason='Chưa phản hồi lịch trong 12 giờ.' where appointment_id=?",a.get("appointment_id"));}
    private Map<String,Object> active(long id) {return jdbc.queryForList("select * from office_intake_appointment where transfer_id=? and status='SCHEDULED' order by appointment_id desc limit 1 for update",id).stream().findFirst().orElseThrow(()->bad("Cần hẹn lịch mới cho yêu cầu này."));}
    private Map<String,Object> lookup(String hash) {return jdbc.queryForList("select a.*,t.status as transfer_status,p.title from office_intake_appointment a join office_transfer t on t.transfer_id=a.transfer_id join post p on p.post_id=t.post_id where response_token_hash=?",hash).stream().findFirst().orElseThrow(()->bad("Liên kết đã hết hiệu lực. Hãy mở thư lịch hẹn mới nhất."));}
    private InvitationDtos.View view(Map<String,Object> a) {
        String status=(String)a.get("response_status");
        if(!"PENDING".equals(a.get("transfer_status")))status="CANCELLED";
        else if("PENDING".equals(status)&&time(a.get("response_deadline"))!=null&&!OffsetDateTime.now().isBefore(time(a.get("response_deadline"))))status="EXPIRED";
        else if(!"SCHEDULED".equals(a.get("status"))&&!Set.of("DECLINED","EXPIRED").contains(status))status="CANCELLED";
        return new InvitationDtos.View((String)a.get("title"),time(a.get("scheduled_at")),(String)a.get("location"),time(a.get("response_deadline")),status);
    }
    private Map<String,Object> lock(long id) {
        Long postId=jdbc.queryForList("select post_id from office_transfer where transfer_id=?",Long.class,id).stream().findFirst().orElseThrow(()->bad("Không tìm thấy yêu cầu."));
        Post p=posts.findForClaimUpdate(postId).orElseThrow();em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        if(p.getDeletedAt()!=null||!Set.of("OPEN","RESERVED").contains(p.getStatus()))throw bad("Đồ vật không còn có thể tiếp nhận. Hãy kiểm tra lại bài đăng.");
        return jdbc.queryForMap("select * from office_transfer where transfer_id=? for update",id);
    }
    private static long number(Map<String,Object> a,String key){return ((Number)a.get(key)).longValue();}
    private static OffsetDateTime time(Object value){if(value==null)return null;if(value instanceof OffsetDateTime t)return t;return ((java.sql.Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC);}
}
