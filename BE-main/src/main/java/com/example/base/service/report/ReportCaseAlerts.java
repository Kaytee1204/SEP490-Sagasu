package com.example.base.service.report;

import com.example.base.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ReportCaseAlerts {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public void notifyRelated(long reportId) {
        var owners=jdbc.queryForList("""
            with targets as (
              select r.*,coalesce(r.post_id,(select post_id from claim where claim_id=r.claim_id),
                (select c.post_id from chat_message m join chat_conversation c on c.conversation_id=m.conversation_id where m.message_id=r.message_id)) as item_id
              from report r where r.report_id=? or r.case_id is not null
            )
            select distinct c.owner_id from targets source join targets related on source.report_id<>related.report_id
              and ((source.item_id is not null and source.item_id=related.item_id)
                or (source.reported_account_id is not null and source.reported_account_id=related.reported_account_id)
                or (source.message_id is not null and source.message_id=related.message_id))
            join report_case c on c.case_id=related.case_id where source.report_id=?
            """,reportId,reportId);
        for(var owner:owners) notifications.notifyUser(((Number)owner.get("owner_id")).longValue(),
            "REPORT_CASE_RELATED","Có báo cáo có thể liên quan đến vụ việc đang phụ trách",
            "Báo cáo #"+reportId+" cùng đối tượng hoặc đồ vật. Hãy đối chiếu trước khi liên kết; báo cáo chưa được tự thêm vào vụ việc.",
            "/admin/reports?id="+reportId,false);
    }
}
