package com.example.base.service.claim;

import com.example.base.dto.claim.ClaimComparisonDto.Completion;
import com.example.base.entity.Claim;
import com.example.base.entity.ClaimAnswer;
import com.example.base.entity.ClaimQuestion;
import com.example.base.entity.ClaimQuestionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimReviewCompletenessTest {
    private final ObjectMapper json = new ObjectMapper();

    private ClaimQuestion question(String code, String type) {
        var template = BeanUtils.instantiateClass(ClaimQuestionTemplate.class);
        ReflectionTestUtils.setField(template, "code", "COMMON");
        ReflectionTestUtils.setField(template, "title", "Questions");
        ReflectionTestUtils.setField(template, "version", 1);
        var question = BeanUtils.instantiateClass(ClaimQuestion.class);
        ReflectionTestUtils.setField(question, "template", template);
        ReflectionTestUtils.setField(question, "code", code);
        ReflectionTestUtils.setField(question, "label", code);
        ReflectionTestUtils.setField(question, "inputType", type);
        ReflectionTestUtils.setField(question, "options", json.createArrayNode());
        return question;
    }

    private ClaimAnswer answer(ClaimQuestion question, String text) { return new ClaimAnswer(question, text, 1); }

    private void condition(ClaimQuestion child, String parent, String value) {
        ReflectionTestUtils.setField(child, "visibility", json.valueToTree(Map.of("anyOf", List.of(Map.of("questionCode", parent, "values", List.of(value))))));
    }

    @Test void countsOptionalAnswersAndDescriptionButNotImages() {
        var date = question("C1", "LOST_DATE");
        var description = question("C3", "DESCRIPTION");
        var image = question("C4", "EVIDENCE");
        var optional = question("P2", "TEXT");
        var count = ClaimReviewCompleteness.calculate("Căn cứ", List.of(answer(date, "Không nhớ rõ thời điểm mất")), List.of(date, description, image, optional));
        assertEquals(new Completion(2, 3), count);
    }

    @Test void nonApplicableBranchDoesNotLowerCompletion() {
        var type = question("P1", "CHOICE");
        ReflectionTestUtils.setField(type, "options", json.valueToTree(List.of(Map.of("value", "BOTTLE", "label", "Bình nước"), Map.of("value", "UMBRELLA", "label", "Ô"))));
        var lid = question("P2", "TEXT");
        condition(lid, "P1", "BOTTLE");
        assertEquals(new Completion(1, 1), ClaimReviewCompleteness.calculate("", List.of(answer(type, "Ô")), List.of(type, lid)));
        assertEquals(new Completion(1, 2), ClaimReviewCompleteness.calculate("", List.of(answer(type, "Bình nước")), List.of(type, lid)));
    }

    @Test void choiceDetailsDoNotBreakBranchSelection() {
        var type = question("P1", "CONTENTS");
        ReflectionTestUtils.setField(type, "options", json.valueToTree(List.of(Map.of("value", "OTHER", "label", "Khác"))));
        var detail = question("P2", "TEXT");
        condition(detail, "P1", "OTHER");
        assertEquals(new Completion(2, 2), ClaimReviewCompleteness.calculate("", List.of(answer(type, "Khác\nMô tả thêm"), answer(detail, "Dấu riêng")), List.of(type, detail)));
    }

    @Test void hiddenParentCannotActivateNestedQuestion() {
        var root = question("P1", "CHOICE");
        ReflectionTestUtils.setField(root, "options", json.valueToTree(List.of(Map.of("value", "NO", "label", "Không"))));
        var child = question("P2", "CHOICE");
        ReflectionTestUtils.setField(child, "options", json.valueToTree(List.of(Map.of("value", "YES", "label", "Có"))));
        condition(child, "P1", "YES");
        var nested = question("P3", "TEXT");
        condition(nested, "P2", "YES");
        assertEquals(new Completion(1, 1), ClaimReviewCompleteness.calculate("", List.of(answer(root, "Không"), answer(child, "Có"), answer(nested, "Stale")), List.of(root, child, nested)));
    }

    @Test void suppliedUnknownOrNoAnswerIsStillAnAnswerNotOwnershipProof() {
        var a = question("P1", "TEXT");
        var b = question("P2", "TEXT");
        assertEquals(new Completion(2, 2), ClaimReviewCompleteness.calculate("", List.of(answer(a, "không có"), answer(b, "không nhớ")), List.of(a, b)));
    }

    @Test void blankAnswersAndWhitespaceDescriptionAreNotFilled() {
        var a = question("P1", "TEXT");
        var b = question("C3", "DESCRIPTION");
        assertEquals(new Completion(0, 2), ClaimReviewCompleteness.calculate("  ", List.of(answer(a, " \n ")), List.of(a, b)));
    }

    @Test void legacyDossierHasUnknownDenominator() {
        assertEquals(new Completion(0, 0), ClaimReviewCompleteness.calculate("Legacy description", List.of(), List.of(question("P1", "TEXT"))));
    }

    private Claim claim(long id, String status, int daysAgo) {
        var claim = BeanUtils.instantiateClass(Claim.class);
        ReflectionTestUtils.setField(claim, "id", id);
        ReflectionTestUtils.setField(claim, "status", status);
        ReflectionTestUtils.setField(claim, "createdAt", OffsetDateTime.parse("2026-11-17T12:00:00+07:00").minusDays(daysAgo));
        return claim;
    }

    @Test void fullDossiersMoveAheadBeforePagingAndBranchesAreComparedByRatio() {
        var newestPartial = claim(1, "PENDING", 0);
        var olderFull = claim(2, "PENDING", 2);
        var newestFullDifferentBranch = claim(3, "PENDING", 1);
        var approved = claim(4, "APPROVED", 0);
        var legacy = claim(5, "PENDING", 0);
        var scores = Map.of(1L, new Completion(9, 10), 2L, new Completion(10, 10),
                3L, new Completion(6, 6), 4L, new Completion(10, 10), 5L, new Completion(0, 0));
        var ordered = List.of(newestPartial, olderFull, newestFullDifferentBranch, approved, legacy).stream()
                .sorted(ClaimReviewCompleteness.order(scores)).toList();
        assertEquals(List.of(3L, 2L, 1L, 5L, 4L), ordered.stream().map(Claim::getId).toList());
        assertEquals(List.of(3L, 2L), ordered.stream().limit(2).map(Claim::getId).toList());
        assertEquals(1L, ordered.stream().skip(2).limit(2).findFirst().orElseThrow().getId());
    }
}
