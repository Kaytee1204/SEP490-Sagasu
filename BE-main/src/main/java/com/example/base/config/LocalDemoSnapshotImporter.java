package com.example.base.config;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.repository.AccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class LocalDemoSnapshotImporter {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSnapshotImporter.class);
    private static final String SNAPSHOT_PATH = "seed/local_demo_snapshot.json";

    private LocalDemoSnapshotImporter() {
    }

    static void syncAccountsOnly(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        JsonNode root = readSnapshot(objectMapper);
        if (root == null) return;
        try {
            syncAccounts(root.path("accounts"), accountRepository, passwordEncoder);
        } catch (Exception e) {
            log.warn("[DEMO SNAPSHOT] Lỗi đồng bộ accounts: {}", e.getMessage());
        }
    }

    static void importIfNeeded(
            DataSource dataSource,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        JsonNode root = readSnapshot(objectMapper);
        if (root == null) return;

        try {
            syncAccounts(root.path("accounts"), accountRepository, passwordEncoder);
        } catch (Exception e) {
            log.warn("[DEMO SNAPSHOT] Lỗi đồng bộ accounts: {}", e.getMessage());
        }

        try (Connection conn = dataSource.getConnection()) {
            Map<String, Long> accountIds = loadAccountIdsByEmail(conn);
            Map<String, Long> postIds = loadPostIdsByTitle(conn);

            importClaimsIdempotently(conn, root.path("claims"), root.path("postStatusOverrides"), accountIds, postIds);
            importNotificationsIfEmpty(conn, root.path("notifications"), accountIds);
        } catch (Exception e) {
            log.warn("[DEMO SNAPSHOT] Bỏ qua import snapshot: {}", e.getMessage());
        }
    }

    private static JsonNode readSnapshot(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(SNAPSHOT_PATH);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            log.warn("[DEMO SNAPSHOT] Không đọc được {}: {}", SNAPSHOT_PATH, e.getMessage());
            return null;
        }
    }

    private static void syncAccounts(
            JsonNode accountsNode,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        if (!accountsNode.isArray()) return;
        int created = 0;
        for (JsonNode item : accountsNode) {
            String email = text(item, "email");
            if (!StringUtils.hasText(email)) continue;
            Account existing = accountRepository.findByEmailIgnoreCase(email).orElse(null);
            String phone = text(item, "phone");
            if (existing == null) {
                Account acc = new Account();
                acc.setEmail(email);
                acc.setFullName(text(item, "fullName"));
                acc.setPhone(phone);
                acc.setPasswordHash(passwordEncoder.encode("123"));
                acc.setRole(parseRole(text(item, "role")));
                acc.setStatus(parseStatus(text(item, "status")));
                acc.setMustChangePassword(item.path("mustChangePassword").asBoolean(false));
                accountRepository.save(acc);
                created++;
            } else if (!StringUtils.hasText(existing.getPhone()) && StringUtils.hasText(phone)) {
                existing.setPhone(phone);
                accountRepository.save(existing);
            }
        }
        if (created > 0) {
            log.info("[DEMO SNAPSHOT] Đã tạo bổ sung {} tài khoản demo từ snapshot.", created);
        }
    }

    private static void importClaimsIdempotently(
            Connection conn,
            JsonNode claimsNode,
            JsonNode statusOverridesNode,
            Map<String, Long> accountIds,
            Map<String, Long> postIds
    ) throws SQLException {
        if (!claimsNode.isArray() || claimsNode.isEmpty()) return;

        Map<String, Long> questionIds = loadQuestionIds(conn);
        int insertedClaims = 0;

        boolean origAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement psCheckClaim = conn.prepareStatement("""
                    SELECT 1 FROM claim WHERE post_id = ? AND claimant_id = ? AND status IN ('PENDING', 'APPROVED') LIMIT 1
                    """);
             PreparedStatement psCheckApproved = conn.prepareStatement("""
                    SELECT 1 FROM claim WHERE post_id = ? AND status = 'APPROVED' LIMIT 1
                    """);
             PreparedStatement psClaim = conn.prepareStatement("""
                    INSERT INTO claim (post_id, claimant_id, created_by, claim_type, submission_channel, status, description, evidence_note, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, 0, ?, ?) RETURNING claim_id
                    """);
             PreparedStatement psAnswer = conn.prepareStatement("""
                    INSERT INTO claim_answer (claim_id, question_id, question_code, question_label, answer_text, section_title, template_code, template_version, sort_order, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
             PreparedStatement psActivity = conn.prepareStatement("""
                    INSERT INTO claim_activity (claim_id, actor_id, action, from_status, to_status, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """);
             PreparedStatement psHandover = conn.prepareStatement("""
                    INSERT INTO p2p_handover (claim_id, recorded_by, confirmed_by, status, note, recorded_at, confirmed_at, created_at, updated_at, client_request_id, request_hash, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::uuid, ?, 0)
                    """)) {

            for (JsonNode c : claimsNode) {
                Long postId = postIds.get(text(c, "postTitle"));
                Long claimantId = lookupEmail(accountIds, text(c, "claimantEmail"));
                if (postId == null || claimantId == null) continue;

                // Per-claim idempotency: skip if this claimant already has an active claim on this post
                psCheckClaim.setLong(1, postId);
                psCheckClaim.setLong(2, claimantId);
                try (ResultSet rs = psCheckClaim.executeQuery()) {
                    if (rs.next()) continue;
                }

                String desiredStatus = StringUtils.hasText(text(c, "status")) ? text(c, "status") : "PENDING";
                if ("APPROVED".equals(desiredStatus)) {
                    psCheckApproved.setLong(1, postId);
                    try (ResultSet rs = psCheckApproved.executeQuery()) {
                        if (rs.next()) {
                            desiredStatus = "PENDING";
                        }
                    }
                }

                Timestamp createdAt = parseTs(text(c, "createdAt"));
                Timestamp updatedAt = parseTs(text(c, "updatedAt"));

                Savepoint sp = conn.setSavepoint();
                try {
                    psClaim.setLong(1, postId);
                    psClaim.setLong(2, claimantId);
                    psClaim.setLong(3, claimantId);
                    psClaim.setString(4, StringUtils.hasText(text(c, "claimType")) ? text(c, "claimType") : "P2P");
                    psClaim.setString(5, desiredStatus);
                    psClaim.setString(6, nonNull(text(c, "description")));
                    setNullableString(psClaim, 7, text(c, "evidenceNote"));
                    psClaim.setTimestamp(8, createdAt);
                    psClaim.setTimestamp(9, updatedAt);

                    long newClaimId;
                    try (ResultSet rs = psClaim.executeQuery()) {
                        rs.next();
                        newClaimId = rs.getLong(1);
                    }
                    insertedClaims++;

                    JsonNode answers = c.path("answers");
                    if (answers.isArray()) {
                        for (JsonNode a : answers) {
                            String tpl = text(a, "templateCode");
                            String qCode = text(a, "questionCode");
                            Long qId = questionIds.get(tpl + "::" + qCode);
                            if (qId == null) continue;
                            psAnswer.setLong(1, newClaimId);
                            psAnswer.setLong(2, qId);
                            psAnswer.setString(3, qCode);
                            psAnswer.setString(4, nonNull(text(a, "questionLabel")));
                            psAnswer.setString(5, nonNull(text(a, "answerText")));
                            psAnswer.setString(6, nonNull(text(a, "sectionTitle")));
                            psAnswer.setString(7, nonNull(tpl));
                            psAnswer.setInt(8, a.path("templateVersion").asInt(1));
                            psAnswer.setInt(9, a.path("sortOrder").asInt(0));
                            psAnswer.setTimestamp(10, parseTs(text(a, "createdAt")));
                            psAnswer.addBatch();
                        }
                        psAnswer.executeBatch();
                    }

                    JsonNode activities = c.path("activities");
                    if (activities.isArray()) {
                        for (JsonNode act : activities) {
                            Long actorId = lookupEmail(accountIds, text(act, "actorEmail"));
                            if (actorId == null) actorId = claimantId;
                            psActivity.setLong(1, newClaimId);
                            psActivity.setLong(2, actorId);
                            psActivity.setString(3, nonNull(text(act, "action")));
                            setNullableString(psActivity, 4, text(act, "fromStatus"));
                            psActivity.setString(5, nonNull(text(act, "toStatus")));
                            setNullableString(psActivity, 6, text(act, "reason"));
                            psActivity.setTimestamp(7, parseTs(text(act, "createdAt")));
                            psActivity.addBatch();
                        }
                        psActivity.executeBatch();
                    }

                    JsonNode h = c.path("handover");
                    if (h.isObject() && "APPROVED".equals(desiredStatus)) {
                        Long recBy = lookupEmail(accountIds, text(h, "recordedByEmail"));
                        if (recBy != null) {
                            psHandover.setLong(1, newClaimId);
                            psHandover.setLong(2, recBy);
                            setNullableLong(psHandover, 3, lookupEmail(accountIds, text(h, "confirmedByEmail")));
                            psHandover.setString(4, StringUtils.hasText(text(h, "status")) ? text(h, "status") : "WAITING_CONFIRMATION");
                            setNullableString(psHandover, 5, text(h, "note"));
                            psHandover.setTimestamp(6, parseTs(text(h, "recordedAt")));
                            setNullableTs(psHandover, 7, text(h, "confirmedAt"));
                            psHandover.setTimestamp(8, parseTs(text(h, "createdAt")));
                            psHandover.setTimestamp(9, parseTs(text(h, "updatedAt")));
                            String reqId = text(h, "clientRequestId");
                            psHandover.setString(10, StringUtils.hasText(reqId) ? reqId : UUID.randomUUID().toString());
                            psHandover.setString(11, StringUtils.hasText(text(h, "requestHash")) ? text(h, "requestHash") : UUID.randomUUID().toString());
                            psHandover.executeUpdate();
                        }
                    }
                    conn.releaseSavepoint(sp);
                } catch (Exception claimEx) {
                    conn.rollback(sp);
                    insertedClaims = Math.max(0, insertedClaims - 1);
                    log.warn("[DEMO SNAPSHOT] Bỏ qua 1 claim mẫu ({}): {}", text(c, "claimantEmail"), claimEx.getMessage());
                }
            }

            if (insertedClaims > 0 && statusOverridesNode.isArray()) {
                try (PreparedStatement psPost = conn.prepareStatement("UPDATE post SET status = ? WHERE post_id = ? AND status = 'OPEN'")) {
                    for (JsonNode ov : statusOverridesNode) {
                        Long postId = postIds.get(text(ov, "postTitle"));
                        String status = text(ov, "status");
                        if (postId != null && StringUtils.hasText(status)) {
                            psPost.setString(1, status);
                            psPost.setLong(2, postId);
                            psPost.addBatch();
                        }
                    }
                    psPost.executeBatch();
                }
            }

            conn.commit();
            if (insertedClaims > 0) {
                log.info("[DEMO SNAPSHOT] Đã import bổ sung {} yêu cầu nhận đồ (claims) kèm câu trả lời và lịch sử.", insertedClaims);
            }
        } catch (Exception e) {
            conn.rollback();
            log.warn("[DEMO SNAPSHOT] Lỗi import claims (đã rollback): {}", e.getMessage());
        } finally {
            conn.setAutoCommit(origAuto);
        }
    }

    private static void importNotificationsIfEmpty(Connection conn, JsonNode notiNode, Map<String, Long> accountIds) throws SQLException {
        if (!notiNode.isArray() || notiNode.isEmpty()) return;
        if (countRows(conn, "notification") > 0) return;

        int inserted = 0;
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO notification (recipient_id, type, channel, title, content, target_url, is_read, read_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (JsonNode n : notiNode) {
                Long recipientId = lookupEmail(accountIds, text(n, "recipientEmail"));
                if (recipientId == null) continue;
                ps.setLong(1, recipientId);
                ps.setString(2, StringUtils.hasText(text(n, "type")) ? text(n, "type") : "SYSTEM");
                ps.setString(3, StringUtils.hasText(text(n, "channel")) ? text(n, "channel") : "IN_APP");
                ps.setString(4, nonNull(text(n, "title")));
                ps.setString(5, nonNull(text(n, "content")));
                setNullableString(ps, 6, text(n, "targetUrl"));
                ps.setBoolean(7, n.path("isRead").asBoolean(false));
                setNullableTs(ps, 8, text(n, "readAt"));
                ps.setTimestamp(9, parseTs(text(n, "createdAt")));
                ps.addBatch();
                inserted++;
            }
            if (inserted > 0) {
                ps.executeBatch();
                log.info("[DEMO SNAPSHOT] Đã import {} thông báo hệ thống.", inserted);
            }
        }
    }

    private static long countRows(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static Map<String, Long> loadAccountIdsByEmail(Connection conn) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, LOWER(email) FROM accounts")) {
            while (rs.next()) {
                map.put(rs.getString(2), rs.getLong(1));
            }
        }
        return map;
    }

    private static Map<String, Long> loadPostIdsByTitle(Connection conn) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT post_id, title FROM post")) {
            while (rs.next()) {
                map.putIfAbsent(rs.getString(2), rs.getLong(1));
            }
        }
        return map;
    }

    private static Map<String, Long> loadQuestionIds(Connection conn) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT cq.question_id, cqt.code, cq.question_code
                     FROM claim_question cq
                     JOIN claim_question_template cqt ON cqt.template_id = cq.template_id
                     WHERE cqt.is_active = true
                     """)) {
            while (rs.next()) {
                map.put(rs.getString(2) + "::" + rs.getString(3), rs.getLong(1));
            }
        }
        return map;
    }

    private static Long lookupEmail(Map<String, Long> map, String email) {
        if (!StringUtils.hasText(email)) return null;
        return map.get(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static Timestamp parseTs(String iso) {
        if (!StringUtils.hasText(iso)) return Timestamp.from(Instant.now());
        try {
            return Timestamp.from(Instant.parse(iso));
        } catch (Exception e) {
            return Timestamp.from(Instant.now());
        }
    }

    private static void setNullableTs(PreparedStatement ps, int idx, String iso) throws SQLException {
        if (!StringUtils.hasText(iso)) {
            ps.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(idx, parseTs(iso));
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, val);
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.BIGINT);
        else ps.setLong(idx, val);
    }

    private static AccountRole parseRole(String role) {
        if (!StringUtils.hasText(role)) return AccountRole.USER;
        try {
            return AccountRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AccountRole.USER;
        }
    }

    private static AccountStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) return AccountStatus.ACTIVE;
        try {
            return AccountStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AccountStatus.ACTIVE;
        }
    }
}
