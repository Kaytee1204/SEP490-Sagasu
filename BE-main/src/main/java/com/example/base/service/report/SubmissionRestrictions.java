package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.service.notification.NotificationService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmissionRestrictions {
    public enum Action { POST, CLAIM }

    private final JdbcTemplate jdbc;

    public void checkAvailable(long accountId, Action action) {
        jdbc.query("""
            select expires_at from account_submission_restriction
            where account_id=? and action=? and expires_at>clock_timestamp()
            """, (rs, row) -> rs.getObject("expires_at", OffsetDateTime.class), accountId, action.name())
            .stream().findFirst().ifPresent(expiresAt -> {
                String activity = action == Action.POST ? "đăng tin" : "gửi yêu cầu nhận đồ mới";
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn bị tạm cấm " + activity + " đến "
                        + NotificationService.formatVietnamDeadline(expiresAt) + ".");
            });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForSubmission(long accountId, Action action) {
        lock(accountId, action);
        checkAvailable(accountId, action);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OffsetDateTime restrictForSevenDays(long accountId, Action action, long reportId) {
        lock(accountId, action);
        return jdbc.queryForObject("""
            insert into account_submission_restriction (account_id, action, expires_at, report_id)
            values (?, ?, clock_timestamp() + interval '168 hours', ?)
            on conflict (account_id, action) do update set
                expires_at=greatest(account_submission_restriction.expires_at, excluded.expires_at),
                report_id=excluded.report_id
            returning expires_at
            """, (rs, row) -> rs.getObject("expires_at", OffsetDateTime.class), accountId, action.name(), reportId);
    }

    private void lock(long accountId, Action action) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { }, "submission-restriction:" + action.name() + ":" + accountId);
    }
}
