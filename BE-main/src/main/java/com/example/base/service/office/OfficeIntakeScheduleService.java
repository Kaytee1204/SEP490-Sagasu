package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.entity.*;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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
    private final OfficeAppointmentSchedule appointmentSchedule;

    public String schedule(long id,IntakeSchedule request,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            Account actor=access.require(user,APPROVER);Map<String,Object> t=lock(id);
            if(!"PENDING".equals(t.get("status")) || number(t,"version")!=request.expectedVersion()) throw bad("Yêu cầu đã thay đổi. Hãy tải lại.");
            if(number(t,"requested_by")==actor.getId())throw bad("Nhờ nhân viên khác sắp lịch tiếp nhận đồ của bạn.");
            appointmentSchedule.validateIntake(request.scheduledAt(), OffsetDateTime.now());
            var previous=active(id);
            if (previous!=null) {
                requireBeforeDeadline(previous);
                if (request.reason()==null || request.reason().isBlank()) throw bad("Nhập lý do văn phòng đổi lịch.");
                jdbc.update("update office_intake_appointment set status='CANCELLED',cancellation_reason=?,response_token_hash=null where appointment_id=?",
                    request.reason().strip(),number(previous,"appointment_id"));
            }
            OffsetDateTime deadline=request.scheduledAt().plusHours(72);
            jdbc.update("""
                insert into office_intake_appointment(transfer_id,scheduled_at,location,note,created_by,intake_deadline)
                values(?,?,?,?,?,?)
                """,id,request.scheduledAt(),request.location().strip(),request.note(),actor.getId(),deadline);
            jdbc.update("update office_transfer set version=version+1 where transfer_id=?",id);
            notifications.notifyUser(number(t,"requested_by"),"INTAKE_APPOINTMENT_SCHEDULED",
                "Văn phòng đã hẹn ngày mang đồ đến",
                "Giờ hẹn " + AppointmentEmailService.date(request.scheduledAt()) + ". Hạn tiếp nhận " + AppointmentEmailService.date(deadline)
                    + " (UTC+7). Nếu văn phòng chưa nhận đồ trước hạn, yêu cầu sẽ tự động bị hủy.",
                "/van-phong/chuyen-giao/"+id,false);
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
            if (a==null) throw bad("Cần hẹn lịch mới cho yêu cầu này.");
            requireBeforeDeadline(a);
            OffsetDateTime deadline=time(a.get("intake_deadline"));
            if (deadline==null) throw bad("Lịch cũ chưa có hạn tiếp nhận. Hãy xếp lịch mới trước khi gửi email.");
            var person=jdbc.queryForMap("select a.email,a.full_name,p.title from office_transfer t join accounts a on a.id=t.requested_by join post p on p.post_id=t.post_id where t.transfer_id=?",id);
            boolean sent=email.sendIntake(new AppointmentEmailService.IntakeMail(id,(String)person.get("email"),
                (String)person.get("full_name"),(String)person.get("title"),time(a.get("scheduled_at")),
                (String)a.get("location"),(String)a.get("note")),deadline);
            if(sent) jdbc.update("update office_intake_appointment set email_status='SENT',emailed_at=now(),email_sent_at=coalesce(email_sent_at,now()) where appointment_id=?",a.get("appointment_id"));
            else jdbc.update("update office_intake_appointment set email_status='FAILED' where appointment_id=?",a.get("appointment_id"));
            return sent?"SENT":"FAILED";
        });
    }

    private void requireBeforeDeadline(Map<String,Object> appointment) {
        OffsetDateTime deadline=time(appointment.get("intake_deadline"));
        if(deadline!=null && !OffsetDateTime.now().isBefore(deadline)) throw bad("Yêu cầu đã quá hạn tiếp nhận. Hãy tải lại.");
    }

    private Map<String,Object> active(long id) {
        return jdbc.queryForList("select * from office_intake_appointment where transfer_id=? and status='SCHEDULED' for update",id)
            .stream().findFirst().orElse(null);
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
