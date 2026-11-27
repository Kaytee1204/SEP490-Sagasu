package com.example.base.service.office;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AppointmentEmailService {
    public record IntakeMail(long transferId, String recipient, String name, String title,
                             OffsetDateTime time, String location, String note) {}
    private final JavaMailSender sender;
    private final String from, frontendUrl;
    public AppointmentEmailService(JavaMailSender sender, @Value("${spring.mail.username:}") String from,
            @Value("${office.frontend-base-url:http://localhost:5173}") String frontendUrl) {
        this.sender=sender; this.from=from; this.frontendUrl=frontendUrl.replaceAll("/+$", "");
        var uri=java.net.URI.create(this.frontendUrl);
        if (!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getUserInfo()!=null)
            throw new IllegalArgumentException("office.frontend-base-url must be an HTTP(S) frontend URL");
    }
    public record Mail(Long claimId,String recipient,String name,String title,OffsetDateTime time,String location,String note) {}
    private static String h(String text) { return HtmlUtils.htmlEscape(text==null?"":text); }
    static String date(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.of("Asia/Bangkok")).format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));
    }
    public boolean send(Mail mail) {
        String url=frontendUrl+"/van-phong/claims/"+mail.claimId();
        String verificationLimit=date(OfficeAppointmentSchedule.verificationDeadline(mail.time()))+" (UTC+7)";
        String instruction="Bạn vui lòng mang theo giấy tờ tùy thân và căn cứ sở hữu để xác minh tại quầy.";
        String rule="Nếu hết hạn mà bạn chưa đến xác minh, yêu cầu nhận tài sản sẽ tự động bị hủy.";
        String plain="Kính gửi "+mail.name()+",\n\nVăn phòng trân trọng thông báo lịch xác minh nhận tài sản."
            +"\nHồ sơ #"+mail.claimId()+"\nTài sản: "+mail.title()+"\nGiờ hẹn: "+date(mail.time())+" (UTC+7)"
            +"\nĐịa điểm: "+mail.location()+"\nHạn đến quầy: "+verificationLimit+"\n"+instruction+"\n"+rule
            +(mail.note()==null?"":"\nHướng dẫn: "+mail.note())+"\nXem lịch hiện hành trên Sagasu: "+url;
        String html="""
            <!doctype html><html lang="vi"><body style="font-family:Arial,sans-serif;background:#f1f5f4;padding:24px">
            <div style="max-width:600px;margin:auto;background:white;padding:28px;border-radius:16px">
            <h1>Lịch xác minh nhận tài sản</h1><p>Kính gửi %s,</p><p>Tài sản %s · Hồ sơ #%s</p>
            <p><strong>Giờ hẹn</strong> %s (UTC+7)</p><p><strong>Địa điểm</strong> %s</p>
            <p><strong>Hạn đến quầy</strong> %s</p><p>%s</p><p>%s</p><p>%s</p>
            <p><a href="%s">Xem lịch hiện hành trên Sagasu</a></p></div></body></html>
            """.formatted(h(mail.name()),h(mail.title()),mail.claimId(),h(date(mail.time())),h(mail.location()),
                h(verificationLimit),h(instruction),h(rule),h(mail.note()).replace("\n","<br>"),h(url));
        try {
            var message=sender.createMimeMessage(); var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank()) helper.setFrom(from);
            helper.setTo(mail.recipient()); helper.setSubject("[Sagasu] Lịch xác minh nhận tài sản — hồ sơ #"+mail.claimId());
            helper.setText(plain,html); sender.send(message); return true;
        } catch(Exception failure) {
            LoggerFactory.getLogger(getClass()).warn("Office appointment mail failed for claim {} ({})",mail.claimId(),failure.getClass().getSimpleName()); return false;
        }
    }
    public boolean sendIntake(IntakeMail mail, OffsetDateTime deadline) {
        String link=frontendUrl+"/van-phong/chuyen-giao/"+mail.transferId();
        String rule="Nếu văn phòng chưa nhận đồ trước hạn tiếp nhận, yêu cầu sẽ tự động bị hủy.";
        String plain="Xin chào "+mail.name()+",\n\nVăn phòng mời bạn mang đồ đến bàn giao.\nĐồ vật: "+mail.title()
                +"\nGiờ hẹn: "+date(mail.time())+" (UTC+7)\nĐịa điểm: "+mail.location()
                +"\nHạn tiếp nhận: "+date(deadline)+" (UTC+7)\n"+rule+"\nXem yêu cầu: "+link
                +(mail.note()==null?"":"\nLời nhắn: "+mail.note());
        String html="""
            <!doctype html><html lang="vi"><body style="font-family:Arial,sans-serif;background:#f1f5f4;padding:24px">
            <div style="max-width:600px;margin:auto;background:white;padding:28px;border-radius:16px">
            <h1>Lịch mang đồ đến văn phòng</h1><p>Xin chào %s,</p><p>%s</p>
            <p><strong>Giờ hẹn</strong> %s (UTC+7)</p><p><strong>Địa điểm</strong> %s</p>
            <p><strong>Hạn tiếp nhận</strong> %s (UTC+7)</p><p>%s</p><p>%s</p>
            <p><a href="%s">Xem yêu cầu tiếp nhận</a></p></div></body></html>
            """.formatted(h(mail.name()),h(mail.title()),h(date(mail.time())),h(mail.location()),
                h(date(deadline)),h(rule),h(mail.note()),h(link));
        try {
            var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank())helper.setFrom(from);
            helper.setTo(mail.recipient());helper.setSubject("[Sagasu] Lịch mang đồ đến văn phòng — yêu cầu #"+mail.transferId());
            helper.setText(plain,html);sender.send(message);return true;
        } catch(Exception failure) {
            LoggerFactory.getLogger(getClass()).warn("Intake appointment mail failed for transfer {} ({})",mail.transferId(),failure.getClass().getSimpleName());return false;
        }
    }
}
