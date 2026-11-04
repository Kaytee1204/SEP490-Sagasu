package com.example.base.service.office;

import com.example.base.dto.office.InvitationDtos.*;
import com.example.base.entity.*;
import com.example.base.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeInvitationService {
    private final ClaimRepository claims;
    private final PostRepository posts;
    private final AppointmentRepository appointments;
    private final ClaimActivityRepository activities;
    private final AppointmentEmailService email;
    private final PlatformTransactionManager transactions;
    private final EntityManager em;

    private Claim lock(long id) {
        Long postId=claims.findPostId(id).orElseThrow(()->bad("Không tìm thấy hồ sơ."));
        Post post=posts.findForClaimUpdate(postId).orElseThrow(); em.refresh(post,LockModeType.PESSIMISTIC_WRITE);
        Claim claim=claims.findForUpdate(id).orElseThrow(); em.refresh(claim,LockModeType.PESSIMISTIC_WRITE);
        if(!claim.isOffice()) throw invalid();
        return claim;
    }
    private RuntimeException invalid() { return bad("Liên kết không hợp lệ hoặc đã được thay thế. Hãy mở thư lịch hẹn mới nhất."); }
    private Appointment lookup(String hash) {
        Appointment a=appointments.findByResponseTokenHash(hash).orElseThrow(this::invalid);
        if(!a.getClaim().isOffice()) throw invalid(); return a;
    }
    // Called after approval commits, using the existing SMTP bean rather than OTP services.
    public boolean send(long claimId) {
        return Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(tx->{
            Claim c=lock(claimId);
            Appointment a=appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(claimId,"SCHEDULED").orElseThrow(()->bad("Lịch hẹn không còn hiệu lực."));
            em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
            if(!"APPROVED".equals(c.getStatus())) throw bad("Yêu cầu chưa được duyệt.");
            if("CONFIRMED".equals(a.getResponseStatus())) throw bad("Người nhận đã đồng ý lịch hẹn, không cần gửi lại lời mời.");
            OffsetDateTime now=OffsetDateTime.now();
            if(a.responseExpired(now)) { reject(c,a,"EXPIRED",now); return false; }
            if(a.getEmailSentAt()==null && !a.getScheduledAt().isAfter(now.plusHours(12).plusMinutes(1)))
                throw bad("Giờ hẹn quá gần để dành đủ 12 giờ phản hồi. Cần bố trí lại lịch trước khi gửi lời mời.");
            String token=InvitationTokens.create();
            String recipient=c.getClaimantEmail()!=null?c.getClaimantEmail():c.getClaimant().getEmail();
            var mail=new AppointmentEmailService.Mail(c.getId(),recipient,c.getClaimantName(),c.getPost().getTitle(),a.getScheduledAt(),a.getLocation(),a.getNote());
            if(!email.send(mail,token,a.getResponseDeadline())) return false;
            // Failed resend preserves the old link. Successful resend rotates it without extending the deadline.
            a.invitationSent(InvitationTokens.hash(token),OffsetDateTime.now()); appointments.flush(); return true;
        }));
    }
    @Transactional(readOnly=true)
    public View preview(String token) { return view(lookup(InvitationTokens.hash(token))); }
    public View respond(Response request) {
        String hash=InvitationTokens.hash(request.token());
        return new TransactionTemplate(transactions).execute(tx->{
            Appointment found=lookup(hash); Claim c=lock(found.getClaim().getId());
            em.refresh(found,LockModeType.PESSIMISTIC_WRITE);
            if(!hash.equals(found.getResponseTokenHash())) throw invalid();
            if(!java.util.Set.of("CONFIRM","DECLINE").contains(request.decision())) throw bad("Lựa chọn không hợp lệ.");
            OffsetDateTime now=OffsetDateTime.now();
            if(!"PENDING".equals(found.getResponseStatus()) || !"SCHEDULED".equals(found.getStatus()) || !"APPROVED".equals(c.getStatus())) return view(found);
            if(found.responseExpired(now)) reject(c,found,"EXPIRED",now);
            else if("CONFIRM".equals(request.decision())) {
                found.confirmInvitation(now);
                activities.save(new ClaimActivity(c,c.getClaimant()==null?c.getCreatedBy():c.getClaimant(),"INVITE_CONFIRM","APPROVED","Người nhận đồng ý lịch hẹn qua liên kết trong email."));
            } else reject(c,found,"DECLINED",now);
            return view(found);
        });
    }
    private View view(Appointment a) {
        String status=a.getResponseStatus();
        if((!"SCHEDULED".equals(a.getStatus()) || !"APPROVED".equals(a.getClaim().getStatus())) && !java.util.Set.of("DECLINED","EXPIRED").contains(status)) status="CANCELLED";
        else if(a.responseExpired(OffsetDateTime.now())) status="EXPIRED";
        return new View(a.getClaim().getPost().getTitle(),a.getScheduledAt(),a.getLocation(),a.getResponseDeadline(),status);
    }
    private void reject(Claim c,Appointment a,String response,OffsetDateTime now) {
        boolean expired="EXPIRED".equals(response);
        String reason=expired?"Không phản hồi lời mời nhận đồ trong thời hạn 12 giờ.":"Người nhận từ chối lịch hẹn qua email.";
        a.rejectInvitation(response,reason,now); c.review("REJECTED");
        if("RESERVED".equals(c.getPost().getStatus())) c.getPost().reopenAfterClaimCancellation();
        Account actor=expired||c.getClaimant()==null?a.getCreatedBy():c.getClaimant();
        activities.save(new ClaimActivity(c,actor,expired?"INVITE_EXPIRE":"INVITE_DECLINE","APPROVED",reason));
    }
    public void expireDue() {
        for(Long id:appointments.findExpiredOfficeClaims(OffsetDateTime.now(),PageRequest.of(0,100))) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(tx->{
                    Claim c=lock(id);
                    appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(id,"SCHEDULED").ifPresent(a->{
                        em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
                        OffsetDateTime now=OffsetDateTime.now();
                        if("APPROVED".equals(c.getStatus()) && a.responseExpired(now)) reject(c,a,"EXPIRED",now);
                    });
                });
            } catch(RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not expire office invitation for claim {} ({})",id,failure.getClass().getSimpleName());
            }
        }
    }
}
