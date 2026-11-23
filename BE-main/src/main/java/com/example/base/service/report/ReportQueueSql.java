package com.example.base.service.report;

final class ReportQueueSql {
  private ReportQueueSql() {}

  // These predicates are shared with the appeal detail to keep the queue and the reopen action in sync.
  static final String ACTIVE_APPROVAL = """
      approval.closed_at is null and chosen.status='APPROVED' and chosen.claim_type=p.custody_mode
        and approval.custody_mode=chosen.claim_type
        and p.status='RESERVED' and p.deleted_at is null
      """;
  static final String OTHER_APPEALS_PENDING = """
      exists (
        select 1 from p2p_claim_appeal sibling join report q on q.report_id=sibling.report_id
        join p2p_claim_approval sibling_round on sibling_round.approval_id=sibling.approval_id
        join claim sibling_claim on sibling_claim.claim_id=sibling_round.approved_claim_id
        where approval.approval_id is not null and sibling_claim.post_id=p.post_id
          and (approval.custody_mode='P2P' or sibling_round.approval_id=approval.approval_id)
          and q.status in ('OPEN','IN_REVIEW')
      )
      """;
  static final String APPROVAL_CAN_BE_REOPENED = """
      p.post_type='FOUND' and p.moderation_status='VISIBLE'
        and ((chosen.claim_type='P2P' and not exists (select 1 from item_receipt receipt where receipt.post_id=p.post_id))
          or (chosen.claim_type='OFFICE' and exists (
            select 1 from item_receipt receipt where receipt.post_id=p.post_id and receipt.status='IN_STORAGE'
          ) and not exists (
            select 1 from office_handover office_record where office_record.claim_id=chosen.claim_id
          )))
        and not exists (
          select 1 from p2p_claim_appeal custody_appeal join report_inquiry inquiry using (report_id)
          where custody_appeal.approval_id=approval.approval_id and inquiry.verification_kind='CUSTODY'
            and (inquiry.verified_custody='DELIVERED' or inquiry.response='Tôi đã giao đồ' or (inquiry.responded_at is null and inquiry.closed_at is null))
        )
        and not exists (select 1 from p2p_handover handover where handover.claim_id=chosen.claim_id and handover.status<>'CANCELLED')
        and not exists (select 1 from report_hold hold where hold.post_id=p.post_id and hold.released_at is null)
        and not exists (
          select 1 from p2p_handover_issue issue join report dispute using(report_id)
          join claim disputed on disputed.claim_id=dispute.claim_id
          where disputed.post_id=p.post_id and issue.phase='BEFORE' and dispute.status in ('OPEN','IN_REVIEW'))
        and not exists (
          select 1 from p2p_claim_appeal related join report_recovery x using(report_id)
          where related.approval_id=approval.approval_id and x.confirmed_at is null)
      """;
  static final String REOPEN_ALLOWED = "not pending.waiting_other_appeals and (" + APPROVAL_CAN_BE_REOPENED + ")";

  // The report conclusion and the approval round can finish at different times.
  static final String APPEAL_STEP = """
      case when r.status='RESOLVED' and not exists(select 1 from report_decision w where w.report_id=r.report_id and w.action='APPEAL_WITHDRAWN') and (%s) then
        case when pending.waiting_other_appeals then 'WAIT_APPEALS'
          when (%s) then 'READY_TO_REOPEN'
          else 'VERIFY_CUSTODY' end
      end
      """.formatted(ACTIVE_APPROVAL, REOPEN_ALLOWED);

