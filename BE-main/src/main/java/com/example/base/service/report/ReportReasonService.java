package com.example.base.service.report;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportReasonService {
    private final JdbcTemplate jdbc;

    public static final Set<String> VALID_GROUPS = Set.of(
            "POST", "CLAIM", "HANDOVER_ISSUE", "CLAIM_APPEAL");

    // Office review integration: these allegations need context beyond the selected label.
    private static final Set<String> CONTEXT_REQUIRED = Set.of(
            "POST_FALSE_INFO", "POST_SCAM", "POST_PRIVACY", "USER_IMPERSONATION", "USER_HARASSMENT",
            "USER_SCAM", "USER_PRIVACY", "CLAIM_FALSE_OWNERSHIP", "CLAIM_FALSE_EVIDENCE",
            "CLAIM_CONFLICTING_INFO");

    private Reason reason(String code, String label, boolean required) {
        return new Reason(code, label, required || CONTEXT_REQUIRED.contains(code));
    }

    public record Reason(String code, String label, boolean requiresDescription) {}
    public record Options(String group, List<Reason> reasons) {}

    public record AdminReasonItem(
            String code,
            String targetGroup,
            String label,
            boolean descriptionRequired,
            boolean active,
            int sortOrder,
            long usageCount
    ) {}

    public record UpsertReasonRequest(
            String code,
            String targetGroup,
            String label,
            Boolean descriptionRequired,
            Boolean active,
            Integer sortOrder
    ) {}

    public record StatusRequest(boolean active) {}

    public Options options(String group) {
        requireSelectableGroup(group);
        return new Options(group, jdbc.query("""
                select code, label, description_required from report_reason
                where target_group = ? and active = true order by sort_order, code
                """, (rs, row) -> reason(rs.getString(1), rs.getString(2), rs.getBoolean(3)), group));
    }

    public Reason require(String group, String code, String description) {
        requireSelectableGroup(group);
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

    private static void requireSelectableGroup(String group) {
        if (group == null || !VALID_GROUPS.contains(group)) throw new AppException(ErrorCode.REPORT_REASON_INVALID);
    }

    @Transactional(readOnly = true)
    public List<AdminReasonItem> listAdmin(String targetGroup, Boolean active, String keyword) {
        StringBuilder sql = new StringBuilder("""
                select r.code, r.target_group, r.label, r.description_required, r.active, r.sort_order,
                       (select count(*) from report rep where rep.reason_code = r.code) as usage_count
                from report_reason r
                where r.target_group not in ('MESSAGE', 'USER')
                """);
        List<Object> params = new ArrayList<>();

        if (targetGroup != null && !targetGroup.isBlank() && !"ALL".equalsIgnoreCase(targetGroup.trim())) {
            String group = targetGroup.trim().toUpperCase(Locale.ROOT);
            if (!VALID_GROUPS.contains(group)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Nhóm đối tượng báo cáo không hợp lệ: " + targetGroup);
            }
            sql.append(" and r.target_group = ?");
            params.add(group);
        }

        if (active != null) {
            sql.append(" and r.active = ?");
            params.add(active);
        }

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" and (lower(r.code) like ? or lower(r.label) like ?)");
            params.add(like);
            params.add(like);
        }

        sql.append("""
                 order by case r.target_group
                    when 'POST' then 1
                    when 'USER' then 2
                    when 'CLAIM' then 3
                    when 'HANDOVER_ISSUE' then 4
                    else 5
                 end, r.sort_order asc, r.code asc
                """);

        return jdbc.query(sql.toString(), (rs, row) -> new AdminReasonItem(
                rs.getString("code"),
                rs.getString("target_group"),
                rs.getString("label"),
                rs.getBoolean("description_required"),
                rs.getBoolean("active"),
                rs.getInt("sort_order"),
                rs.getLong("usage_count")
        ), params.toArray());
    }

    @Transactional(readOnly = true)
    public AdminReasonItem getByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã lý do báo cáo không được để trống.");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return jdbc.query("""
                select r.code, r.target_group, r.label, r.description_required, r.active, r.sort_order,
                       (select count(*) from report rep where rep.reason_code = r.code) as usage_count
                from report_reason r
                where r.code = ?
                """, (rs, row) -> new AdminReasonItem(
                rs.getString("code"),
                rs.getString("target_group"),
                rs.getString("label"),
                rs.getBoolean("description_required"),
                rs.getBoolean("active"),
                rs.getInt("sort_order"),
                rs.getLong("usage_count")
        ), normalized).stream().findFirst().orElseThrow(
                () -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy lý do báo cáo: " + normalized));
    }

    @Transactional
    public AdminReasonItem create(UpsertReasonRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu lý do báo cáo không hợp lệ.");
        }
        String group = validateGroup(request.targetGroup());
        String label = validateLabel(request.label());
        String code = normalizeOrGenerateCode(request.code(), group, label);

        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from report_reason where code = ?)",
                Boolean.class,
                code
        );
        if (Boolean.TRUE.equals(exists)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã lý do báo cáo '" + code + "' đã tồn tại.");
        }

        int sortOrder;
        if (request.sortOrder() != null) {
            sortOrder = Math.max(1, Math.min(999, request.sortOrder()));
        } else {
            Integer maxSort = jdbc.queryForObject(
                    "select coalesce(max(sort_order), 0) from report_reason where target_group = ? and sort_order < 90",
                    Integer.class,
                    group
            );
            sortOrder = (maxSort == null ? 0 : maxSort) + 1;
        }

        boolean descRequired = request.descriptionRequired() != null
                ? request.descriptionRequired()
                : CONTEXT_REQUIRED.contains(code);
        boolean active = request.active() == null || request.active();

        jdbc.update("""
                insert into report_reason (code, target_group, label, description_required, active, sort_order)
                values (?, ?, ?, ?, ?, ?)
                """, code, group, label, descRequired, active, sortOrder);

        return getByCode(code);
    }

    @Transactional
    public AdminReasonItem update(String code, UpsertReasonRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu cập nhật không hợp lệ.");
        }
        AdminReasonItem existing = getByCode(code);
        validateGroup(existing.targetGroup());
        String group = (request.targetGroup() != null && !request.targetGroup().isBlank())
                ? validateGroup(request.targetGroup())
                : existing.targetGroup();
        String label = (request.label() != null && !request.label().isBlank())
                ? validateLabel(request.label())
                : existing.label();
        boolean descRequired = request.descriptionRequired() != null
                ? request.descriptionRequired()
                : existing.descriptionRequired();
        boolean active = request.active() != null
                ? request.active()
                : existing.active();
        int sortOrder = request.sortOrder() != null
                ? Math.max(1, Math.min(999, request.sortOrder()))
                : existing.sortOrder();

        jdbc.update("""
                update report_reason
                set target_group = ?, label = ?, description_required = ?, active = ?, sort_order = ?
                where code = ?
                """, group, label, descRequired, active, sortOrder, existing.code());

        return getByCode(existing.code());
    }

    @Transactional
    public AdminReasonItem updateStatus(String code, boolean active) {
        AdminReasonItem existing = getByCode(code);
        validateGroup(existing.targetGroup());
        jdbc.update("update report_reason set active = ? where code = ?", active, existing.code());
        return getByCode(existing.code());
    }

    @Transactional
    public Map<String, Object> delete(String code) {
        AdminReasonItem existing = getByCode(code);
        validateGroup(existing.targetGroup());
        if (existing.usageCount() > 0) {
            jdbc.update("update report_reason set active = false where code = ?", existing.code());
            return Map.of(
                    "code", existing.code(),
                    "deleted", false,
                    "deactivated", true,
                    "usageCount", existing.usageCount(),
                    "message", "Lý do đã có " + existing.usageCount() + " báo cáo sử dụng nên được chuyển sang Ngừng hoạt động để bảo toàn lịch sử."
            );
        }
        jdbc.update("delete from report_reason where code = ?", existing.code());
        return Map.of(
                "code", existing.code(),
                "deleted", true,
                "deactivated", false,
                "usageCount", 0L,
                "message", "Đã xóa vĩnh viễn lý do báo cáo " + existing.code() + "."
        );
    }

    private String validateGroup(String rawGroup) {
        if (rawGroup == null || rawGroup.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng chọn nhóm đối tượng báo cáo.");
        }
        String normalized = rawGroup.trim().toUpperCase(Locale.ROOT);
        if (!VALID_GROUPS.contains(normalized)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Nhóm đối tượng báo cáo không hợp lệ: " + rawGroup);
        }
        return normalized;
    }

    private String validateLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên hiển thị lý do báo cáo không được để trống.");
        }
        String trimmed = rawLabel.trim();
        if (trimmed.length() > 200) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Tên lý do báo cáo tối đa 200 ký tự.");
        }
        return trimmed;
    }

    private String normalizeOrGenerateCode(String rawCode, String group, String label) {
        String candidate;
        if (rawCode != null && !rawCode.isBlank()) {
            candidate = slugifyCode(rawCode);
        } else {
            String slug = slugifyCode(label);
            candidate = slug.startsWith(group + "_") ? slug : (group + "_" + slug);
        }
        if (candidate.length() > 64) {
            candidate = candidate.substring(0, 64).replaceAll("_+$", "");
        }
        if (!candidate.matches("^[A-Z0-9_]{3,64}$")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã lý do báo cáo chỉ gồm chữ in hoa, số và dấu gạch dưới (3-64 ký tự).");
        }
        return candidate;
    }

    private static String slugifyCode(String input) {
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "CUSTOM" : normalized;
    }
}
