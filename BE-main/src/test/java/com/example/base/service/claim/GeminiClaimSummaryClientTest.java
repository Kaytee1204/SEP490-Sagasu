package com.example.base.service.claim;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static com.example.base.service.claim.ClaimSummarySanitizerTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClaimSummaryClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private GeminiClaimSummaryClient client(boolean enabled, String key, String model) {
        return new GeminiClaimSummaryClient(json, enabled, key, model, 2000);
    }
    @Test void explicitOptInAndKeyAndValidModelAreAllRequired() {
        assertFalse(client(false, "key", "gemini-3.5-flash-lite").available());
        assertFalse(client(true, " ", "gemini-3.5-flash-lite").available());
        assertFalse(client(true, "key", "gemini-x/../../other-host").available());
        assertTrue(client(true, "key", "gemini-3.5-flash-lite").available());
    }
    @Test void sendsKeyInHeaderAndStructuredSafeInputOnly() {
        var client = client(true, "test-key", "gemini-3.5-flash-lite");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"rows\\\":[]}\"}]}}]}", MediaType.APPLICATION_JSON));
        assertTrue(client.select(new ClaimSummarySanitizer().sanitize(List.of(entry(99, answer("PHONE", "Q1", "Samsung"))))).path("rows").isArray());
        server.verify();
    }
    @Test void incompleteOrBlockedOutputIsNotASuccess() {
        var client = client(true, "test-key", "gemini-3.5-flash-lite");
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> client.select(List.of())); server.verify();
    }
}
