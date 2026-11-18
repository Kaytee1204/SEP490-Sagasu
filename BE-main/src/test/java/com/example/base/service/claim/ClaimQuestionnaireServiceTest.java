package com.example.base.service.claim;

import com.example.base.dto.claim.ClaimAnswerRequest;
import com.example.base.dto.claim.ClaimQuestionnaireDto;
import com.example.base.dto.claim.SubmitClaimRequest;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import com.example.base.entity.Category;
import com.example.base.entity.ClaimAnswer;
import com.example.base.entity.ClaimQuestion;
import com.example.base.entity.ClaimQuestionTemplate;
import com.example.base.entity.Post;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.ClaimQuestionRepository;
import com.example.base.repository.ClaimQuestionTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** One test per UTC of sheet "Claim" for ClaimQuestionnaireService (UTC083-UTC119). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimQuestionnaireServiceTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ClaimQuestionTemplateRepository templates;
    @Mock
    private ClaimQuestionRepository questions;

    private ObjectMapper mapper;
    private ClaimQuestionnaireService service;
    private Category category;
    private Post post;
    private ClaimQuestionTemplate commonTemplate;
    private ClaimQuestionTemplate categoryTemplate;
    private List<ClaimQuestion> commonQuestions;
    private List<ClaimQuestion> categoryQuestions;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new ClaimQuestionnaireService(templates, questions, mapper);

        category = new Category();
        ReflectionTestUtils.setField(category, "categoryId", 3L);
        category.setName("Electronics");

        Account owner = new Account();
        owner.setId(5L);
        owner.setEmail("owner@gmail.com");
        owner.setFullName("Owner");
        owner.setRole(AccountRole.USER);
        owner.setStatus(AccountStatus.ACTIVE);

        post = Post.found(owner, category, "Phone", "Desc", "Hall", OffsetDateTime.now().minusDays(1), "P2P", null);

        commonTemplate = template(1L, "COMMON", "Thông tin chung", null, 2);
        categoryTemplate = template(2L, "CAT_ELECTRONICS", "Thiết bị điện tử", category, 4);

        ArrayNode yesNo = options(option("Có", "Có", false), option("Không", "Không", false));
        ArrayNode marks = options(option("Trầy xước", "Trầy xước", false), option("Sticker", "Sticker", false));
        commonQuestions = List.of(
                question(11L, commonTemplate, "Q_COMMON_1", "Mô tả màu sắc", "TEXT", true, 1, null, null, false),
                question(12L, commonTemplate, "Q_COMMON_2", "Có phụ kiện không", "CHOICE", true, 2, yesNo, null, true),
                question(13L, commonTemplate, "Q_COMMON_3", "Chi tiết phụ kiện", "TEXT", true, 3, null, visibility("Q_COMMON_2", "Có"), false),
                question(14L, commonTemplate, "Q_COMMON_4", "Dấu hiệu nhận biết", "CONTENTS", false, 4, marks, null, true)
        );
        categoryQuestions = List.of(
                question(21L, categoryTemplate, "Q_CAT_1", "Hình nền", "TEXT", true, 1, null, null, false),
                question(22L, categoryTemplate, "Q_CAT_2", "Ngày mất", "LOST_DATE", false, 2, null, null, false)
        );
        stubActiveQuestionnaire();
    }

    // ---------------------------------------------------------------- forCategory

    @Test
    void utc083_forCategory_returnsOrderedQuestionnaireWithVersion() {
        ClaimQuestionnaireDto dto = service.forCategory(category);

        assertEquals("COMMON:2/CAT_ELECTRONICS:4", dto.version());
        assertEquals(3L, dto.categoryId());
        assertEquals("Electronics", dto.categoryName());
        assertEquals(4, dto.commonQuestions().size());
        assertEquals(2, dto.categoryQuestions().size());
    }

    @Test
    void utc084_forCategory_missingCategoryTemplateFallsBackToOther() {
        ClaimQuestionTemplate otherTemplate = template(9L, "OTHER", "Khác", null, 1);
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L)).thenReturn(Optional.empty());
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("OTHER")).thenReturn(Optional.of(otherTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(9L)).thenReturn(List.of(
                question(91L, otherTemplate, "Q_OTHER_1", "Đặc điểm riêng", "TEXT", true, 1, null, null, false)));

        ClaimQuestionnaireDto dto = service.forCategory(category);

        assertEquals("COMMON:2/OTHER:1", dto.version());
        assertEquals(1, dto.categoryQuestions().size());
        assertEquals("Q_OTHER_1", dto.categoryQuestions().get(0).code());
    }

    @Test
    void utc085_forCategory_missingCommonTemplateThrowsUnavailable() {
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON")).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.forCategory(category)));
    }

    @Test
    void utc086_forCategory_commonWithThreeQuestionsThrowsUnavailable() {
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions.subList(0, 3));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.forCategory(category)));

        // neither a category template nor the OTHER fallback exists
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.forCategory(category)));
    }

    @Test
    void utc087_forCategory_categoryTemplateWithoutQuestionsThrowsUnavailable() {
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.forCategory(category)));
    }

    // ---------------------------------------------------------------- validateAndSnapshot: request shape

    @Test
    void utc088_validate_validAnswersCreateOrderedSnapshots() {
        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("  Đen nhám ")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Có", "Bao da đen")),
                new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Sạc 20W")),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh chó Corgi"))));

        assertEquals(4, snapshots.size());
        assertEquals("Q_COMMON_1", snapshots.get(0).getQuestionCode());
        assertEquals("Đen nhám", snapshots.get(0).getAnswerText());
        assertEquals("Sạc 20W", snapshots.get(2).getAnswerText());
        assertEquals("Q_CAT_1", snapshots.get(3).getQuestionCode());
    }

    @Test
    void utc089_validate_staleVersionThrowsStale() {
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "COMMON:1/CAT_ELECTRONICS:4", List.of());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_STALE, code(() -> service.validateAndSnapshot(post, req)));
    }

    @Test
    void utc090_validate_nullAnswersThrowsInvalid() {
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", null);
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, code(() -> service.validateAndSnapshot(post, req)));
    }

    @Test
    void utc091_validate_25AnswersThrowsInvalid() {
        List<ClaimAnswerRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tooMany.add(new ClaimAnswerRequest("Q_" + i, TextNode.valueOf("val")));
        }
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", tooMany);
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, code(() -> service.validateAndSnapshot(post, req)));
    }

    @Test
    void utc092_validate_24AnswersAcceptedAtBoundary() {
        List<ClaimQuestion> twentyCategory = new ArrayList<>();
        List<ClaimAnswerRequest> answers = new ArrayList<>(List.of(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Có", "Ốp lưng")),
                new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Trong suốt")),
                new ClaimAnswerRequest("Q_COMMON_4", choice("Sticker", null))));
        for (int i = 1; i <= 20; i++) {
            String qCode = "Q_EXTRA_" + i;
            twentyCategory.add(question(100L + i, categoryTemplate, qCode, "Extra " + i, "TEXT", false, i, null, null, false));
            answers.add(new ClaimAnswerRequest(qCode, TextNode.valueOf("v" + i)));
        }
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(twentyCategory);

        assertEquals(24, service.validateAndSnapshot(post,
                new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", answers)).size());
    }

    @Test
    void utc093_validate_unknownQuestionCodeThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalid(new ClaimAnswerRequest("UNKNOWN_CODE", TextNode.valueOf("x"))));
    }

    @Test
    void utc094_validate_duplicateQuestionCodeThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalid(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Trắng")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
    }

    @Test
    void utc095_validate_answeringDescriptionQuestionThrowsInvalid() {
        List<ClaimQuestion> withExisting = new ArrayList<>(categoryQuestions);
        withExisting.add(question(99L, categoryTemplate, "Q_DESC_EXISTING", "Mô tả", "DESCRIPTION", true, 3, null, null, false));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(withExisting);

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidWithBase(new ClaimAnswerRequest("Q_DESC_EXISTING", TextNode.valueOf("Duplicate desc"))));
    }

    @Test
    void utc096_validate_blankRequiredTextThrowsInvalidWithLabel() {
        AppException ex = assertThrows(AppException.class, () -> service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("   ")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, ex.getErrorCode());
        assertEquals("Vui lòng trả lời: Mô tả màu sắc", ex.getMessage());
    }

    @Test
    void utc097_validate_omittedOptionalQuestionsSkipped() {
        assertEquals(3, service.validateAndSnapshot(post, submitWith(baseAnswers())).size());
    }

    @Test
    void utc098_validate_text2001ThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalid(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("a".repeat(2001))),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
    }

    @Test
    void utc099_validate_numericValueForTextThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalid(
                new ClaimAnswerRequest("Q_COMMON_1", IntNode.valueOf(123)),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
    }

    // ---------------------------------------------------------------- CHOICE

    @Test
    void utc100_validate_choiceWithoutDetailsAccepted() {
        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, submitWith(baseAnswers()));
        assertEquals("Không", snapshots.get(1).getAnswerText());
    }

    @Test
    void utc101_validate_unknownChoiceOptionThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalid(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("INVALID_CHOICE", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh"))));
    }

    @Test
    void utc102_validate_requiredDetailsMissingThrowsInvalid() {
        stubDetailsRequiredChoice();
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalidChoice(choice("Khác", "  ")));
    }

    @Test
    void utc103_validate_choiceWithDetailsFormatsLabelAndDetails() {
        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Có", "Bao da đen")),
                new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Sạc 20W")),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
        assertEquals("Có\nBao da đen", snapshots.get(1).getAnswerText());
    }

    @Test
    void utc104_validate_detailsWhenNotAllowedThrowsInvalid() {
        stubDetailsRequiredChoice();
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalidChoice(choice("Không", "Chi tiết thừa")));
    }

    @Test
    void utc105_validate_unknownPropertyInChoiceThrowsInvalid() {
        stubDetailsRequiredChoice();
        ObjectNode extraField = choice("Không", null);
        extraField.put("extra", "bad");
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalidChoice(extraField));
    }

    @Test
    void utc106_validate_plainStringForChoiceThrowsInvalid() {
        stubDetailsRequiredChoice();
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalidChoice(TextNode.valueOf("Không")));
    }

    // ---------------------------------------------------------------- LOST_DATE

    @Test
    void utc107_validate_lostDateUnknownCreatesSnapshot() {
        assertFalse(lostDateSnapshot(mapper.createObjectNode().put("precision", "UNKNOWN")).isBlank());
    }

    @Test
    void utc108_validate_lostDateUnknownWithDateThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidLostDate(mapper.createObjectNode().put("precision", "UNKNOWN").put("date", "2026-01-15")));
    }

    @Test
    void utc109_validate_lostDateExactFormatsDateAndTime() {
        ObjectNode exact = mapper.createObjectNode().put("precision", "EXACT").put("date", "2026-01-15").put("time", "14:30");
        assertEquals("Ngày 15/01/2026, lúc 14:30 (giờ Việt Nam)", lostDateSnapshot(exact));
    }

    @Test
    void utc110_validate_lostDateApproximateFormatsDate() {
        ObjectNode approx = mapper.createObjectNode().put("precision", "APPROXIMATE").put("date", "2026-01-15");
        assertEquals("Khoảng ngày 15/01/2026 (giờ Việt Nam)", lostDateSnapshot(approx));
    }

    @Test
    void utc111_validate_lostDateUnsupportedPrecisionThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidLostDate(mapper.createObjectNode().put("precision", "APPROX")));
    }

    @Test
    void utc112_validate_lostDateWrongFormatThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidLostDate(mapper.createObjectNode().put("precision", "EXACT").put("date", "15/01/2026")));
    }

    @Test
    void utc113_validate_lostDateNonexistentCalendarDateThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidLostDate(mapper.createObjectNode().put("precision", "EXACT").put("date", "2026-02-30")));
    }

    @Test
    void utc114_validate_lostDateTomorrowThrowsInvalid() {
        String tomorrow = LocalDate.now(VN).plusDays(1).toString();
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidLostDate(mapper.createObjectNode().put("precision", "EXACT").put("date", tomorrow)));
    }

    @Test
    void utc115_validate_lostDateFutureTimeTodayThrowsInvalid() {
        ZonedDateTime nowVn = ZonedDateTime.now(VN);
        LocalTime futureTime = nowVn.toLocalTime().isBefore(LocalTime.of(23, 58))
                ? nowVn.toLocalTime().plusMinutes(1)
                : LocalTime.of(23, 59, 59);
        ObjectNode futureToday = mapper.createObjectNode().put("precision", "EXACT")
                .put("date", nowVn.toLocalDate().toString())
                .put("time", futureTime.toString().substring(0, 5));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalidLostDate(futureToday));
    }

    // ---------------------------------------------------------------- visibility & template integrity

    @Test
    void utc116_validate_answeringHiddenQuestionThrowsInvalid() {
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                invalidWithBase(new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Ẩn"))));
    }

    @Test
    void utc117_validate_hiddenRequiredQuestionSkipped() {
        List<ClaimAnswer> ok = service.validateAndSnapshot(post, submitWith(baseAnswers()));
        assertEquals(3, ok.size());
        assertTrue(ok.stream().noneMatch(a -> a.getQuestionCode().equals("Q_COMMON_3")));

        // once visible ("Có"), the same required question must be answered
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, invalid(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Có", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
    }

    @Test
    void utc118_validate_visibilityCycleThrowsUnavailable() {
        ClaimQuestion qA = question(11L, commonTemplate, "Q_A", "A", "TEXT", false, 1, null, visibility("Q_B", "x"), false);
        ClaimQuestion qB = question(12L, commonTemplate, "Q_B", "B", "TEXT", false, 2, null, visibility("Q_A", "x"), false);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(qA, qB, commonQuestions.get(0), commonQuestions.get(3)));

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.validateAndSnapshot(post, submitWith())));
    }

    @Test
    void utc119_validate_unsupportedInputTypeThrowsUnavailable() {
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of(
                question(30L, categoryTemplate, "Q_FILE", "Ảnh", "FILE", true, 1, null, null, false)));

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, code(() -> service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_FILE", TextNode.valueOf("f.png"))))));
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorCode code(Executable call) {
        return assertThrows(AppException.class, call).getErrorCode();
    }

    /** Minimal valid answer set: required TEXT, CHOICE "Không" (hides Q_COMMON_3), category TEXT. */
    private ClaimAnswerRequest[] baseAnswers() {
        return new ClaimAnswerRequest[]{
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))};
    }

    private ErrorCode invalid(ClaimAnswerRequest... answers) {
        return code(() -> service.validateAndSnapshot(post, submitWith(answers)));
    }

    private ErrorCode invalidWithBase(ClaimAnswerRequest extra) {
        List<ClaimAnswerRequest> all = new ArrayList<>(List.of(baseAnswers()));
        all.add(extra);
        return invalid(all.toArray(ClaimAnswerRequest[]::new));
    }

    private ErrorCode invalidChoice(JsonNode choiceValue) {
        return invalid(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choiceValue),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")));
    }

    private ErrorCode invalidLostDate(ObjectNode lostDate) {
        return invalidWithBase(new ClaimAnswerRequest("Q_CAT_2", lostDate));
    }

    private String lostDateSnapshot(ObjectNode lostDate) {
        List<ClaimAnswerRequest> all = new ArrayList<>(List.of(baseAnswers()));
        all.add(new ClaimAnswerRequest("Q_CAT_2", lostDate));
        return service.validateAndSnapshot(post, submitWith(all.toArray(ClaimAnswerRequest[]::new))).get(3).getAnswerText();
    }

    /** Q_COMMON_2 becomes a CHOICE whose "Khác" option requires details and which allows none otherwise. */
    private void stubDetailsRequiredChoice() {
        ArrayNode withReqDetails = options(option("Khác", "Khác", true), option("Không", "Không", false));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of(
                commonQuestions.get(0),
                question(12L, commonTemplate, "Q_COMMON_2", "Phụ kiện", "CHOICE", true, 2, withReqDetails, null, false),
                commonQuestions.get(2),
                commonQuestions.get(3)));
    }

    private SubmitClaimRequest submitWith(ClaimAnswerRequest... answers) {
        return new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", List.of(answers));
    }

    private void stubActiveQuestionnaire() {
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(categoryQuestions);
    }

    private static ClaimQuestionTemplate template(Long id, String code, String title, Category cat, int version) {
        ClaimQuestionTemplate t = BeanUtils.instantiateClass(ClaimQuestionTemplate.class);
        ReflectionTestUtils.setField(t, "id", id);
        ReflectionTestUtils.setField(t, "code", code);
        ReflectionTestUtils.setField(t, "title", title);
        ReflectionTestUtils.setField(t, "category", cat);
        ReflectionTestUtils.setField(t, "version", version);
        ReflectionTestUtils.setField(t, "active", true);
        return t;
    }

    private static ClaimQuestion question(Long id, ClaimQuestionTemplate t, String code, String label,
                                          String inputType, boolean required, int sortOrder,
                                          JsonNode options, JsonNode visibility, boolean allowDetails) {
        ClaimQuestion q = BeanUtils.instantiateClass(ClaimQuestion.class);
        ReflectionTestUtils.setField(q, "id", id);
        ReflectionTestUtils.setField(q, "template", t);
        ReflectionTestUtils.setField(q, "code", code);
        ReflectionTestUtils.setField(q, "label", label);
        ReflectionTestUtils.setField(q, "inputType", inputType);
        ReflectionTestUtils.setField(q, "required", required);
        ReflectionTestUtils.setField(q, "sortOrder", sortOrder);
        ReflectionTestUtils.setField(q, "options", options);
        ReflectionTestUtils.setField(q, "visibility", visibility);
        ReflectionTestUtils.setField(q, "allowDetails", allowDetails);
        return q;
    }

    private ObjectNode visibility(String dependsOnCode, String expectedValue) {
        ObjectNode cond = mapper.createObjectNode();
        cond.put("questionCode", dependsOnCode);
        cond.set("values", mapper.createArrayNode().add(expectedValue));
        ObjectNode node = mapper.createObjectNode();
        node.set("anyOf", mapper.createArrayNode().add(cond));
        return node;
    }

    private ArrayNode options(ObjectNode... items) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode item : items) {
            array.add(item);
        }
        return array;
    }

    private ObjectNode option(String value, String label, boolean detailsRequired) {
        ObjectNode node = mapper.createObjectNode();
        node.put("value", value);
        node.put("label", label);
        node.put("detailsRequired", detailsRequired);
        return node;
    }

    private ObjectNode choice(String choice, String details) {
        ObjectNode node = mapper.createObjectNode();
        node.put("choice", choice);
        if (details != null) {
            node.put("details", details);
        }
        return node;
    }
}

