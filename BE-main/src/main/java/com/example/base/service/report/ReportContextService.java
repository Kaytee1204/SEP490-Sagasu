package com.example.base.service.report;

import com.example.base.storage.ImageStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Explicit field projections keep credentials and invitation tokens out of investigation responses. */
@Service
@RequiredArgsConstructor
public class ReportContextService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ImageStorage storage;

  public Map<String, Object> context(Map<String, Object> report) {
    Map<String, Object> result = new LinkedHashMap<>();
    Long claim = id(report, "claim_id"),
      post = id(report, "post_id"),
      message = id(report, "message_id"),
      target = id(report, "reported_account_id");
    if (message != null) {
      var m = one(
        "select message_id,conversation_id,sender_id,sequence_no,message_type,body,created_at,read_at,location_label from chat_message where message_id=?",
        message
      );
      result.put("message", m);
      result.put(
        "messageAttachments",
        jdbc.queryForList(
          "select storage_key,original_name from chat_attachment where message_id=? order by sort_order",
          message
        )
      );
      if (!m.isEmpty()) {
        var conversation = one(
          "select post_id,claimant_id,latest_claim_id from chat_conversation where conversation_id=?",
          m.get("conversation_id")
        );
        post = id(conversation, "post_id");
        // A reused conversation can contain several claims; match the one at the message time.
        claim = id(
          one(
            "select claim_id from claim where post_id=? and claimant_id=? and created_at<=? order by created_at desc,claim_id desc limit 1",
            post,
            conversation.get("claimant_id"),
            m.get("created_at")
          ),
          "claim_id"
        );
      }
    }
    if (claim != null) {
      var c = one(
        "select claim_id,post_id,claimant_id,created_by,claimant_name,claimant_email,description,evidence_note,status,claim_type,submission_channel,item_receipt_id,created_at,updated_at,closed_at,close_reason from claim where claim_id=?",
        claim
      );
      result.put("claim", c);
      post = id(c, "post_id");
      result.put(
        "answers",
        jdbc.queryForList(
          "select question_label,answer_text,section_title,sort_order from claim_answer where claim_id=? order by sort_order",
          claim
        )
      );
      result.put(
        "claimEvidence",
        jdbc.queryForList(
          "select storage_key,original_name,uploaded_at from claim_evidence where claim_id=? order by sort_order",
          claim
        )
      );
      result.put(
        "claimHistory",
        jdbc.queryForList(
          "select x.claim_activity_id,x.action,x.from_status,x.to_status,x.reason,x.created_at,a.full_name as actor from claim_activity x join accounts a on a.id=x.actor_id where claim_id=? order by x.created_at,x.claim_activity_id",
          claim
        )
      );
      result.put(
        "appointments",
        jdbc.queryForList(
          "select appointment_id,scheduled_at,location,status,note,cancellation_reason,created_at,email_sent_at,response_deadline,response_status,responded_at from appointment where claim_id=? order by appointment_id",
          claim
        )
      );
      result.put(
        "verifications",
        jdbc.queryForList(
          "select v.verification_id,v.appointment_id,v.result,v.note,v.verified_at,a.full_name as officer from office_claim_verification v join accounts a on a.id=v.verified_by where claim_id=? order by verification_id",
          claim
        )
      );
      result.put(
        "p2pHandover",
        one(
          "select h.handover_id,h.status,h.recorded_by,h.recorded_at,h.note,h.confirmed_by,h.confirmed_at,a.full_name as recorded_by_name,b.full_name as confirmed_by_name from p2p_handover h join accounts a on a.id=h.recorded_by left join accounts b on b.id=h.confirmed_by where h.claim_id=?",
          claim
        )
      );
      result.put(
        "officeHandover",
        one(
          "select h.handover_id,h.item_receipt_id,h.verification_id,h.recorded_by,h.recipient_id,h.recorded_at,h.condition_note,h.status,h.confirmed_at,h.issue_note,a.full_name as recorded_by_name,b.full_name as recipient_name from office_handover h join accounts a on a.id=h.recorded_by left join accounts b on b.id=h.recipient_id where h.claim_id=?",
          claim
        )
      );
      result.put(
        "handoverEvidence",
        jdbc.queryForList(
          "select e.storage_key,e.original_name from p2p_handover_evidence e join p2p_handover h using(handover_id) where h.claim_id=? union all select e.storage_key,e.original_name from office_handover_evidence e join office_handover h using(handover_id) where h.claim_id=?",
          claim,
          claim
        )
      );
    }
    if (post != null) {
      result.put(
        "post",
        one(
          "select post_id,account_id,title,description,post_type,status,custody_mode,moderation_status,moderation_note,location_text,event_at,created_at,updated_at,deleted_at from post where post_id=?",
          post
        )
      );
      result.put(
        "postImages",
        jdbc.queryForList(
          "select storage_key,image_url from post_image where post_id=? order by sort_order",
          post
        )
      );
      result.put(
        "receipt",
        one(
          "select r.item_receipt_id,r.receipt_code,r.source,r.private_identifiers,r.condition_at_receipt,r.recorded_at,r.received_at,r.finder_name,r.finder_contact,r.status,r.note,a.full_name as received_by,l.name as storage_location from item_receipt r left join accounts a on a.id=r.received_by left join storage_location l on l.storage_location_id=r.storage_location_id where r.post_id=?",
          post
        )
      );
      result.put(
        "relatedClaims",
        jdbc.queryForList(
          "select c.claim_id,c.status,c.claim_type,c.created_at,coalesce(c.claimant_name,a.full_name) as claimant_name from claim c left join accounts a on a.id=c.claimant_id where c.post_id=? order by c.created_at desc",
          post
        )
      );
      result.put(
        "transfers",
        jdbc.queryForList(
          "select transfer_id,status,requested_at,processed_at,requested_by,processed_by,reason,reconciliation_note from office_transfer where post_id=? order by transfer_id",
          post
        )
      );
      result.put(
        "intakeAppointments",
        jdbc.queryForList(
          "select a.scheduled_at,a.location,a.note,a.status,a.response_status,a.responded_at,a.cancellation_reason from office_intake_appointment a join office_transfer t using(transfer_id) where t.post_id=? order by a.appointment_id",
          post
        )
      );
    }
    if (target != null) result.put(
      "account",
      one(
        "select id,full_name,email,role,status,created_at from accounts where id=?",
        target
      )
    );
    result.put("participants", participants(report, post, claim, message));
    return result;
  }

  public List<Map<String, Object>> participants(
    Map<String, Object> report,
    Long post,
    Long claim,
    Long message
  ) {
    Set<Long> ids = new LinkedHashSet<>();
    ids.add(id(report, "reporter_id"));
    ids.add(id(report, "reported_account_id"));
    if (post != null) {
      ids.add(
        id(
          one("select account_id from post where post_id=?", post),
          "account_id"
        )
      );
      jdbc
        .queryForList(
          "select received_by,recorded_by,reviewed_by from item_receipt where post_id=?",
          post
        )
        .forEach(r -> {
          ids.add(id(r, "received_by"));
          ids.add(id(r, "recorded_by"));
          ids.add(id(r, "reviewed_by"));
        });
    }
    if (claim != null) {
      var c = one(
        "select claimant_id,created_by from claim where claim_id=?",
        claim
      );
      ids.add(id(c, "claimant_id"));
      ids.add(id(c, "created_by"));
      jdbc
        .queryForList(
          "select recorded_by,recipient_id from office_handover where claim_id=?",
          claim
        )
        .forEach(r -> {
          ids.add(id(r, "recorded_by"));
          ids.add(id(r, "recipient_id"));
        });
      jdbc
        .queryForList(
          "select verified_by from office_claim_verification where claim_id=?",
          claim
        )
        .forEach(r -> ids.add(id(r, "verified_by")));
    }
    if (message != null) ids.add(
      id(
        one("select sender_id from chat_message where message_id=?", message),
        "sender_id"
      )
    );
    ids.remove(null);
    return ids
      .stream()
      .map(i ->
        one("select id,full_name,email,role,status from accounts where id=?", i)
      )
      .filter(p -> !p.isEmpty())
      .toList();
  }

  public void capture(long reportId, String source) {
    if (
      Boolean.TRUE.equals(
        jdbc.queryForObject(
          "select exists(select 1 from report_snapshot where report_id=?)",
          Boolean.class,
          reportId
        )
      )
    ) return;
    // Serialize source-image edits with snapshot retention; source cleanup runs after its post transaction.
    jdbc.queryForList(
      "select p.post_id from report r left join claim c on c.claim_id=r.claim_id left join chat_message m on m.message_id=r.message_id left join chat_conversation x on x.conversation_id=m.conversation_id join post p on p.post_id=coalesce(r.post_id,c.post_id,x.post_id) where r.report_id=? for update of p",
      reportId
    );
    var payload = context(
      one("select * from report where report_id=?", reportId)
    );
    jdbc.update(
      "insert into report_snapshot(report_id,capture_source,payload) values(?,?,?::jsonb) on conflict do nothing",
      reportId,
      source,
      json(payload)
    );
    retainAssets(reportId, payload, "CONTEXT");
  }

  @SuppressWarnings("unchecked")
  public void retainAssets(long reportId, Object value, String source) {
    if (value instanceof Map<?, ?> row) {
      Object key = row.get("storage_key");
      if (key instanceof String s && !s.isBlank()) jdbc.update(
        "insert into report_asset(report_id,storage_key,original_name,source) values(?,?,?,?) on conflict do nothing",
        reportId,
        s,
        row.get("original_name"),
        source
      );
      row.values().forEach(v -> retainAssets(reportId, v, source));
    } else if (value instanceof List<?> list) list.forEach(v ->
      retainAssets(reportId, v, source)
    );
  }

  public Object withUrls(Object value) {
    if (value instanceof Map<?, ?> map) {
      var out = new LinkedHashMap<String, Object>();
      map.forEach((k, v) -> out.put(k.toString(), withUrls(v)));
      if (map.containsKey("storage_key")) {
        out.put(
          "url",
          storage.readUrl(
            (String) map.get("storage_key"),
            (String) map.get("image_url")
          )
        );
        out.remove("storage_key");
      }
      return out;
    }
    if (value instanceof List<?> list) return list
      .stream()
      .map(this::withUrls)
      .toList();
    return value;
  }

  public Map<String, Object> snapshot(long id) {
    var row = one(
      "select captured_at,capture_source,payload::text from report_snapshot where report_id=?",
      id
    );
    if (!row.isEmpty()) row.put(
      "payload",
      withUrls(parse((String) row.get("payload")))
    );
    return row;
  }

  public String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> parse(String value) {
    try {
      return mapper.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> one(String sql, Object... args) {
    return jdbc
      .queryForList(sql, args)
      .stream()
      .findFirst()
      .orElseGet(LinkedHashMap::new);
  }

  public static Long id(Map<String, Object> row, String key) {
    return row.get(key) instanceof Number n ? n.longValue() : null;
  }
}