  // OFFSET 0 preserves these lateral stages so PostgreSQL computes each report's facts
  // once instead of inlining the same correlated subqueries into every priority expression.
  static final String CLASSIFICATION = """
      left join claim chosen on chosen.claim_id=approval.approved_claim_id
      left join report_hold hold on hold.report_id=r.report_id and hold.released_at is null
      left join report_recovery recovery on recovery.report_id=r.report_id
      left join lateral (
        select min(ap.scheduled_at) as next_pickup
        from appointment ap join claim scheduled on scheduled.claim_id=ap.claim_id
        where r.kind='HANDOVER_ISSUE' and appeal.report_id is null and scheduled.post_id=p.post_id
          and scheduled.status='APPROVED' and ap.status='SCHEDULED'
          and coalesce(ap.response_status,'LEGACY') not in ('DECLINED','EXPIRED') and ap.scheduled_at>=now()
      ) pickup on true
      cross join lateral (
        select count(*)>0 as asked, coalesce(bool_or(responded_at is null and closed_at is null),false) as waiting_reply,
          coalesce(bool_or(responded_at is null and closed_at is null
            and coalesce(response_due_at,created_at+interval '3 days')<=now()),false) as reply_overdue,
          max(responded_at) as latest_reply, max(created_at) as latest_question
        from report_inquiry where report_id=r.report_id
      ) inquiry
      cross join lateral (
        select max(created_at) as latest_decision, coalesce(bool_or(action='REQUEST_ITEM_RECOVERY'),false) as recovery_requested
        from report_decision where report_id=r.report_id and action not in ('PRIORITY_HIGH','PRIORITY_NORMAL','PRIORITY_AUTO')
      ) decisions
      cross join lateral (select %s as waiting_other_appeals offset 0) pending
      cross join lateral (
        select r.status='RESOLVED' and not exists(select 1 from report_decision w where w.report_id=r.report_id and w.action='APPEAL_WITHDRAWN') and appeal.report_id is not null and (%s) as accepted_appeal,
          coalesce(inquiry.latest_reply > greatest(inquiry.latest_question, decisions.latest_decision),false) as new_reply,
          coalesce(pickup.next_pickup < ?::timestamptz,false) as pickup_this_shift,
          decisions.recovery_requested and recovery.confirmed_at is null as recovery_pending,
          coalesce(recovery.confirmed_at is null and coalesce(recovery.due_at,
            decisions.latest_decision+interval '3 days')<=now(),false) and decisions.recovery_requested as recovery_overdue,
          recovery.confirmed_at is not null as recovery_confirmed,
          exists (
            select 1 from p2p_handover_issue issue left join p2p_handover h using(handover_id)
            where issue.report_id=r.report_id and (h.status='DISPUTED' or issue.phase in ('BEFORE','AFTER'))
          ) as active_p2p_dispute
        offset 0
      ) facts
      cross join lateral (
        select case
          when r.status not in ('OPEN','IN_REVIEW') then null
          when r.post_id is not null and p.deleted_at is null and p.moderation_status='VISIBLE'
            and r.reason_code in ('POST_PRIVACY','POST_SCAM') then r.reason_code
          when r.reason_code='USER_SAFETY_THREAT' then 'USER_SAFETY_THREAT'
          when facts.pickup_this_shift and hold.report_id is null then 'OFFICE_SOON'
          when facts.active_p2p_dispute and not decisions.recovery_requested
            and r.reason_code in ('HANDOVER_WRONG_ITEM','HANDOVER_NOT_RECEIVED','HANDOVER_WRONG_RECIPIENT') then 'P2P_RISK'
        end as urgent_reason
        offset 0
      ) risk
      cross join lateral (
        select case
          when r.status not in ('OPEN','IN_REVIEW') and not coalesce(facts.accepted_appeal,false) then 'HISTORY'
          when risk.urgent_reason is not null or facts.new_reply or facts.recovery_overdue then null
          when facts.recovery_pending then 'WAITING_RECOVERY'
          when inquiry.reply_overdue then null
          when r.kind='HANDOVER_ISSUE' and exists (select 1 from report_decision d where d.report_id=r.report_id and d.action='HANDOVER_VERIFY_HOLDER' and d.created_at=decisions.latest_decision and (inquiry.latest_question is null or d.created_at>=inquiry.latest_question)) then null
          when inquiry.waiting_reply then 'WAITING_REPLY'
          when facts.accepted_appeal and pending.waiting_other_appeals then 'WAITING_OTHER_APPEALS'
          when facts.accepted_appeal and not (%s) then 'WAITING_REOPEN'
          when facts.accepted_appeal then 'WAITING_FINDER_REVIEW'
        end as waiting_reason
        offset 0
      ) step
      cross join lateral (
        select case when step.waiting_reason='HISTORY' then 'HISTORY'
          when step.waiting_reason is not null then 'WAITING' else 'READY' end as work_state,
          case when step.waiting_reason='HISTORY' then 4 when step.waiting_reason is not null then 3
            when risk.urgent_reason is not null then 0
            when facts.new_reply or facts.recovery_overdue or inquiry.reply_overdue or facts.recovery_confirmed
              or appeal.report_id is not null or facts.active_p2p_dispute
              or pickup.next_pickup is not null then 1
            else 2 end as priority_rank,
          case when step.waiting_reason is not null then null
            when risk.urgent_reason is not null then risk.urgent_reason
            when facts.new_reply and decisions.recovery_requested then 'RECOVERY_REPLY'
            when facts.new_reply then 'REPLIED'
            when facts.recovery_overdue then 'RECOVERY_OVERDUE'
            when inquiry.reply_overdue then 'REPLY_OVERDUE'
            when facts.recovery_confirmed then 'RECOVERY_CONFIRMED'
            when appeal.report_id is not null then 'CLAIM_APPEAL'
            when facts.active_p2p_dispute then 'P2P_ACTIVE'
            when pickup.next_pickup is not null then 'OFFICE_UPCOMING'
            when r.kind='HANDOVER_ISSUE' then 'HANDOVER'
          end as priority_reason
      ) queue_state
      """.formatted(OTHER_APPEALS_PENDING, ACTIVE_APPROVAL, REOPEN_ALLOWED);
}
