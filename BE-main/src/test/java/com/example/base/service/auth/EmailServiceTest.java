package com.example.base.service.auth;

import com.example.base.entity.OtpPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService service;

    // UTC056 - Configured mailPassword sends OTP email with subject containing OTP and body containing purpose
    @Test
    void sendOtpEmail_whenMailPasswordConfigured_sendsMessageWithOtpAndPurpose() {
        ReflectionTestUtils.setField(service, "mailPassword", "app-secret-123");
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@sagasu.vn");

        boolean sent = service.sendOtpEmail("usertest@gmail.com", "123456", OtpPurpose.LOGIN);

        assertTrue(sent);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertNotNull(msg.getSubject());
        assertTrue(msg.getSubject().contains("123456"));
        assertNotNull(msg.getText());
        assertTrue(msg.getText().contains("LOGIN"));
        assertTrue("noreply@sagasu.vn".equals(msg.getFrom()));
    }

    @Test
    void utc057_sendOtpEmail_whenPasswordBlank_returnsFalseWithoutSending() {
        ReflectionTestUtils.setField(service, "mailPassword", "");
        assertFalse(service.sendOtpEmail("usertest@gmail.com", "123456", OtpPurpose.LOGIN));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void utc058_sendOtpEmail_whenPasswordPlaceholder_returnsFalseWithoutSending() {
        ReflectionTestUtils.setField(service, "mailPassword", "YOUR_GMAIL_APP_PASSWORD");
        assertFalse(service.sendOtpEmail("usertest@gmail.com", "123456", OtpPurpose.LOGIN));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // UTC059 - MailSendException from mailSender is swallowed and returns false
    @Test
    void sendOtpEmail_whenMailSenderThrowsMailSendException_swallowsExceptionAndReturnsFalse() {
        ReflectionTestUtils.setField(service, "mailPassword", "app-secret-123");
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@sagasu.vn");
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        boolean sent = service.sendOtpEmail("usertest@gmail.com", "123456", OtpPurpose.LOGIN);

        assertFalse(sent);
    }

    // UTC060 - Blank fromEmail sends email without calling setFrom
    @Test
    void sendOtpEmail_whenFromEmailBlank_sendsWithoutSettingFrom() {
        ReflectionTestUtils.setField(service, "mailPassword", "app-secret-123");
        ReflectionTestUtils.setField(service, "fromEmail", "");

        boolean sent = service.sendOtpEmail("usertest@gmail.com", "123456", OtpPurpose.CHANGE_PASSWORD);

        assertTrue(sent);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertNull(captor.getValue().getFrom());
    }
}

