package com.example.base.service.report;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.exception.AppException;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.OfficeAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.IntStream;

import static com.example.base.service.report.SqlStub.row;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportChatServiceTest {

    @Mock OfficeAccess access;
    @Mock ReportReviewService reviews;
    @Mock ReportContextService context;
    @Mock JdbcTemplate jdbc;
    @InjectMocks ReportChatService service;

    Account manager;
    CustomUserDetails user;

    @BeforeEach
    void setUp() {
        manager = new Account();
        manager.setId(1L);
        manager.setRole(AccountRole.MANAGER);
        manager.setStatus(AccountStatus.ACTIVE);
        user = new CustomUserDetails(manager);
        when(access.require(eq(user), any(AccountRole[].class))).thenReturn(manager);
        when(reviews.participantsFor(any())).thenReturn(Set.of(7L, 8L));
        when(context.one(contains("from chat_message where message_id"), any(Object[].class))).thenReturn(row("conversation_id", 40L));
        when(context.one(contains("from chat_conversation x join claim"), any(Object[].class))).thenReturn(row("conversation_id", 41L));
        when(context.withUrls(any())).thenAnswer(inv -> inv.getArgument(0));
        SqlStub.defaults(jdbc);
    }

    private void messages(int count) {
        when(jdbc.queryForList(contains("from chat_message m join accounts"), any(Object[].class))).thenAnswer(inv -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            // newest first, as the query orders by sequence_no desc
            IntStream.range(0, count).forEach(i -> rows.add(row("message_id", (long) (100 - i), "sequence_no", (long) (100 - i))));
            return rows;
        });
    }

    @Test
    void utc111_read_messageReport_returnsChronologicalPageAndLogsView() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "message_id", 30L, "status", "IN_REVIEW"));
        messages(3);

        Map<String, Object> res = service.read(5L, null, user);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        assertThat(content).extracting(m -> m.get("sequence_no")).containsExactly(98L, 99L, 100L);
        assertThat(res).containsEntry("hasMore", false).containsEntry("before", 98L);
        verify(jdbc).queryForList(contains("from chat_message m join accounts"), eq(40L), eq(Long.MAX_VALUE));
        verify(context, times(3)).retainAssets(eq(5L), any(), eq("CHAT_REVIEW"));
        verify(reviews).history(eq(5L), eq(1L), eq("VIEW_CHAT"), any(), any(), isNull(), contains("#40"));
    }

    @Test
    void utc112_read_51Rows_hasMoreAndTrimmedTo50() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "message_id", 30L, "status", "IN_REVIEW"));
        messages(51);

        Map<String, Object> res = service.read(5L, 200L, user);

        assertThat((List<?>) res.get("content")).hasSize(50);
        assertThat(res).containsEntry("hasMore", true);
    }

    @Test
    void utc113_read_claimReport_usesClaimConversation() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "claim_id", 20L, "status", "IN_REVIEW"));

        Map<String, Object> res = service.read(5L, null, user);

        verify(jdbc).queryForList(contains("from chat_message m join accounts"), eq(41L), eq(Long.MAX_VALUE));
        assertThat(res).containsEntry("before", 0);
    }

    @Test
    void utc114_read_userReport_returnsEmptyWithoutBrowsing() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "reported_account_id", 8L));

        assertThat(service.read(5L, null, user)).containsEntry("content", List.of()).containsEntry("hasMore", false);
        verifyNoInteractions(jdbc);
        verify(reviews, never()).history(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void utc115_read_managerIsParticipant_rejected() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "message_id", 30L));
        when(reviews.participantsFor(any())).thenReturn(Set.of(1L));
        assertThatThrownBy(() -> service.read(5L, null, user)).isInstanceOf(AppException.class).hasMessageContaining("quản lý khác");
    }

    @Test
    void utc116_read_beforeZero_rejected() {
        when(reviews.report(5L)).thenReturn(row("report_id", 5L, "message_id", 30L));
        assertThatThrownBy(() -> service.read(5L, 0L, user)).isInstanceOf(AppException.class).hasMessageContaining("không hợp lệ");
    }
}
