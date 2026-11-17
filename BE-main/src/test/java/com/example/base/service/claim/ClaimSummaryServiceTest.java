package com.example.base.service.claim;

import com.example.base.dto.claim.*;
import com.example.base.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.base.service.claim.ClaimSummarySanitizerTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimSummaryServiceTest {
    private final ClaimService claims = mock(ClaimService.class);
    private final GeminiClaimSummaryClient provider = mock(GeminiClaimSummaryClient.class);
    private final CustomUserDetails owner = mock(CustomUserDetails.class);
    private final ClaimSummarySanitizer sanitizer = new ClaimSummarySanitizer();
    private final ObjectMapper json = new ObjectMapper();
    private final ClaimSummaryService service = new ClaimSummaryService(claims, sanitizer, provider,
            Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));
    private ClaimComparisonDto comparison(long id) {
        return new ClaimComparisonDto(List.of(entry(id, answer("PHONE", "Q1", "Samsung"))), 1, 5, 1, 1);
    }
    @BeforeEach void setup() {
        when(owner.getId()).thenReturn(10L);
        when(claims.comparison(1, 1, 5, "ALL", owner)).thenReturn(comparison(13));
    }
    private ClaimReviewSummaryDto summary() { return service.summarize(1, 1, 5, "ALL", owner); }

    @Test void noKeyUsesUsefulLocalSummaryAndNeverCallsProvider() {
        var result = summary();
        assertEquals("EXTRACTIVE", result.mode());
        assertEquals(List.of("Samsung"), result.rows().getFirst().cells().getFirst().points());
        verify(provider, never()).select(any());
    }
    @Test void authorizedCacheHitStillRechecksAccountAndOwnership() {
        summary(); assertTrue(summary().cached());
        when(claims.comparison(1, 1, 5, "ALL", owner)).thenThrow(new IllegalStateException("no access"));
        assertThrows(IllegalStateException.class, this::summary);
        verify(provider, never()).select(any());
    }
    @Test void invalidModelFactsFallBackWithoutLeakingProviderText() throws Exception {
        when(provider.available()).thenReturn(true);
        when(provider.select(any())).thenReturn(json.readTree("{\"rows\":[{\"alias\":\"R1\",\"identity\":[\"Personal secret\"],\"appearance\":[],\"accessories\":[]}]}"));
        assertEquals("EXTRACTIVE", summary().mode());
        assertEquals(List.of("Samsung"), summary().rows().getFirst().cells().getFirst().points());
        verify(provider, times(1)).select(any());
    }
    @Test void validGroundedSelectionReturnsGeminiModeAndOriginalClaimMapping() throws Exception {
        when(provider.available()).thenReturn(true);
        when(provider.select(any())).thenReturn(json.readTree("{\"rows\":[{\"alias\":\"R1\",\"identity\":[\"F0\"],\"appearance\":[],\"accessories\":[]}]}"));
        var result = summary();
        assertEquals("GEMINI", result.mode()); assertEquals(13L, result.rows().getFirst().claimId());
        assertTrue(summary().cached()); verify(provider, times(1)).select(any());
    }
    @Test void upstreamQuotaOrNetworkFailureIsCachedLocalFallback() {
        when(provider.available()).thenReturn(true);
        when(provider.select(any())).thenThrow(new IllegalStateException("429 private vendor body"));
        assertEquals("EXTRACTIVE", summary().mode()); assertTrue(summary().cached());
        verify(provider, times(1)).select(any());
    }
    @Test void changedDossierInvalidatesCacheAndBudgetStopsFourthCall() {
        when(provider.available()).thenReturn(true);
        when(provider.select(any())).thenThrow(new IllegalStateException("unavailable"));
        for (int id = 1; id <= 4; id++) {
            when(claims.comparison(1, 1, 5, "ALL", owner)).thenReturn(comparison(id));
            assertEquals((long) id, summary().rows().getFirst().claimId());
        }
        verify(provider, times(3)).select(any());
    }
    @Test void privateOnlyAnswersNeverTriggerCloud() {
        when(provider.available()).thenReturn(true);
        when(claims.comparison(1, 1, 5, "ALL", owner)).thenReturn(new ClaimComparisonDto(
                List.of(entry(1, answer("ID_CARD", "Q2", "Nguyễn Văn A"))), 1, 5, 1, 1));
        assertEquals("EXTRACTIVE", summary().mode());
        verify(provider, never()).select(any());
    }
    @Test void rejectsCrossRowOrCrossCriterionFactsAndOmittedNonEmptyCriteria() throws Exception {
        var rows = sanitizer.sanitize(List.of(entry(1, answer("PHONE", "Q1", "Samsung")), entry(2, answer("PHONE", "Q1", "Xiaomi"))));
        var invalid = json.readTree("{\"rows\":[{\"alias\":\"R1\",\"identity\":[\"F1\"],\"appearance\":[],\"accessories\":[]},{\"alias\":\"R2\",\"identity\":[\"F0\"],\"appearance\":[],\"accessories\":[]}]}" );
        assertThrows(IllegalArgumentException.class, () -> ClaimSummaryService.grounded(invalid, rows));
        var missing = json.readTree("{\"rows\":[{\"alias\":\"R1\",\"identity\":[],\"appearance\":[],\"accessories\":[]}]}" );
        assertThrows(IllegalArgumentException.class, () -> ClaimSummaryService.grounded(missing, rows.subList(0, 1)));
    }
    @Test void simultaneousRequestsShareOneUpstreamCall() throws Exception {
        when(provider.available()).thenReturn(true);
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        when(provider.select(any())).thenAnswer(invocation -> {
            entered.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS));
            return json.readTree("{\"rows\":[{\"alias\":\"R1\",\"identity\":[\"F0\"],\"appearance\":[],\"accessories\":[]}]}" );
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(this::summary); assertTrue(entered.await(5, TimeUnit.SECONDS));
            var two = pool.submit(this::summary); finish.countDown();
            assertEquals("GEMINI", one.get(5, TimeUnit.SECONDS).mode());
            assertEquals("GEMINI", two.get(5, TimeUnit.SECONDS).mode());
            verify(provider, times(1)).select(any());
        }
    }
}
