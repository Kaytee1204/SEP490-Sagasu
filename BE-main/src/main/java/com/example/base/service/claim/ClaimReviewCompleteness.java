package com.example.base.service.claim;

import com.example.base.dto.claim.ClaimComparisonDto.Completion;
import com.example.base.entity.Claim;
import com.example.base.entity.ClaimAnswer;
import com.example.base.entity.ClaimQuestion;
import java.util.*;

/** Counts supplied answers, never their truth or the claimant's ownership. */
final class ClaimReviewCompleteness {
    private ClaimReviewCompleteness() {}

    static Completion calculate(String description, List<ClaimAnswer> answers, List<ClaimQuestion> catalog) {
        // Old dossiers do not record a questionnaire version. Do not invent a denominator for them.
        if (answers.isEmpty() || catalog.isEmpty()) return new Completion(0, 0);
        Map<String, ClaimQuestion> definitions = new LinkedHashMap<>();
        for (var question : catalog) definitions.put(question.getCode(), question);
        Map<String, String> texts = new HashMap<>();
        for (var answer : answers) texts.put(answer.getQuestionCode(), answer.getAnswerText());
        int answered = 0;
        int total = 0;
        for (var question : definitions.values()) {
            if ("EVIDENCE".equals(question.getInputType()) || !visible(question, texts, definitions, new HashSet<>())) continue;
            total++;
            String text = "DESCRIPTION".equals(question.getInputType()) ? description : texts.get(question.getCode());
            if (text != null && !text.isBlank()) answered++;
        }
        return new Completion(answered, total);
    }

    private static boolean visible(ClaimQuestion question, Map<String, String> texts,
                                   Map<String, ClaimQuestion> definitions, Set<String> visiting) {
        var rule = question.getVisibility();
        if (rule == null || rule.isNull()) return true;
        if (!visiting.add(question.getCode())) return false;
        try {
            for (var condition : rule.path("anyOf")) {
                String parentCode = condition.path("questionCode").asText();
                var parent = definitions.get(parentCode);
                if (parent == null || !visible(parent, texts, definitions, visiting)) continue;
                String text = texts.get(parentCode);
                if (text == null || text.isBlank()) continue;
                // Choice snapshots contain the server's option label, followed by optional details.
                String label = text.lines().findFirst().orElse("").strip();
                if (parent.getOptions() == null) continue;
                for (var option : parent.getOptions()) {
                    if (!label.equals(option.path("label").asText())) continue;
                    for (var value : condition.path("values")) {
                        if (value.asText().equals(option.path("value").asText())) return true;
                    }
                }
            }
            return false;
        } finally {
            visiting.remove(question.getCode());
        }
    }

    static Comparator<Claim> order(Map<Long, Completion> completions) {
        return Comparator.comparingInt((Claim claim) -> "PENDING".equals(claim.getStatus()) ? 0 : 1)
                .thenComparing(Comparator.comparingDouble((Claim claim) -> {
                    Completion completion = completions.get(claim.getId());
                    return completion.total() == 0 ? -1 : (double) completion.answered() / completion.total();
                }).reversed())
                .thenComparing(Claim::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(Claim::getId, Comparator.reverseOrder());
    }
}
