package com.example.base.service.office;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentEmailServiceTest {

    @Mock
    JavaMailSender sender;

    private String extractHtml(MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        return extractFromPart(content);
    }

    private String extractFromPart(Object content) throws Exception {
        if (content instanceof String s) return s;
        if (content instanceof Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                sb.append(extractFromPart(bp.getContent()));
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }

    @Test
    void utc053_constructor_stripsTrailingSlashes() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn///");

        boolean ok = svc.send(new AppointmentEmailService.Mail(1L, "u@x.com", "An", "Keys",
                OffsetDateTime.now().plusDays(1), "Room 1", "Note"), "tok123", null);

        assertThat(ok).isTrue();
        assertThat(extractHtml(mime)).contains("https://sagasu.vn/van-phong/lich-hen#token=tok123");
    }

    @Test
    void utc054_constructor_ftpScheme_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AppointmentEmailService(sender, "a@b.com", "ftp://sagasu.vn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("office.frontend-base-url must be an HTTP(S) frontend URL");
    }

    @Test
    void utc055_constructor_queryString_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AppointmentEmailService(sender, "a@b.com", "https://sagasu.vn?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void utc056_constructor_userInfo_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AppointmentEmailService(sender, "a@b.com", "https://user@sagasu.vn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void utc057_send_escapesHtmlAndIncludesConfirmDeclineLinksAndDefaultDeadline() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn");

        boolean ok = svc.send(new AppointmentEmailService.Mail(30L, "u@x.com", "<b>An</b>", "Black Wallet",
                OffsetDateTime.now().plusDays(1), "Room 1", null), "myToken", null);

        assertThat(ok).isTrue();
        verify(sender).send(mime);
        String html = extractHtml(mime);
        assertThat(html).contains("&lt;b&gt;An&lt;/b&gt;");
        assertThat(html).contains("https://sagasu.vn/van-phong/lich-hen#token=myToken&amp;decision=CONFIRM");
        assertThat(html).contains("12 giờ kể từ khi gửi lời mời thành công");
    }

    @Test
    void utc058_send_explicitDeadline_formatsUtcPlus7() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn");
        OffsetDateTime deadline = OffsetDateTime.of(2026, 11, 11, 5, 0, 0, 0, ZoneOffset.UTC);

        boolean ok = svc.send(new AppointmentEmailService.Mail(30L, "u@x.com", "An", "Wallet",
                OffsetDateTime.now().plusDays(1), "Room 1", null), "tok", deadline);

        assertThat(ok).isTrue();
        String html = extractHtml(mime);
        assertThat(html).contains("12:00 ngày 11/11/2026 (UTC+7)");
    }

    @Test
    void utc059_send_blankFrom_doesNotSetFromAndReturnsTrue() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        AppointmentEmailService svc = new AppointmentEmailService(sender, "", "https://sagasu.vn");

        boolean ok = svc.send(new AppointmentEmailService.Mail(30L, "u@x.com", "An", "Wallet",
                OffsetDateTime.now().plusDays(1), "Room 1", null), "tok", null);

        assertThat(ok).isTrue();
        assertThat(mime.getFrom()).isNullOrEmpty();
    }

    @Test
    void utc060_send_mailSendException_returnsFalse() {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(MimeMessage.class));
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn");

        boolean ok = svc.send(new AppointmentEmailService.Mail(30L, "u@x.com", "An", "Wallet",
                OffsetDateTime.now().plusDays(1), "Room 1", null), "tok", null);

        assertThat(ok).isFalse();
    }

    @Test
    void utc061_sendIntake_sendsExpectedSubjectAndIntakeLink() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn");
        OffsetDateTime time = OffsetDateTime.now().plusDays(1);
        OffsetDateTime deadline = OffsetDateTime.now().plusHours(12);

        boolean ok = svc.sendIntake(new AppointmentEmailService.IntakeMail(4L, "u@x.com", "An", "Wallet",
                time, "Room 101", "Call"), "tok4", deadline);

        assertThat(ok).isTrue();
        verify(sender).send(mime);
        assertThat(mime.getSubject()).isEqualTo("[Sagasu] Lịch mang đồ đến văn phòng — yêu cầu #4");
        assertThat(extractHtml(mime)).contains("/van-phong/hen-giao-do#token=tok4");
    }

    @Test
    void utc062_sendIntake_senderThrows_returnsFalse() {
        MimeMessage mime = new MimeMessage((Session) null);
        when(sender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(MimeMessage.class));
        AppointmentEmailService svc = new AppointmentEmailService(sender, "noreply@sagasu.vn", "https://sagasu.vn");

        boolean ok = svc.sendIntake(new AppointmentEmailService.IntakeMail(4L, "u@x.com", "An", "Wallet",
                OffsetDateTime.now().plusDays(1), "Room 101", null), "tok4", OffsetDateTime.now().plusHours(12));

        assertThat(ok).isFalse();
    }
}

