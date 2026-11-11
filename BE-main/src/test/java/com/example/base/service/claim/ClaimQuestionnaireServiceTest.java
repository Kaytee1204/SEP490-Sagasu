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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimQuestionnaireServiceTest {

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
        owner.setEmail("owner@fpt.edu.vn");
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
    }

    // UTC081 - forCategory returns ordered questionnaire DTO with computed version string
    @Test
    void forCategory_whenTemplatesValid_returnsOrderedQuestionnaireWithComputedVersion() {
        stubActiveQuestionnaire();

        ClaimQuestionnaireDto dto = service.forCategory(category);

        assertEquals("COMMON:2/CAT_ELECTRONICS:4", dto.version());
        assertEquals(3L, dto.categoryId());
        assertEquals("Electronics", dto.categoryName());
        assertEquals(4, dto.commonQuestions().size());
        assertEquals(2, dto.categoryQuestions().size());
    }

    // UTC084 - forCategory throws CLAIM_QUESTIONNAIRE_UNAVAILABLE when category template missing or COMMON has fewer than 4 questions
    @Test
    void forCategory_whenCategoryTemplateMissingOrCommonCountInvalid_throwsUnavailable() {
        // Supplementary (ClaimQuestionnaireService.forCategory): missing category and OTHER fallback throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.empty());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());

        // UTC084 - COMMON template having only 3 questions throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(commonQuestions.subList(0, 3));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L))
                .thenReturn(categoryQuestions);

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());
    }

    // UTC087 - validateAndSnapshot throws CLAIM_QUESTIONNAIRE_STALE on outdated questionnaireVersion
    @Test
    void validateAndSnapshot_whenVersionStale_throwsClaimQuestionnaireStale() {
        stubActiveQuestionnaire();
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "COMMON:1/CAT_ELECTRONICS:4", List.of());

        AppException ex = assertThrows(AppException.class, () -> service.validateAndSnapshot(post, req));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_STALE, ex.getErrorCode());
    }

    // UTC086, UTC101, UTC107 - validateAndSnapshot produces ordered snapshots for TEXT, CHOICE with details, and EXACT LOST_DATE
    @Test
    void validateAndSnapshot_whenValidVisibleAndConditionalAnswers_createsOrderedSnapshots() {
        stubActiveQuestionnaire();
        // UTC101 - CHOICE option with allowed/required details formats "<label>\n<details>"
        ObjectNode selectWithDetails = choice("Có", "Bao da đen");
        // UTC107 - LOST_DATE with EXACT precision formats VN date and time
        ObjectNode lostDate = mapper.createObjectNode();
        lostDate.put("precision", "EXACT");
        lostDate.put("date", "2026-01-15");
        lostDate.put("time", "14:30");

        // UTC086 - matching version and valid answers yield ordered ClaimAnswer snapshots
        SubmitClaimRequest req = new SubmitClaimRequest(
                "desc", "note", "COMMON:2/CAT_ELECTRONICS:4",
                List.of(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("  Đen nhám ")),
                        new ClaimAnswerRequest("Q_COMMON_2", selectWithDetails),
                        new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Sạc 20W")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh chó Corgi")),
                        new ClaimAnswerRequest("Q_CAT_2", lostDate)
                ));

        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, req);

        assertEquals(5, snapshots.size());
        assertEquals("Q_COMMON_1", snapshots.get(0).getQuestionCode());
        assertEquals("Đen nhám", snapshots.get(0).getAnswerText());
        assertEquals("Có\nBao da đen", snapshots.get(1).getAnswerText());
        assertEquals("Sạc 20W", snapshots.get(2).getAnswerText());
        assertEquals("Ngày 15/01/2026, lúc 14:30 (giờ Việt Nam)", snapshots.get(4).getAnswerText());
    }

    // UTC098, UTC115 - validateAndSnapshot accepts CHOICE without details and skips hidden conditional required questions
    @Test
    void validateAndSnapshot_whenConditionalHiddenQuestionAnsweredOrRequiredMissing_handlesVisibility() {
        stubActiveQuestionnaire();
        // UTC098, UTC115 - CHOICE "Không" hides required Q_COMMON_3 so omitting it succeeds
        SubmitClaimRequest hiddenSkipped = new SubmitClaimRequest(
                "desc", null, "COMMON:2/CAT_ELECTRONICS:4",
                List.of(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh nền mặc định"))
                ));

        List<ClaimAnswer> ok = service.validateAndSnapshot(post, hiddenSkipped);
        assertEquals(3, ok.size());

        // Supplementary (ClaimQuestionnaireService.validateAndSnapshot): visible conditional required question missing throws CLAIM_QUESTIONNAIRE_INVALID
        SubmitClaimRequest visibleMissing = new SubmitClaimRequest(
                "desc", null, "COMMON:2/CAT_ELECTRONICS:4",
                List.of(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Có", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh nền mặc định"))
                ));

        AppException ex = assertThrows(AppException.class, () -> service.validateAndSnapshot(post, visibleMissing));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, ex.getErrorCode());
    }

    // UTC099 - validateAndSnapshot rejects invalid CHOICE option
    @Test
    void validateAndSnapshot_whenInvalidOptionOrUnknownCodeOrDuplicate_throwsInvalid() {
        stubActiveQuestionnaire();
        SubmitClaimRequest badOption = new SubmitClaimRequest(
                "desc", null, "COMMON:2/CAT_ELECTRONICS:4",
                List.of(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("INVALID_CHOICE", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh"))
                ));

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID,
                assertThrows(AppException.class, () -> service.validateAndSnapshot(post, badOption)).getErrorCode());
    }

    // UTC082 - forCategory falls back to OTHER template when category-specific template is missing
    @Test
    void forCategory_whenCategoryTemplateMissing_fallsBackToOtherTemplate() {
        ClaimQuestionTemplate otherTemplate = template(9L, "OTHER", "Khác", null, 1);
        List<ClaimQuestion> otherQuestions = List.of(
                question(91L, otherTemplate, "Q_OTHER_1", "Đặc điểm riêng", "TEXT", true, 1, null, null, false));
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.empty());
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("OTHER"))
                .thenReturn(Optional.of(otherTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(9L)).thenReturn(otherQuestions);

        ClaimQuestionnaireDto dto = service.forCategory(category);

        assertEquals("COMMON:2/OTHER:1", dto.version());
        assertEquals(1, dto.categoryQuestions().size());
        assertEquals("Q_OTHER_1", dto.categoryQuestions().get(0).code());
    }

    // UTC083, UTC085 - forCategory throws CLAIM_QUESTIONNAIRE_UNAVAILABLE when COMMON missing or specific questions empty
    @Test
    void forCategory_whenCommonMissingOrCategoryQuestionsEmpty_throwsUnavailable() {
        // UTC083 - no active COMMON template throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.empty());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());

        // UTC085 - category template with 0 questions throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());
    }

    // UTC088, UTC089 - validateAndSnapshot rejects null answers list or more than 24 answers
    @Test
    void validateAndSnapshot_whenAnswersNullOrExceedMax24_throwsInvalid() {
        stubActiveQuestionnaire();
        // UTC088 - null answers throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post,
                        new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", null)))
                .getErrorCode());

        // UTC089 - 25 answers exceeds max 24 and throws CLAIM_QUESTIONNAIRE_INVALID
        List<ClaimAnswerRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tooMany.add(new ClaimAnswerRequest("Q_" + i, TextNode.valueOf("val")));
        }
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post,
                        new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", tooMany)))
                .getErrorCode());
    }

    // UTC090 - validateAndSnapshot accepts boundary size of 24 valid answers
    @Test
    void validateAndSnapshot_when24AnswersSubmitted_acceptsBoundary() {
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);

        List<ClaimQuestion> twentyCategory = new ArrayList<>();
        List<ClaimAnswerRequest> answers = new ArrayList<>(List.of(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Có", "Ốp lưng")),
                new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Trong suốt")),
                new ClaimAnswerRequest("Q_COMMON_4", choice("Sticker", null))));
        for (int i = 1; i <= 20; i++) {
            String code = "Q_EXTRA_" + i;
            twentyCategory.add(question(100L + i, categoryTemplate, code, "Extra " + i, "TEXT", false, i, null, null,
                    false));
            answers.add(new ClaimAnswerRequest(code, TextNode.valueOf("v" + i)));
        }
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(twentyCategory);

        List<ClaimAnswer> snapshots = service.validateAndSnapshot(
                post, new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", answers));

        assertEquals(24, snapshots.size());
    }

    // UTC091, UTC092, UTC093 - validateAndSnapshot rejects unknown codes, duplicates, and DESCRIPTION inputType answers
    @Test
    void validateAndSnapshot_whenUnknownDuplicateOrExistingFieldAnswered_throwsInvalid() {
        List<ClaimQuestion> withExisting = new ArrayList<>(categoryQuestions);
        withExisting.add(question(99L, categoryTemplate, "Q_DESC_EXISTING", "Mô tả", "DESCRIPTION", true, 3, null,
                null, false));
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(withExisting);

        // UTC091 - unknown questionCode throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("UNKNOWN_CODE", TextNode.valueOf("x")))))
                .getErrorCode());

        // UTC092 - duplicate questionCode throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Trắng")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        // UTC093 - answering DESCRIPTION question throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                        new ClaimAnswerRequest("Q_DESC_EXISTING", TextNode.valueOf("Duplicate desc")))))
                .getErrorCode());
    }

    // UTC094, UTC095 - validateAndSnapshot requires non-blank required TEXT and skips omitted optional questions
    @Test
    void validateAndSnapshot_whenRequiredMissingOrOptionalOmitted_handlesValidation() {
        stubActiveQuestionnaire();

        // UTC094 - blank required TEXT throws CLAIM_QUESTIONNAIRE_INVALID with question label
        AppException missingRequired = assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("   ")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, missingRequired.getErrorCode());
        assertEquals("Vui lòng trả lời: Mô tả màu sắc", missingRequired.getMessage());

        // UTC095 - omitted optional questions are skipped without error
        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
        assertEquals(3, snapshots.size());
    }

    // UTC096, UTC097 - validateAndSnapshot rejects TEXT answers exceeding 2000 chars or non-textual values
    @Test
    void validateAndSnapshot_whenTextTooLongOrNonTextual_throwsInvalid() {
        stubActiveQuestionnaire();

        // UTC096 - 2001-char TEXT exceeds max 2000 and throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("a".repeat(2001))),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        // UTC097 - numeric IntNode for TEXT question throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", IntNode.valueOf(123)),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());
    }

    // UTC100, UTC102, UTC103, UTC104 - validateAndSnapshot validates CHOICE details, unexpected fields, and object format
    @Test
    void validateAndSnapshot_whenChoiceDetailsInvalid_throwsInvalid() {
        ArrayNode withReqDetails = options(option("Khác", "Khác", true), option("Không", "Không", false));
        List<ClaimQuestion> customCommon = List.of(
                commonQuestions.get(0),
                question(12L, commonTemplate, "Q_COMMON_2", "Phụ kiện", "CHOICE", true, 2, withReqDetails, null, false),
                commonQuestions.get(2),
                commonQuestions.get(3));
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(customCommon);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(categoryQuestions);

        // UTC100 - detailsRequired option missing details throws CLAIM_QUESTIONNAIRE_INVALID
        AppException missingDetails = assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Khác", "  ")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, missingDetails.getErrorCode());

        // UTC102 - details passed when not allowed throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", "Chi tiết thừa")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        // UTC103 - unknown property inside CHOICE object throws CLAIM_QUESTIONNAIRE_INVALID
        ObjectNode extraFieldChoice = choice("Không", null);
        extraFieldChoice.put("extra", "bad");
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", extraFieldChoice),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        // UTC104 - string value instead of CHOICE object throws CLAIM_QUESTIONNAIRE_INVALID
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", TextNode.valueOf("Không")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());
    }

    // UTC105, UTC108 - validateAndSnapshot formats LOST_DATE for UNKNOWN and APPROXIMATE precisions
    @Test
    void validateAndSnapshot_whenLostDateUnknownOrApproximate_createsExpectedSnapshots() {
        stubActiveQuestionnaire();

        // UTC105 - UNKNOWN precision snapshot text
        ObjectNode unknownDate = mapper.createObjectNode().put("precision", "UNKNOWN");
        List<ClaimAnswer> unknownRes = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                new ClaimAnswerRequest("Q_CAT_2", unknownDate)));
        assertFalse(unknownRes.get(3).getAnswerText().isBlank());

        // UTC108 - APPROXIMATE precision snapshot text
        ObjectNode approxDate = mapper.createObjectNode().put("precision", "APPROXIMATE").put("date", "2026-01-15");
        List<ClaimAnswer> approxRes = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                new ClaimAnswerRequest("Q_CAT_2", approxDate)));
        assertEquals("Khoảng ngày 15/01/2026 (giờ Việt Nam)", approxRes.get(3).getAnswerText());
    }

    // UTC106, UTC109, UTC110, UTC111, UTC112, UTC113 - validateAndSnapshot rejects invalid or future LOST_DATE values
    @Test
    void validateAndSnapshot_whenLostDateInvalidOrInFuture_throwsInvalid() {
        stubActiveQuestionnaire();
        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        String tomorrow = LocalDate.now(vn).plusDays(1).toString();
        ZonedDateTime nowVn = ZonedDateTime.now(vn);
        String today = nowVn.toLocalDate().toString();
        LocalTime futureTime = nowVn.toLocalTime().isBefore(LocalTime.of(23, 58))
                ? nowVn.toLocalTime().plusMinutes(1)
                : LocalTime.of(23, 59, 59);

        List<ObjectNode> badDates = List.of(
                // UTC106 - UNKNOWN precision with date field rejected
                mapper.createObjectNode().put("precision", "UNKNOWN").put("date", "2026-01-15"),
                // UTC109 - unsupported precision value rejected
                mapper.createObjectNode().put("precision", "APPROX"),
                // UTC110 - wrong date format dd/MM/yyyy rejected
                mapper.createObjectNode().put("precision", "EXACT").put("date", "15/01/2026"),
                // UTC111 - non-existent calendar date 2026-02-30 rejected
                mapper.createObjectNode().put("precision", "EXACT").put("date", "2026-02-30"),
                // UTC112 - future date tomorrow (VN) rejected
                mapper.createObjectNode().put("precision", "EXACT").put("date", tomorrow),
                // UTC113 - future time today (VN) rejected
                mapper.createObjectNode().put("precision", "EXACT").put("date", today)
                        .put("time", futureTime.toString().substring(0, 5)));

        for (ObjectNode badDate : badDates) {
            assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                    () -> service.validateAndSnapshot(post, submitWith(
                            new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                            new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                            new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                            new ClaimAnswerRequest("Q_CAT_2", badDate))))
                    .getErrorCode());
        }
    }

    // UTC114 - validateAndSnapshot rejects answers submitted for hidden conditional questions
    @Test
    void validateAndSnapshot_whenHiddenQuestionAnswered_throwsInvalid() {
        stubActiveQuestionnaire();

        AppException ex = assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_COMMON_3", TextNode.valueOf("Ẩn")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, ex.getErrorCode());
    }

    // UTC116, UTC117 - validateAndSnapshot throws CLAIM_QUESTIONNAIRE_UNAVAILABLE on visibility cycles or unsupported inputType
    @Test
    void validateAndSnapshot_whenVisibilityCycleOrUnsupportedInputType_throwsUnavailable() {
        // UTC116 - visibility cycle A <-> B throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        ClaimQuestion qA = question(11L, commonTemplate, "Q_A", "A", "TEXT", false, 1, null, visibility("Q_B", "x"),
                false);
        ClaimQuestion qB = question(12L, commonTemplate, "Q_B", "B", "TEXT", false, 2, null, visibility("Q_A", "x"),
                false);
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(qA, qB, commonQuestions.get(0), commonQuestions.get(3)));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(categoryQuestions);

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith())).getErrorCode());

        // UTC117 - unsupported inputType "FILE" throws CLAIM_QUESTIONNAIRE_UNAVAILABLE
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of(
                question(30L, categoryTemplate, "Q_FILE", "Ảnh", "FILE", true, 1, null, null, false)));

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_FILE", TextNode.valueOf("f.png")))))
                .getErrorCode());
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

