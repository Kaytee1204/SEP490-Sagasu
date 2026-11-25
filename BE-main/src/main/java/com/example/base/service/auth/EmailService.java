package com.example.base.service.auth;

import com.example.base.entity.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean sendOtpEmail(String toEmail, String otpCode, OtpPurpose purpose) {
        log.info("==================================================");
        log.info("[OTP] Email: {} | Purpose: {} | Mã OTP: {}", toEmail, purpose, otpCode);
        log.info("==================================================");

        if (!StringUtils.hasText(mailPassword) || "YOUR_GMAIL_APP_PASSWORD".equals(mailPassword)) {
            log.warn("Chưa cấu hình Gmail App Password trong application.yml (spring.mail.password). Bỏ qua gửi mail SMTP và dùng mã OTP in trên Console.");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(fromEmail)) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("[SAGASU - KhanhTB] Mã xác thực OTP của bạn: " + otpCode);
            message.setText(
                    "Xin chào " + toEmail + ",\n\n"
                    + "Mã xác thực OTP của bạn cho mục đích [" + purpose.name() + "] là: " + otpCode + "\n"
                    + "Mã có hiệu lực trong vòng 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n"
                    + "Trân trọng,\nHệ thống Sagasu"
            );
            mailSender.send(message);
            log.info("Đã gửi email OTP thành công tới {}", toEmail);
            return true;
        } catch (Exception ex) {
            log.error("Không thể gửi email OTP tới {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    /** SRS UC-10 bước 6: gửi thông tin đăng nhập và mật khẩu ban đầu cho tài khoản mới. Lỗi gửi mail không chặn việc tạo tài khoản. */
    public boolean sendInitialPasswordEmail(String toEmail, String fullName, String rawPassword) {
        if (!StringUtils.hasText(mailPassword) || "YOUR_GMAIL_APP_PASSWORD".equals(mailPassword)) {
            log.warn("Chưa cấu hình Gmail App Password: bỏ qua gửi email mật khẩu ban đầu tới {}.", toEmail);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (StringUtils.hasText(fromEmail)) {
                message.setFrom(fromEmail);
            }
            message.setTo(toEmail);
            message.setSubject("[SAGASU] Thông tin tài khoản của bạn");
            message.setText(
                    "Xin chào " + fullName + ",\n\n"
                    + "Tài khoản Sagasu của bạn đã được tạo.\n"
                    + "Email đăng nhập: " + toEmail + "\n"
                    + "Mật khẩu ban đầu: " + rawPassword + "\n\n"
                    + "Vui lòng đăng nhập và đổi mật khẩu ngay sau lần đăng nhập đầu tiên.\n\n"
                    + "Trân trọng,\nHệ thống Sagasu"
            );
            mailSender.send(message);
            log.info("Đã gửi email mật khẩu ban đầu tới {}", toEmail);
            return true;
        } catch (Exception ex) {
            log.error("Không thể gửi email mật khẩu ban đầu tới {}: {}", toEmail, ex.getMessage());
            return false;
        }
    }
}
