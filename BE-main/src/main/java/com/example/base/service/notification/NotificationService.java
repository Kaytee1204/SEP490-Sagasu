package com.example.base.service.notification;

import com.example.base.dto.notification.NotificationDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.NotificationPreferenceRepository;
import com.example.base.repository.NotificationRepository;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final AccountRepository accounts;
    private final SimpMessagingTemplate messaging;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${office.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public NotificationPageDto list(CustomUserDetails user, boolean unreadOnly, int page, int size) {
        long accountId = requireActorId(user);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Page<Notification> result = unreadOnly
                ? notifications.findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(accountId, pageable)
                : notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(accountId, pageable);

        long unreadCount = notifications.countByRecipientIdAndReadFalse(accountId);
        NotificationPreferenceDto prefDto = toPreferenceDto(getOrDefault(accountId));

        List<NotificationItemDto> items = result.getContent().stream()
                .map(this::toItemDto)
                .toList();

        return new NotificationPageDto(
                items,
                safePage,
                safeSize,
                result.getTotalElements(),
                Math.max(1, result.getTotalPages()),
                unreadCount,
                prefDto
        );
    }

    @Transactional(readOnly = true)
    public UnreadCountDto unreadCount(CustomUserDetails user) {
        long accountId = requireActorId(user);
        long count = notifications.countByRecipientIdAndReadFalse(accountId);
        return new UnreadCountDto(count, toPreferenceDto(getOrDefault(accountId)));
    }

    @Transactional
    public UnreadCountDto markRead(CustomUserDetails user, long notificationId) {
        long accountId = requireActorId(user);
        notifications.findByIdAndRecipientId(notificationId, accountId).ifPresent(n -> {
            n.markRead();
            notifications.save(n);
        });
        long count = notifications.countByRecipientIdAndReadFalse(accountId);
        return new UnreadCountDto(count, toPreferenceDto(getOrDefault(accountId)));
    }

    @Transactional
    public UnreadCountDto markAllRead(CustomUserDetails user) {
        long accountId = requireActorId(user);
        notifications.markAllAsRead(accountId, OffsetDateTime.now());
        return new UnreadCountDto(0L, toPreferenceDto(getOrDefault(accountId)));
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceDto getPreferences(CustomUserDetails user) {
        long accountId = requireActorId(user);
        return toPreferenceDto(getOrDefault(accountId));
    }

    @Transactional
    public NotificationPreferenceDto updatePreferences(CustomUserDetails user, UpdatePreferenceRequest request) {
        long accountId = requireActorId(user);
        NotificationPreference pref = preferences.findById(accountId)
                .orElseGet(() -> new NotificationPreference(accountId));

        if (request != null) {
            if (request.inAppEnabled() != null) {
                pref.setInAppEnabled(request.inAppEnabled());
            }
            if (request.emailEnabled() != null) {
                pref.setEmailEnabled(request.emailEnabled());
            }
            if (request.browserPushEnabled() != null) {
                pref.setBrowserPushEnabled(request.browserPushEnabled());
            }
        }
        pref.setUpdatedAt(OffsetDateTime.now());
        NotificationPreference saved = preferences.save(pref);
        return toPreferenceDto(saved);
    }

    @Transactional
    public void notifyUser(
            Long recipientId,
            String type,
            String title,
            String content,
            String targetUrl,
            boolean sendEmail
    ) {
        if (recipientId == null) {
            return;
        }
        try {
            Account recipient = accounts.findById(recipientId).orElse(null);
            if (recipient == null || recipient.getStatus() != AccountStatus.ACTIVE) {
                return;
            }
            NotificationPreference pref = getOrDefault(recipientId);
            String channel = sendEmail ? "BOTH" : "IN_APP";

            NotificationItemDto savedItemDto = null;
            if (pref.isInAppEnabled()) {
                Notification entity = new Notification(
                        recipientId,
                        type,
                        channel,
                        title,
                        content,
                        targetUrl
                );
                Notification saved = notifications.save(entity);
                savedItemDto = toItemDto(saved);
            }

            final NotificationItemDto wsPayload = savedItemDto;
            final boolean shouldEmail = sendEmail && pref.isEmailEnabled();
            final String recipientEmail = recipient.getEmail();
            final String recipientName = StringUtils.hasText(recipient.getFullName())
                    ? recipient.getFullName()
                    : recipient.getEmail();

            Runnable afterCommitTask = () -> {
                if (wsPayload != null) {
                    try {
                        messaging.convertAndSendToUser(
                                Long.toString(recipientId),
                                "/queue/notifications",
                                wsPayload
                        );
                    } catch (RuntimeException ex) {
                        log.debug("Could not push WebSocket notification to user {}: {}", recipientId, ex.getMessage());
                    }
                }
                if (shouldEmail && isDeliverableEmail(recipientEmail)) {
                    CompletableFuture.runAsync(() ->
                            sendNotificationEmail(recipientEmail, recipientName, title, content, targetUrl)
                    );
                }
            };

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        afterCommitTask.run();
                    }
                });
            } else {
                afterCommitTask.run();
            }
        } catch (RuntimeException ex) {
            log.warn("Notification dispatch skipped for user {} ({}): {}", recipientId, type, ex.getMessage());
        }
    }

    @Transactional
    public void notifyRoles(
            Collection<AccountRole> roles,
            Long excludeActorId,
            String type,
            String title,
            String content,
            String targetUrl,
            boolean sendEmail
    ) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        try {
            for (AccountRole role : roles) {
                List<Account> roleAccounts = accounts.findByRole(role);
                for (Account acc : roleAccounts) {
                    if (acc.getStatus() == AccountStatus.ACTIVE
                            && !Objects.equals(acc.getId(), excludeActorId)) {
                        notifyUser(acc.getId(), type, title, content, targetUrl, sendEmail);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Role notification dispatch skipped for roles {} ({}): {}", roles, type, ex.getMessage());
        }
    }

    private void sendNotificationEmail(
            String toEmail,
            String recipientName,
            String title,
            String content,
            String targetUrl
    ) {
        if (!StringUtils.hasText(mailPassword) || "YOUR_GMAIL_APP_PASSWORD".equals(mailPassword)) {
            log.info("[NOTIFICATION-EMAIL] To: {} | Subject: {} | Content: {}", toEmail, title, content);
            return;
        }

        try {
            String base = frontendBaseUrl != null ? frontendBaseUrl.replaceAll("/+$", "") : "http://localhost:5173";
            String fullUrl = StringUtils.hasText(targetUrl)
                    ? (targetUrl.startsWith("http") ? targetUrl : base + (targetUrl.startsWith("/") ? targetUrl : "/" + targetUrl))
                    : base;

            String plainText = "Xin chào " + recipientName + ",\n\n"
                    + title + "\n\n"
                    + content + "\n\n"
                    + "Xem chi tiết tại hệ thống Sagasu: " + fullUrl + "\n\n"
                    + "Bạn có thể tùy chỉnh bật/tắt thông báo Email bất kỳ lúc nào tại biểu tượng Chuông thông báo trên thanh điều hướng Sagasu.\n"
                    + "Trân trọng,\nHệ thống Sagasu";

            String htmlBody = """
                    <!doctype html>
                    <html lang="vi">
                    <body style="margin:0;background:#f1f5f9;font-family:Arial,sans-serif;color:#0f172a">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                        <tr>
                          <td align="center" style="padding:28px 12px">
                            <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%%;max-width:600px;background:#ffffff;border-radius:16px;border:1px solid #e2e8f0">
                              <tr>
                                <td style="padding:28px">
                                  <p style="margin:0 0 8px;color:#4f46e5;font-weight:700;font-size:13px;letter-spacing:0.06em">THÔNG BÁO HỆ THỐNG · SAGASU</p>
                                  <h1 style="margin:0 0 16px;font-size:21px;color:#0f172a">%s</h1>
                                  <p style="margin:0 0 12px;font-size:15px;line-height:1.6">Xin chào <strong>%s</strong>,</p>
                                  <div style="background:#f8fafc;border-left:4px solid #4f46e5;padding:16px;border-radius:8px;margin:16px 0;font-size:15px;line-height:1.6;color:#1e293b">
                                    %s
                                  </div>
                                  <p style="margin:24px 0">
                                    <a href="%s" style="display:inline-block;background:#4f46e5;color:#ffffff;padding:12px 22px;border-radius:10px;text-decoration:none;font-weight:600;font-size:14px">
                                      Xem chi tiết trên Sagasu
                                    </a>
                                  </p>
                                  <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0 16px"/>
                                  <p style="margin:0;color:#64748b;font-size:12px;line-height:1.5">
                                    Bạn nhận được email này vì tính năng thông báo Email đang bật trên tài khoản Sagasu của bạn. Bạn có thể chủ động bật/tắt thông báo tại mục Cài đặt thông báo (biểu tượng chuông trên web).
                                  </p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(
                    HtmlUtils.htmlEscape(title != null ? title : "Thông báo mới"),
                    HtmlUtils.htmlEscape(recipientName != null ? recipientName : toEmail),
                    HtmlUtils.htmlEscape(content != null ? content : "").replace("\n", "<br/>"),
                    HtmlUtils.htmlEscape(fullUrl)
            );

            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            if (StringUtils.hasText(fromEmail)) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(toEmail);
            helper.setSubject("[Sagasu] " + title);
            helper.setText(plainText, htmlBody);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.warn("Could not send notification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private boolean isDeliverableEmail(String email) {
        return email != null && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    }

    private NotificationPreference getOrDefault(long accountId) {
        return preferences.findById(accountId)
                .orElseGet(() -> new NotificationPreference(accountId));
    }

    private NotificationPreferenceDto toPreferenceDto(NotificationPreference pref) {
        return new NotificationPreferenceDto(
                pref.isInAppEnabled(),
                pref.isEmailEnabled(),
                pref.isBrowserPushEnabled()
        );
    }

    private NotificationItemDto toItemDto(Notification n) {
        return new NotificationItemDto(
                n.getId(),
                n.getType(),
                n.getChannel(),
                n.getTitle(),
                n.getContent(),
                n.getTargetUrl(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }

    private long requireActorId(CustomUserDetails user) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return user.getId();
    }
}
