package com.example.base.dto.claim;

import java.time.Instant;
import java.util.List;

/** Owner-only reading aid. Never a correctness or ownership score. */
public record ClaimReviewSummaryDto(String mode, Instant generatedAt, boolean cached, List<Row> rows) {
    public record Row(Long claimId, List<Cell> cells) {}
    public record Cell(String criterion, String label, List<String> points, List<Source> sources) {}
    public record Source(String templateCode, int templateVersion, String questionCode) {}
}
