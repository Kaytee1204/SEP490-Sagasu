package com.example.base.service.handover;

import com.example.base.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class P2pReceiptReminder {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    @Transactional
    public void remindUnconfirmedReceipts() {
        var reminders = jdbc.query("""
                with due as (
                    select h.handover_id, c.claim_id, c.claimant_id, c.post_id
                    from p2p_handover h
                    join claim c on c.claim_id = h.claim_id
                    join post p on p.post_id = c.post_id
                    where h.status = 'WAITING_CONFIRMATION' and h.receipt_reminded_at is null
                      and h.updated_at <= now() - interval '24 hours'
                      and c.status = 'APPROVED' and c.claim_type = 'P2P'
                      and p.status = 'RESERVED' and p.custody_mode = 'P2P' and p.deleted_at is null
                      and not exists (select 1 from report_hold hold where hold.post_id = p.post_id and hold.released_at is null)
                      and not exists (
                          select 1 from p2p_handover_issue issue join report r using (report_id)
                          where issue.handover_id = h.handover_id and r.status in ('OPEN', 'IN_REVIEW')
                      )
                    order by h.updated_at, h.handover_id
                    limit 100 for update of h skip locked
                )
                update p2p_handover h set receipt_reminded_at = now() from due
                where h.handover_id = due.handover_id
                returning due.claim_id, due.claimant_id, due.post_id
                """, (rs, row) -> new DueReceipt(rs.getLong("claim_id"), rs.getLong("claimant_id"), rs.getLong("post_id")));
        for (var receipt : reminders) {
            notifications.notifyUser(receipt.recipientId(), "HANDOVER_RECORDED", "Bạn còn một lượt nhận đồ cần xác nhận",
                    "Nếu đã nhận đúng món đồ, hãy mở hồ sơ để xác nhận. Nếu chưa nhận hoặc có vấn đề, hãy báo sự cố trong hồ sơ.",
                    "/bai-dang/" + receipt.postId() + "/claims/" + receipt.claimId() + "#handover", true);
        }
    }

    private record DueReceipt(long claimId, long recipientId, long postId) {}
}
