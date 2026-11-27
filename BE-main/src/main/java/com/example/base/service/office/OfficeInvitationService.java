package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.ReviewResult;
import com.example.base.entity.Appointment;
import com.example.base.repository.AppointmentRepository;
import com.example.base.repository.ClaimRepository;
import com.example.base.service.claim.P2pClaimAppealService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeInvitationService {
    private final ClaimRepository claims;
    private final AppointmentRepository appointments;
    private final AppointmentEmailService email;
    private final PlatformTransactionManager transactions;
    private final OfficeAppointmentSchedule appointmentSchedule;
    private final P2pClaimAppealService appeals;

    public void validatePickupSchedule(OffsetDateTime scheduledAt, OffsetDateTime now) {
        appointmentSchedule.validatePickup(scheduledAt,now);
    }

    public boolean send(long claimId) {
        return "SENT".equals(sendWithResult(claimId).emailStatus());
    }

    private record Delivery(long appointmentId, AppointmentEmailService.Mail message) {}

    public ReviewResult sendWithResult(long claimId) {
        var transaction = new TransactionTemplate(transactions);
        Delivery delivery = transaction.execute(tx -> {
            var claim = claims.findById(claimId).filter(c -> c.isOffice() && "APPROVED".equals(c.getStatus()))
                .orElseThrow(() -> bad("Yêu cầu nhận tài sản chưa được chấp thuận hoặc đã kết thúc."));
            appeals.requireOfficeClearance(claim);
            Appointment appointment = appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(claimId,"SCHEDULED")
                .orElseThrow(() -> bad("Lịch hẹn không còn hiệu lực."));
            if (!OffsetDateTime.now().isBefore(OfficeAppointmentSchedule.verificationDeadline(appointment.getScheduledAt())))
                throw bad("Đã hết hạn đến quầy xác minh.");
            String recipient = claim.getClaimantEmail();
            if (recipient == null && claim.getClaimant() != null) recipient = claim.getClaimant().getEmail();
            var mail = new AppointmentEmailService.Mail(claimId,recipient,claim.getClaimantName(),claim.getPost().getTitle(),
                appointment.getScheduledAt(),appointment.getLocation(),appointment.getNote());
            return new Delivery(appointment.getId(),mail);
        });
        if (delivery.message().recipient() == null || !email.send(delivery.message()))
            return new ReviewResult(claimId,"APPROVED","FAILED");
        transaction.executeWithoutResult(tx -> appointments.markEmailSent(delivery.appointmentId(),OffsetDateTime.now()));
        return new ReviewResult(claimId,"APPROVED","SENT");
    }
}
