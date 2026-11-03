package com.example.base.repository;

import com.example.base.dto.chat.ChatPageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository @RequiredArgsConstructor
public class ChatQueryRepository {
    private final JdbcTemplate jdbc;
    public List<ChatPageDto.Summary> list(long actorId, OffsetDateTime before, long beforeId, int limit) {
        String cutoff = before == null ? null : before.toString();
        return jdbc.query("""
            SELECT c.conversation_id, c.post_id, p.title, a.full_name,
                   COALESCE(m.body, CASE WHEN m.message_id IS NULL THEN 'Chưa có tin nhắn' ELSE 'Tệp đính kèm' END) AS preview,
                   c.last_activity_at,
                   (SELECT count(*) FROM chat_message u WHERE u.conversation_id=c.conversation_id
                      AND u.sender_id<>? AND u.sequence_no>mine.last_read_sequence) AS unread,
                   (cl.status='APPROVED' AND cl.post_id=c.post_id AND cl.claimant_id=c.claimant_id
                     AND p.post_type='FOUND' AND p.custody_mode='P2P' AND p.moderation_status='VISIBLE'
                     AND p.status='RESERVED' AND p.deleted_at IS NULL AND a.status='ACTIVE'
                     AND (a.locked_until IS NULL OR a.locked_until<now())
                     AND (a.valid_until IS NULL OR a.valid_until>now())
                     AND NOT EXISTS (SELECT 1 FROM item_receipt r WHERE r.post_id=p.post_id)) AS can_send
            FROM chat_participant mine JOIN chat_conversation c ON c.conversation_id=mine.conversation_id
            JOIN post p ON p.post_id=c.post_id JOIN claim cl ON cl.claim_id=c.latest_claim_id
            JOIN accounts a ON a.id=CASE WHEN mine.account_id=p.account_id THEN c.claimant_id ELSE p.account_id END
            LEFT JOIN chat_message m ON m.conversation_id=c.conversation_id AND m.sequence_no=c.last_message_sequence
            WHERE mine.account_id=? AND (?::timestamptz IS NULL OR
                (c.last_activity_at,c.conversation_id)<(?::timestamptz,?))
            ORDER BY c.last_activity_at DESC,c.conversation_id DESC LIMIT ?
            """, (rs, n) -> new ChatPageDto.Summary(rs.getLong("conversation_id"), rs.getLong("post_id"),
                rs.getString("title"), rs.getString("full_name"), rs.getString("preview"),
                rs.getObject("last_activity_at", OffsetDateTime.class), rs.getLong("unread"), rs.getBoolean("can_send")),
                actorId, actorId, cutoff, cutoff, beforeId, limit);
    }
    public long unread(long actorId) {
        Long count = jdbc.queryForObject("""
            SELECT count(*) FROM chat_participant p JOIN chat_message m ON m.conversation_id=p.conversation_id
            WHERE p.account_id=? AND m.sender_id<>? AND m.sequence_no>p.last_read_sequence
            """, Long.class, actorId, actorId);
        return count == null ? 0 : count;
    }
}
