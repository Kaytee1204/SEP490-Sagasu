package com.example.base.service.report;

import com.example.base.storage.ImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static com.example.base.service.report.SqlStub.row;
import static com.example.base.service.report.SqlStub.rows;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportContextServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock ImageStorage storage;
    ReportContextService service;

    @BeforeEach
    void setUp() {
        service = new ReportContextService(jdbc, new ObjectMapper(), storage);
        SqlStub.defaults(jdbc);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> participantIds(Map<String, Object> ctx) {
        return ((List<Map<String, Object>>) ctx.get("participants")).stream()
                .map(p -> ((Number) p.get("id")).longValue()).toList();
    }

    @Test
    void utc035_context_postReport_loadsPostSectionAndParticipants() {
        rows(jdbc, "from post where post_id", row("post_id", 10L, "account_id", 8L, "title", "Wallet", "post_type", "FOUND"));
        rows(jdbc, "select account_id from post", row("account_id", 8L));
        rows(jdbc, "from item_receipt where post_id", row("received_by", 3L, "recorded_by", null, "reviewed_by", null));
        rows(jdbc, "from accounts where id", row("id", 1L));
        when(jdbc.queryForList(contains("select id,full_name,email,role,status from accounts"), any(Object[].class)))
                .thenAnswer(inv -> List.of(row("id", inv.getArgument(1))));

        Map<String, Object> ctx = service.context(row("report_id", 1L, "reporter_id", 7L, "post_id", 10L));

        assertThat(ctx).containsKeys("post", "postImages", "receipt", "relatedClaims", "transfers", "intakeAppointments", "participants");
        assertThat(ctx).doesNotContainKeys("claim", "message", "account");
        assertThat(participantIds(ctx)).containsExactly(7L, 8L, 3L);
    }

    @Test
    void utc036_context_claimReport_loadsClaimAndDerivedPost() {
        rows(jdbc, "from claim where claim_id", row("claim_id", 20L, "post_id", 10L, "claimant_id", 7L, "created_by", 7L));
        rows(jdbc, "from post where post_id", row("post_id", 10L));

        Map<String, Object> ctx = service.context(row("report_id", 1L, "reporter_id", 7L, "claim_id", 20L));

        assertThat(ctx).containsKeys("claim", "answers", "claimEvidence", "claimHistory", "appointments",
                "verifications", "p2pHandover", "officeHandover", "handoverEvidence", "post");
        verify(jdbc).queryForList(contains("post_type,status,custody_mode"), eq(10L));
    }

    @Test
    void utc037_context_messageReport_resolvesConversationClaimAtMessageTime() {
        rows(jdbc, "from chat_message where message_id", row("message_id", 30L, "conversation_id", 5L, "created_at", "t", "sender_id", 8L));
        rows(jdbc, "from chat_conversation where conversation_id", row("post_id", 10L, "claimant_id", 7L));
        rows(jdbc, "select claim_id from claim where post_id", row("claim_id", 20L));
        rows(jdbc, "evidence_note,status", row("claim_id", 20L, "post_id", 10L));

        Map<String, Object> ctx = service.context(row("report_id", 1L, "reporter_id", 7L, "message_id", 30L));

        assertThat(ctx).containsKeys("message", "messageAttachments", "claim", "post");
        verify(jdbc).queryForList(contains("evidence_note,status"), eq(20L));
    }

    @Test
    void utc038_context_messageMissing_noConversationLookup() {
        Map<String, Object> ctx = service.context(row("report_id", 1L, "reporter_id", 7L, "message_id", 30L));

        assertThat(ctx).containsKey("message").doesNotContainKey("claim");
        verify(jdbc, never()).queryForList(contains("from chat_conversation"), any(Object[].class));
    }

    @Test
    void utc039_context_userReport_loadsAccountOnly() {
        rows(jdbc, "role,status,created_at from accounts", row("id", 8L, "full_name", "B"));

        Map<String, Object> ctx = service.context(row("report_id", 1L, "reporter_id", 7L, "reported_account_id", 8L));

        assertThat(ctx).containsKeys("account", "participants").doesNotContainKeys("post", "claim");
    }

    @Test
    void utc040_capture_snapshotExists_skipped() {
        when(jdbc.queryForObject(contains("report_snapshot"), eq(Boolean.class), any(Object[].class))).thenReturn(true);

        service.capture(1L, "SUBMISSION");

        verify(jdbc, never()).update(contains("insert into report_snapshot"), any(Object[].class));
    }

    @Test
    void utc041_capture_newSnapshot_insertsJsonAndRetainsAssets() {
        when(jdbc.queryForObject(contains("report_snapshot"), eq(Boolean.class), any(Object[].class))).thenReturn(false);
        rows(jdbc, "select * from report where report_id", row("report_id", 1L, "reporter_id", 7L, "post_id", 10L));
        rows(jdbc, "from post_image where post_id", row("storage_key", "posts/a.jpg", "image_url", null));

        service.capture(1L, "SUBMISSION");

        verify(jdbc).update(contains("insert into report_snapshot"), eq(1L), eq("SUBMISSION"), anyString());
        verify(jdbc).update(contains("insert into report_asset"), eq(1L), eq("posts/a.jpg"), isNull(), eq("CONTEXT"));
    }

    @Test
    void utc042_retainAssets_nestedMapsAndLists_blankKeySkipped() {
        Object value = row("a", List.of(row("storage_key", "k1", "original_name", "1.jpg"), row("storage_key", " ")),
                "b", row("storage_key", "k2"));

        service.retainAssets(9L, value, "DECISION");

        verify(jdbc).update(contains("insert into report_asset"), eq(9L), eq("k1"), eq("1.jpg"), eq("DECISION"));
        verify(jdbc).update(contains("insert into report_asset"), eq(9L), eq("k2"), isNull(), eq("DECISION"));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void utc043_withUrls_replacesStorageKeyWithUrl() {
        when(storage.readUrl("k1", "fallback")).thenReturn("https://cdn/k1");

        Object out = service.withUrls(List.of(row("storage_key", "k1", "image_url", "fallback", "name", "x")));

        @SuppressWarnings("unchecked")
        Map<String, Object> first = ((List<Map<String, Object>>) out).get(0);
        assertThat(first).containsEntry("url", "https://cdn/k1").containsEntry("name", "x").doesNotContainKey("storage_key");
        assertThat(service.withUrls("plain")).isEqualTo("plain");
    }

    @Test
    void utc044_snapshot_parsesPayload_orEmptyWhenMissing() {
        assertThat(service.snapshot(1L)).isEmpty();

        rows(jdbc, "from report_snapshot where report_id", row("capture_source", "SUBMISSION", "payload", "{\"post\":{\"title\":\"W\"}}"));
        Map<String, Object> snap = service.snapshot(1L);
        assertThat(snap.get("payload")).isInstanceOf(Map.class);
        assertThat(snap.get("payload").toString()).contains("title=W");
    }

    @Test
    void utc045_jsonAndParse_roundTrip_invalidJsonThrows() {
        String json = service.json(Map.of("a", 1));
        assertThat(service.parse(json)).containsEntry("a", 1);
        assertThatThrownBy(() -> service.parse("{bad")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void utc046_id_numberOrNull() {
        assertThat(ReportContextService.id(row("x", 5), "x")).isEqualTo(5L);
        assertThat(ReportContextService.id(row("x", "5"), "x")).isNull();
        assertThat(ReportContextService.id(row(), "x")).isNull();
    }
}
