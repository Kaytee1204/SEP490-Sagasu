package com.example.base.service.office;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AppointmentEmailService {
    private static final Logger log=LoggerFactory.getLogger(AppointmentEmailService.class);
    private final JavaMailSender sender;
    private final String from;
    public AppointmentEmailService(JavaMailSender sender,@Value("${spring.mail.username:}") String from) { this.sender=sender; this.from=from; }
    public record Mail(Long claimId,String recipient,String name,String title,OffsetDateTime time,String location,String note) {}
    public boolean send(Mail mail) {
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            if(!from.isBlank()) message.setFrom(from);
            message.setTo(mail.recipient());
            message.setSubject("[Sagasu] Lịch hẹn nhận đồ — hồ sơ #"+mail.claimId());
            String time=mail.time().atZoneSameInstant(ZoneId.of("Asia/Bangkok")).format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));
            message.setText("Xin chào "+mail.name()+",\n\nYêu cầu nhận đồ của bạn đã được văn phòng chấp thuận.\n"
                +"Hồ sơ: #"+mail.claimId()+"\nĐồ vật: "+mail.title()+"\nThời gian: "+time+" (UTC+7)\nĐịa điểm: "+mail.location()
                +"\n\nVui lòng mang giấy tờ và bằng chứng sở hữu để xác minh trực tiếp trước khi nhận đồ."
                +(mail.note()==null?"":"\nHướng dẫn thêm: "+mail.note())
                +"\n\nNếu không thể đến đúng lịch, vui lòng liên hệ văn phòng.\nSagasu");
            sender.send(message); return true;
        } catch(Exception failure) {
            log.warn("Appointment email delivery failed for claim {} ({})",mail.claimId(),failure.getClass().getSimpleName()); return false;
        }
    }
}
