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
    private static String date(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.of("Asia/Bangkok")).format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));
    }
    public boolean send(Mail mail,String token,OffsetDateTime deadline) {
        String url=frontendUrl+"/van-phong/lich-hen#token="+token;
        String confirm=url+"&decision=CONFIRM", decline=url+"&decision=DECLINE";
        String limit=deadline==null?"12 giờ kể từ khi gửi lời mời thành công":date(deadline)+" (UTC+7)";
        String plain="Xin chào "+mail.name()+",\n\nVăn phòng mời bạn đến nhận đồ.\nHồ sơ: #"+mail.claimId()
            +"\nĐồ vật: "+mail.title()+"\nThời gian: "+date(mail.time())+" (UTC+7)\nĐịa điểm: "+mail.location()
            +"\nHạn phản hồi: "+limit+"\n\nĐồng ý lịch hẹn: "+confirm+"\nTừ chối lịch hẹn: "+decline
            +"\n\nMở liên kết rồi xác nhận lựa chọn. Không cần đăng nhập. Không chia sẻ liên kết này."
            +"\nKhông phản hồi trong hạn: yêu cầu tự động bị từ chối, lịch hẹn bị hủy. Gửi lại thư không gia hạn."
            +(mail.note()==null?"":"\nHướng dẫn: "+mail.note());
        String body="""
            <!doctype html><html lang="vi"><body style="margin:0;background:#f1f5f4;font-family:Arial,sans-serif;color:#183b3b">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr><td align="center" style="padding:28px 12px">
            <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%%;max-width:600px;background:#fff;border-radius:16px">
            <tr><td style="padding:28px"><p style="color:#0f766e;font-weight:bold">VĂN PHÒNG · SAGASU</p>
            <h1 style="font-size:25px">Lịch hẹn nhận đồ</h1><p>Xin chào %s,</p><p>Văn phòng đã duyệt yêu cầu nhận đồ của bạn.</p>
            <table role="presentation" width="100%%" cellpadding="10" style="background:#f0fdfa;text-align:left">
            <tr><th>Hồ sơ</th><td>#%s</td></tr><tr><th>Đồ vật</th><td>%s</td></tr><tr><th>Thời gian</th><td>%s (UTC+7)</td></tr>
            <tr><th>Địa điểm</th><td>%s</td></tr><tr><th>Hạn phản hồi</th><td>%s</td></tr></table>
            <p style="line-height:1.6">%s</p><p style="line-height:3">
            <a href="%s" style="display:inline-block;background:#0f766e;color:white;padding:0 18px;border-radius:8px;text-decoration:none;margin:4px">Đồng ý lịch hẹn</a>
            <a href="%s" style="display:inline-block;background:#fff1f2;color:#9f1239;padding:0 18px;border-radius:8px;text-decoration:none;margin:4px">Từ chối lịch hẹn</a></p>
            <p>Mở liên kết rồi xác nhận lựa chọn. <strong>Không cần đăng nhập.</strong> Mở thư hoặc liên kết chưa được tính là phản hồi.</p>
            <p style="color:#64748b;line-height:1.6">Không phản hồi trong 12 giờ: yêu cầu tự động bị từ chối và lịch hẹn bị hủy. Gửi lại thư không gia hạn.
            Chỉ liên kết trong thư mới nhất có hiệu lực. Không chuyển tiếp liên kết cho người khác.</p>
            <p>Mang theo giấy tờ và bằng chứng sở hữu khi đến nhận đồ.</p></td></tr></table></td></tr></table></body></html>
            """.formatted(h(mail.name()),mail.claimId(),h(mail.title()),h(date(mail.time())),h(mail.location()),h(limit),h(mail.note()).replace("\n","<br>"),h(confirm),h(decline));
        try {
            var message=sender.createMimeMessage(); var helper=new MimeMessageHelper(message,true,"UTF-8");
            if(!from.isBlank()) helper.setFrom(from);
            helper.setTo(mail.recipient()); helper.setSubject("[Sagasu] Xác nhận lịch hẹn nhận đồ — hồ sơ #"+mail.claimId());
            helper.setText(plain,body); sender.send(message); return true;
        } catch(Exception failure) {
            LoggerFactory.getLogger(getClass()).warn("Office appointment mail failed for claim {} ({})",mail.claimId(),failure.getClass().getSimpleName()); return false;
        }
    }
    public boolean sendIntake(IntakeMail mail, String token, OffsetDateTime deadline) {
        String link=frontendUrl+"/van-phong/hen-giao-do#token="+token;
        String confirm=link+"&decision=CONFIRM", decline=link+"&decision=DECLINE";
        String plain="Xin chào "+mail.name()+",\nVăn phòng mời bạn mang đồ đến bàn giao.\nĐồ vật: "+mail.title()
                +"\nThời gian: "+date(mail.time())+" (UTC+7)\nĐịa điểm: "+mail.location()
                +"\nHạn phản hồi: "+date(deadline)+" (UTC+7)\nĐồng ý: "+confirm+"\nTừ chối: "+decline
                +"\nVăn phòng chỉ nhận trách nhiệm giữ đồ sau khi kiểm tra và nhận đồ thực tế."
                +"\nNếu chưa sắp xếp được, hãy từ chối lịch để văn phòng hẹn lại. Không cần đăng nhập."
                +(mail.note()==null?"":"\nLời nhắn: "+mail.note());
        String html="""
            <!doctype html><html lang="vi"><body style="font-family:Arial,sans-serif;background:#f1f5f4;padding:24px;color:#183b3b">
            <div style="max-width:600px;margin:auto;background:white;padding:28px;border-radius:16px">
            <p style="color:#0f766e">VĂN PHÒNG · SAGASU</p><h1>Lịch mang đồ đến văn phòng</h1>
            <p>Xin chào %s,</p><p>Cảm ơn bạn đã giữ giúp đồ thất lạc. Bạn có thể mang đồ đến theo lịch sau:</p>
            <p><strong>%s</strong></p><p>Thời gian: %s (UTC+7)</p><p>Địa điểm: %s</p><p>%s</p>
            <p>Vui lòng phản hồi trước %s (UTC+7).</p>
            <p><a href="%s" style="display:inline-block;background:#0f766e;color:white;padding:14px;border-radius:8px">Đồng ý lịch hẹn</a></p>
            <p><a href="%s">Tôi chưa sắp xếp được</a></p>
            <p>Văn phòng sẽ kiểm tra và xác nhận khi bạn mang đồ đến. Nếu từ chối hoặc chưa phản hồi trong 12 giờ,
            yêu cầu tiếp nhận vẫn được giữ để văn phòng hẹn lại.</p><p>Không cần đăng nhập. Không chia sẻ liên kết này.</p></div></body></html>
            """.formatted(h(mail.name()),h(mail.title()),h(date(mail.time())),h(mail.location()),h(mail.note()),h(date(deadline)),h(confirm),h(decline));
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
