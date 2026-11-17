package com.example.base.service.claim;

import com.example.base.dto.claim.ClaimReviewSummaryDto;
import com.example.base.dto.claim.ClaimReviewSummaryDto.*;
import com.example.base.security.CustomUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

@Service
public class ClaimSummaryService {
    private final ClaimService claims;
    private final ClaimSummarySanitizer sanitizer;
    private final GeminiClaimSummaryClient provider;
    private final Clock clock;
    private final Map<Key, Cached> cache = new LinkedHashMap<>(128, .75f, true);
    private final ConcurrentMap<Key, CompletableFuture<Cached>> pending = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(2);
    private Instant minute = Instant.EPOCH;
    private LocalDate day = LocalDate.MIN;
    private int minuteCalls, dayCalls;
    private record Key(long owner, long post, int page, String status, List<Long> ids, List<ClaimSummarySanitizer.SafeRow> rows) {}
    private record Cached(String mode, Instant created, Instant expires, List<List<Cell>> cells) {}

    @org.springframework.beans.factory.annotation.Autowired
    public ClaimSummaryService(ClaimService claims, ClaimSummarySanitizer sanitizer, GeminiClaimSummaryClient provider) {
        this(claims, sanitizer, provider, Clock.systemUTC());
    }
    ClaimSummaryService(ClaimService claims, ClaimSummarySanitizer sanitizer, GeminiClaimSummaryClient provider, Clock clock) {
        this.claims = claims; this.sanitizer = sanitizer; this.provider = provider; this.clock = clock;
    }

    public ClaimReviewSummaryDto summarize(long postId, int page, int size, String status, CustomUserDetails user) {
        // The existing owner, account-state, post-type and routing checks run BEFORE cache lookup or provider access.
        // Its read transaction completes before any network operation here.
        var comparison = claims.comparison(postId, page, size, status, user);
        var safe = sanitizer.sanitize(comparison.content());
        var ids = comparison.content().stream().map(e -> e.id()).toList();
        var key = new Key(user.getId(), postId, page, status, ids, safe);
        Cached result;
        synchronized (cache) {
            result = cache.get(key);
            if (result != null && result.expires().isAfter(clock.instant())) return dto(result, ids, true);
        }
        var mine = new CompletableFuture<Cached>();
        var other = pending.putIfAbsent(key, mine);
        if (other != null) return dto(other.join(), ids, true);
        try {
            // A concurrent request may have published its result after our first cache lookup.
            synchronized (cache) {
                result = cache.get(key);
                if (result != null && result.expires().isAfter(clock.instant())) {
                    mine.complete(result);
                    return dto(result, ids, true);
                }
            }
            result = generate(safe);
            synchronized (cache) {
                cache.put(key, result);
                while (cache.size() > 128) cache.remove(cache.keySet().iterator().next());
            }
            mine.complete(result);
            return dto(result, ids, false);
        } catch (RuntimeException ex) { mine.completeExceptionally(ex); throw ex; }
        finally { pending.remove(key, mine); }
    }

    private Cached generate(List<ClaimSummarySanitizer.SafeRow> safe) {
        var cells = fallback(safe);
        String mode = "EXTRACTIVE";
        boolean hasFacts = safe.stream().anyMatch(r -> r.criteria().values().stream().anyMatch(f -> !f.isEmpty()));
        if (hasFacts && provider.available() && slots.tryAcquire()) {
            try {
                if (reserveBudget()) { cells = grounded(provider.select(safe), safe); mode = "GEMINI"; }
            } catch (RuntimeException ignored) {
                // Never log provider exceptions: vendor bodies or headers can contain private content or credentials.
            } finally { slots.release(); }
        }
        Instant now = clock.instant();
        return new Cached(mode, now, now.plusSeconds(mode.equals("GEMINI") ? 1800 : 60), cells);
    }

    /** Per-instance guardrails, not a promise about the provider's free allowance. No automatic retries. */
    private synchronized boolean reserveBudget() {
        Instant now = clock.instant();
        if (minute.plusSeconds(60).isBefore(now)) { minute = now; minuteCalls = 0; }
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) { day = today; dayCalls = 0; }
        if (minuteCalls >= 3 || dayCalls >= 100) return false;
        minuteCalls++; dayCalls++; return true;
    }

    private ClaimReviewSummaryDto dto(Cached cached, List<Long> ids, boolean hit) {
        return new ClaimReviewSummaryDto(cached.mode(), cached.created(), hit,
                IntStream.range(0, ids.size()).mapToObj(i -> new Row(ids.get(i), cached.cells().get(i))).toList());
    }

    static List<List<Cell>> fallback(List<ClaimSummarySanitizer.SafeRow> rows) {
        return rows.stream().map(row -> IntStream.range(0, 3).mapToObj(i -> cell(i,
                row.criteria().get(ClaimSummarySanitizer.CRITERIA.get(i)).stream().limit(3).toList())).toList()).toList();
    }

    /** Model output can only select existing facts in the SAME row and criterion. No generated prose reaches the UI. */
    static List<List<Cell>> grounded(JsonNode output, List<ClaimSummarySanitizer.SafeRow> rows) {
        if (output == null || !output.isObject() || output.size() != 1 || !output.path("rows").isArray()
                || output.path("rows").size() != rows.size()) throw new IllegalArgumentException("Invalid summary rows");
        var result = new ArrayList<List<Cell>>();
        for (int r = 0; r < rows.size(); r++) {
            var safe = rows.get(r); var selected = output.path("rows").get(r);
            if (!selected.isObject() || selected.size() != 4 || !safe.alias().equals(selected.path("alias").asText()))
                throw new IllegalArgumentException("Invalid summary alias");
            var cells = new ArrayList<Cell>();
            for (int c = 0; c < 3; c++) {
                String criterion = ClaimSummarySanitizer.CRITERIA.get(c);
                var facts = safe.criteria().get(criterion);
                var chosen = selected.path(criterion);
                if (!chosen.isArray() || chosen.size() > 3 || (!facts.isEmpty() && chosen.isEmpty()))
                    throw new IllegalArgumentException("Invalid summary criterion");
                var used = new HashSet<String>(); var matches = new ArrayList<ClaimSummarySanitizer.Fact>();
                for (var id : chosen) {
                    if (!id.isTextual() || !used.add(id.asText())) throw new IllegalArgumentException("Invalid summary fact");
                    matches.add(facts.stream().filter(f -> f.id().equals(id.asText())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Ungrounded summary fact")));
                }
                cells.add(cell(c, matches));
            }
            result.add(List.copyOf(cells));
        }
        return List.copyOf(result);
    }
    private static Cell cell(int index, List<ClaimSummarySanitizer.Fact> facts) {
        return new Cell(ClaimSummarySanitizer.CRITERIA.get(index), ClaimSummarySanitizer.LABELS.get(index),
                facts.stream().map(f -> f.text()).toList(), facts.stream().map(f -> f.source()).distinct().toList());
    }
}
