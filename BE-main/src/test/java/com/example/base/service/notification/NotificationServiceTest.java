package com.example.base.service.notification;

import com.example.base.dto.notification.NotificationDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.AccountRepository;
import com.example.base.repository.NotificationPreferenceRepository;
import com.example.base.repository.NotificationRepository;
import com.example.base.security.CustomUserDetails;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock NotificationPreferenceRepository preferences;
    @Mock AccountRepository accounts;
    @Mock SimpMessagingTemplate messaging;
    @Mock JavaMailSender mailSender;

    @InjectMocks NotificationService service;

    CustomUserDetails user7;

    @BeforeEach
    void setUp() {
        user7 = new CustomUserDetails(account(7L, AccountStatus.ACTIVE, AccountRole.USER));
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@sagasu.vn");
        ReflectionTestUtils.setField(service, "mailPassword", "secret");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173/");
        when(preferences.findById(anyLong())).thenReturn(Optional.empty());
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(50L);
            return n;
        });
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Account account(Long id, AccountStatus status, AccountRole role) {
        Account a = new Account();
        a.setId(id);
        a.setEmail("user" + id + "@gmail.com");
        a.setFullName("User " + id);
        a.setRole(role);
        a.setStatus(status);
        return a;
    }

    private static List<Notification> items(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            Notification x = new Notification(7L, "T", "IN_APP", "t" + i, "c", null);
            x.setId((long) i);
            return x;
        }).toList();
    }

    private NotificationPreference pref(boolean inApp, boolean email, boolean push) {
        NotificationPreference p = new NotificationPreference(7L);
        p.setInAppEnabled(inApp);
        p.setEmailEnabled(email);
        p.setBrowserPushEnabled(push);
        return p;
    }

    private String sentHtml() throws Exception {
        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(2000)).send(cap.capture());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cap.getValue().writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---------- list ----------

    @Test
    void utc001_list_allNotifications_returnsPageWithUnreadAndDefaultPrefs() {
        Pageable p = PageRequest.of(0, 10);
        when(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), any()))
                .thenReturn(new PageImpl<>(items(10), p, 15));
        when(notifications.countByRecipientIdAndReadFalse(7L)).thenReturn(4L);

        NotificationPageDto res = service.list(user7, false, 1, 10);

        assertThat(res.content()).hasSize(10);
        assertThat(res.unreadCount()).isEqualTo(4);
        assertThat(res.totalPages()).isEqualTo(2);
        assertThat(res.preferences()).isEqualTo(new NotificationPreferenceDto(true, true, false));
    }

    @Test
    void utc002_list_unreadOnly_usesUnreadQuery() {
        when(notifications.findByRecipientIdAndReadFalseOrderByCreatedAtDescIdDesc(eq(7L), any()))
                .thenReturn(new PageImpl<>(items(4)));

        NotificationPageDto res = service.list(user7, true, 1, 10);

        assertThat(res.content()).hasSize(4);
        verify(notifications, never()).findByRecipientIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void utc003_list_pageZero_clampedToOne() {
        when(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(user7, false, 0, 10).page()).isEqualTo(1);
    }

    @Test
    void utc004_list_sizeZero_clampedToOne() {
        when(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(user7, false, 1, 0).size()).isEqualTo(1);
    }

    @Test
    void utc005_list_size100_cappedAt50() {
        when(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(user7, false, 1, 100).size()).isEqualTo(50);
        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void utc006_list_empty_totalPagesAtLeastOne() {
        when(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        NotificationPageDto res = service.list(user7, false, 1, 10);
        assertThat(res.content()).isEmpty();
        assertThat(res.totalPages()).isEqualTo(1);
    }

    @Test
    void utc007_list_nullUser_throwsUnauthorized() {
        assertThatThrownBy(() -> service.list(null, false, 1, 10))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---------- unreadCount / markRead / markAllRead ----------

    @Test
    void utc008_unreadCount_returnsCountAndPrefs() {
        when(notifications.countByRecipientIdAndReadFalse(7L)).thenReturn(3L);
        UnreadCountDto res = service.unreadCount(user7);
        assertThat(res.unreadCount()).isEqualTo(3);
        assertThat(res.preferences()).isNotNull();
    }

    @Test
    void utc009_markRead_ownNotification_markedAndCountDecreases() {
        Notification n = items(1).get(0);
        when(notifications.findByIdAndRecipientId(50L, 7L)).thenReturn(Optional.of(n));
        when(notifications.countByRecipientIdAndReadFalse(7L)).thenReturn(2L);

        UnreadCountDto res = service.markRead(user7, 50L);

        assertThat(n.isRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        verify(notifications).save(n);
        assertThat(res.unreadCount()).isEqualTo(2);
    }

    @Test
    void utc010_markRead_otherUsersNotification_nothingChanged() {
        when(notifications.findByIdAndRecipientId(50L, 7L)).thenReturn(Optional.empty());
        when(notifications.countByRecipientIdAndReadFalse(7L)).thenReturn(3L);
        assertThat(service.markRead(user7, 50L).unreadCount()).isEqualTo(3);
        verify(notifications, never()).save(any());
    }

    @Test
    void utc011_markRead_missingNotification_noError() {
        when(notifications.findByIdAndRecipientId(999L, 7L)).thenReturn(Optional.empty());
        when(notifications.countByRecipientIdAndReadFalse(7L)).thenReturn(1L);
        assertThat(service.markRead(user7, 999L).unreadCount()).isEqualTo(1);
    }

    @Test
    void utc012_markAllRead_callsRepositoryAndReturnsZero() {
        UnreadCountDto res = service.markAllRead(user7);
        verify(notifications).markAllAsRead(eq(7L), any());
        assertThat(res.unreadCount()).isZero();
    }

    // ---------- preferences ----------

    @Test
    void utc013_getPreferences_noRow_returnsDefault() {
        assertThat(service.getPreferences(user7)).isEqualTo(new NotificationPreferenceDto(true, true, false));
    }

    @Test
    void utc014_updatePreferences_noRow_createsRow() {
        when(preferences.save(any())).thenAnswer(inv -> inv.getArgument(0));
        NotificationPreferenceDto res = service.updatePreferences(user7, new UpdatePreferenceRequest(null, false, null));
        ArgumentCaptor<NotificationPreference> cap = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferences).save(cap.capture());
        assertThat(cap.getValue().getAccountId()).isEqualTo(7L);
        assertThat(cap.getValue().getUpdatedAt()).isNotNull();
        assertThat(res).isEqualTo(new NotificationPreferenceDto(true, false, false));
    }

    @Test
    void utc015_updatePreferences_onlyNonNullFieldsChange() {
        when(preferences.findById(7L)).thenReturn(Optional.of(pref(true, true, true)));
        when(preferences.save(any())).thenAnswer(inv -> inv.getArgument(0));
        NotificationPreferenceDto res = service.updatePreferences(user7, new UpdatePreferenceRequest(null, null, false));
        assertThat(res).isEqualTo(new NotificationPreferenceDto(true, true, false));
    }

    @Test
    void utc016_updatePreferences_nullRequest_onlyUpdatedAtChanges() {
        NotificationPreference existing = pref(false, true, true);
        existing.setUpdatedAt(java.time.OffsetDateTime.now().minusDays(3));
        java.time.OffsetDateTime before = existing.getUpdatedAt();
        when(preferences.findById(7L)).thenReturn(Optional.of(existing));
        when(preferences.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceDto res = service.updatePreferences(user7, null);

        assertThat(res).isEqualTo(new NotificationPreferenceDto(false, true, true));
        assertThat(existing.getUpdatedAt()).isAfter(before);
    }

    @Test
    void utc017_updatePreferences_nullUser_throwsUnauthorized() {
        assertThatThrownBy(() -> service.updatePreferences(null, null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---------- notifyUser ----------

    @Test
    void utc018_notifyUser_activeRecipient_savesPushesAndEmails() throws Exception {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));

        service.notifyUser(7L, "CLAIM_APPROVED", "Title", "Body", "/claims/1", true);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(cap.capture());
        assertThat(cap.getValue().getChannel()).isEqualTo("BOTH");
        verify(messaging).convertAndSendToUser(eq("7"), eq("/queue/notifications"), any(NotificationItemDto.class));
        verify(mailSender, timeout(2000)).send(any(MimeMessage.class));
    }

    @Test
    void utc019_notifyUser_noEmail_channelInAppOnly() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));

        service.notifyUser(7L, "T", "Title", "Body", null, false);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(cap.capture());
        assertThat(cap.getValue().getChannel()).isEqualTo("IN_APP");
        verify(mailSender, after(300).never()).send(any(MimeMessage.class));
    }

    @Test
    void utc020_notifyUser_nullRecipient_returnsImmediately() {
        service.notifyUser(null, "T", "t", "c", null, true);
        verifyNoInteractions(accounts, notifications, messaging);
    }

    @Test
    void utc021_notifyUser_suspendedRecipient_nothingSent() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.SUSPENDED, AccountRole.USER)));
        service.notifyUser(7L, "T", "t", "c", null, true);
        verify(notifications, never()).save(any());
        verifyNoInteractions(messaging);
    }

    @Test
    void utc022_notifyUser_recipientNotFound_nothingSent() {
        when(accounts.findById(999L)).thenReturn(Optional.empty());
        service.notifyUser(999L, "T", "t", "c", null, true);
        verify(notifications, never()).save(any());
        verifyNoInteractions(messaging);
    }

    @Test
    void utc023_notifyUser_inAppDisabled_onlyEmail() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));
        when(preferences.findById(7L)).thenReturn(Optional.of(pref(false, true, false)));

        service.notifyUser(7L, "T", "t", "c", null, true);

        verify(notifications, never()).save(any());
        verifyNoInteractions(messaging);
        verify(mailSender, timeout(2000)).send(any(MimeMessage.class));
    }

    @Test
    void utc024_notifyUser_emailDisabled_noEmail() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));
        when(preferences.findById(7L)).thenReturn(Optional.of(pref(true, false, false)));

        service.notifyUser(7L, "T", "t", "c", null, true);

        verify(notifications).save(any());
        verify(mailSender, after(300).never()).send(any(MimeMessage.class));
    }

    @Test
    void utc025_notifyUser_undeliverableEmail_noEmailAttempt() {
        Account a = account(7L, AccountStatus.ACTIVE, AccountRole.USER);
        a.setEmail("user");
        when(accounts.findById(7L)).thenReturn(Optional.of(a));

        service.notifyUser(7L, "T", "t", "c", null, true);

        verify(mailSender, after(300).never()).createMimeMessage();
    }

    @Test
    void utc026_notifyUser_activeTransaction_dispatchAfterCommit() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));
        TransactionSynchronizationManager.initSynchronization();

        service.notifyUser(7L, "T", "t", "c", null, false);

        verifyNoInteractions(messaging);
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        verify(messaging).convertAndSendToUser(eq("7"), eq("/queue/notifications"), any(NotificationItemDto.class));
    }

    @Test
    void utc027_notifyUser_webSocketThrows_noExceptionPropagated() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));
        doThrow(new RuntimeException("ws down")).when(messaging).convertAndSendToUser(anyString(), anyString(), any());

        assertThatCode(() -> service.notifyUser(7L, "T", "t", "c", null, false)).doesNotThrowAnyException();
    }

    @Test
    void utc028_notifyUser_saveThrows_exceptionSwallowed() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));
        when(notifications.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.notifyUser(7L, "T", "t", "c", null, true)).doesNotThrowAnyException();
        verifyNoInteractions(messaging);
    }

    @Test
    void utc029_notifyUser_noMailPassword_emailOnlyLogged() {
        ReflectionTestUtils.setField(service, "mailPassword", "");
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));

        service.notifyUser(7L, "T", "t", "c", null, true);

        verify(mailSender, after(300).never()).send(any(MimeMessage.class));
    }

    @Test
    void utc030_notifyUser_relativeTargetUrl_joinedWithFrontendBase() throws Exception {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));

        service.notifyUser(7L, "T", "t", "c", "bai-dang/10", true);

        assertThat(sentHtml()).contains("http://localhost:5173/bai-dang/10");
    }

    @Test
    void utc031_notifyUser_htmlContentIsEscaped() throws Exception {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, AccountStatus.ACTIVE, AccountRole.USER)));

        service.notifyUser(7L, "T", "t", "<script>alert(1)</script>", null, true);

        String body = sentHtml();
        assertThat(body).contains("&lt;script&gt;");
    }

    // ---------- notifyRoles ----------

    @Test
    void utc032_notifyRoles_skipsExcludedAndInactive() {
        Account a2 = account(2L, AccountStatus.ACTIVE, AccountRole.APPROVER);
        Account a3 = account(3L, AccountStatus.ACTIVE, AccountRole.APPROVER);
        Account a4 = account(4L, AccountStatus.SUSPENDED, AccountRole.APPROVER);
        when(accounts.findByRole(AccountRole.APPROVER)).thenReturn(List.of(a2, a3, a4));
        when(accounts.findById(2L)).thenReturn(Optional.of(a2));
        NotificationService spy = spy(service);

        spy.notifyRoles(List.of(AccountRole.APPROVER), 3L, "T", "t", "c", null, false);

        verify(spy).notifyUser(eq(2L), any(), any(), any(), any(), anyBoolean());
        verify(spy, never()).notifyUser(eq(3L), any(), any(), any(), any(), anyBoolean());
        verify(spy, never()).notifyUser(eq(4L), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void utc033_notifyRoles_multipleRoles_notifiesEach() {
        when(accounts.findByRole(AccountRole.ADMIN)).thenReturn(List.of(account(1L, AccountStatus.ACTIVE, AccountRole.ADMIN)));
        when(accounts.findByRole(AccountRole.MANAGER)).thenReturn(List.of(account(5L, AccountStatus.ACTIVE, AccountRole.MANAGER)));
        NotificationService spy = spy(service);

        spy.notifyRoles(List.of(AccountRole.ADMIN, AccountRole.MANAGER), null, "T", "t", "c", null, false);

        verify(spy).notifyUser(eq(1L), any(), any(), any(), any(), anyBoolean());
        verify(spy).notifyUser(eq(5L), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void utc034_notifyRoles_nullRoles_noRepositoryCall() {
        service.notifyRoles(null, null, "T", "t", "c", null, false);
        verifyNoInteractions(accounts);
    }

    @Test
    void utc035_notifyRoles_emptyRoles_noRepositoryCall() {
        service.notifyRoles(List.of(), null, "T", "t", "c", null, false);
        verifyNoInteractions(accounts);
    }

    @Test
    void utc036_notifyRoles_findByRoleThrows_swallowed() {
        when(accounts.findByRole(any())).thenThrow(new RuntimeException("db"));
        assertThatCode(() -> service.notifyRoles(List.of(AccountRole.APPROVER), null, "T", "t", "c", null, false))
                .doesNotThrowAnyException();
    }
}
