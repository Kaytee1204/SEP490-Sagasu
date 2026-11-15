package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportReasonService {
    private final JdbcTemplate jdbc;

    // Office review integration: these allegations need context beyond the selected label.
    private static final Set<String> CONTEXT_REQUIRED = Set.of(
            "POST_FALSE_INFO", "POST_SCAM", "USER_IMPERSONATION", "USER_HARASSMENT",
            "USER_SCAM", "USER_PRIVACY", "CLAIM_FALSE_OWNERSHIP", "CLAIM_FALSE_EVIDENCE",
            "CLAIM_CONFLICTING_INFO");

    private Reason reason(String code, String label, boolean required) {
        return new Reason(code, label, required || CONTEXT_REQUIRED.contains(code));
    }

    public record Reason(String code, String label, boolean requiresDescription) {}
    public record Options(String group, List<Reason> reasons) {}

    public Options options(String group) {
        return new Options(group, jdbc.query("""
                select code, label, description_required from report_reason
                where target_group = ? and active = true order by sort_order, code
                """, (rs, row) -> reason(rs.getString(1), rs.getString(2), rs.getBoolean(3)), group));
    }

    public Reason require(String group, String code, String description) {
        Reason reason = jdbc.query("""
                select code, label, description_required from report_reason
                where target_group = ? and code = ? and active = true
                """, (rs, row) -> reason(rs.getString(1), rs.getString(2), rs.getBoolean(3)), group, code)
                .stream().findFirst().orElseThrow(() -> new AppException(ErrorCode.REPORT_REASON_INVALID));
        if (reason.requiresDescription() && (description == null || description.isBlank())) {
            throw new AppException(ErrorCode.REPORT_DESCRIPTION_REQUIRED);
        }
        return reason;
    }
}
