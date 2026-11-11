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

    @Test
    void forCategory_whenCategoryTemplateMissingOrCommonCountInvalid_throwsUnavailable() {
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.empty());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());

        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(commonQuestions.subList(0, 3));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L))
                .thenReturn(categoryQuestions);

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());
    }

    @Test
    void validateAndSnapshot_whenVersionStale_throwsClaimQuestionnaireStale() {
        stubActiveQuestionnaire();
        SubmitClaimRequest req = new SubmitClaimRequest("desc", null, "COMMON:1/CAT_ELECTRONICS:4", List.of());

        AppException ex = assertThrows(AppException.class, () -> service.validateAndSnapshot(post, req));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_STALE, ex.getErrorCode());
    }

    @Test
    void validateAndSnapshot_whenValidVisibleAndConditionalAnswers_createsOrderedSnapshots() {
        stubActiveQuestionnaire();
        ObjectNode selectWithDetails = choice("Có", "Bao da đen");
        ObjectNode lostDate = mapper.createObjectNode();
        lostDate.put("precision", "EXACT");
        lostDate.put("date", "2026-01-15");
        lostDate.put("time", "14:30");

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

    @Test
    void validateAndSnapshot_whenConditionalHiddenQuestionAnsweredOrRequiredMissing_handlesVisibility() {
        stubActiveQuestionnaire();
        SubmitClaimRequest hiddenSkipped = new SubmitClaimRequest(
                "desc", null, "COMMON:2/CAT_ELECTRONICS:4",
                List.of(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Ảnh nền mặc định"))
                ));

        List<ClaimAnswer> ok = service.validateAndSnapshot(post, hiddenSkipped);
        assertEquals(3, ok.size());

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

    @Test
    void forCategory_whenCommonMissingOrCategoryQuestionsEmpty_throwsUnavailable() {
        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.empty());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());

        when(templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON"))
                .thenReturn(Optional.of(commonTemplate));
        when(templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(3L))
                .thenReturn(Optional.of(categoryTemplate));
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(commonQuestions);
        when(questions.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.forCategory(category)).getErrorCode());
    }

    @Test
    void validateAndSnapshot_whenAnswersNullOrExceedMax24_throwsInvalid() {
        stubActiveQuestionnaire();
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post,
                        new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", null)))
                .getErrorCode());

        List<ClaimAnswerRequest> tooMany = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tooMany.add(new ClaimAnswerRequest("Q_" + i, TextNode.valueOf("val")));
        }
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post,
                        new SubmitClaimRequest("desc", null, "COMMON:2/CAT_ELECTRONICS:4", tooMany)))
                .getErrorCode());
    }

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

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("UNKNOWN_CODE", TextNode.valueOf("x")))))
                .getErrorCode());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Trắng")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                        new ClaimAnswerRequest("Q_DESC_EXISTING", TextNode.valueOf("Duplicate desc")))))
                .getErrorCode());
    }

    @Test
    void validateAndSnapshot_whenRequiredMissingOrOptionalOmitted_handlesValidation() {
        stubActiveQuestionnaire();

        AppException missingRequired = assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("   ")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, missingRequired.getErrorCode());
        assertEquals("Vui lòng trả lời: Mô tả màu sắc", missingRequired.getMessage());

        List<ClaimAnswer> snapshots = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo"))));
        assertEquals(3, snapshots.size());
    }

    @Test
    void validateAndSnapshot_whenTextTooLongOrNonTextual_throwsInvalid() {
        stubActiveQuestionnaire();

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("a".repeat(2001))),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", IntNode.valueOf(123)),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());
    }

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

        AppException missingDetails = assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Khác", "  ")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))));
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, missingDetails.getErrorCode());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", choice("Không", "Chi tiết thừa")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        ObjectNode extraFieldChoice = choice("Không", null);
        extraFieldChoice.put("extra", "bad");
        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", extraFieldChoice),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());

        assertEquals(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, assertThrows(AppException.class,
                () -> service.validateAndSnapshot(post, submitWith(
                        new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                        new ClaimAnswerRequest("Q_COMMON_2", TextNode.valueOf("Không")),
                        new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")))))
                .getErrorCode());
    }

    @Test
    void validateAndSnapshot_whenLostDateUnknownOrApproximate_createsExpectedSnapshots() {
        stubActiveQuestionnaire();

        ObjectNode unknownDate = mapper.createObjectNode().put("precision", "UNKNOWN");
        List<ClaimAnswer> unknownRes = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                new ClaimAnswerRequest("Q_CAT_2", unknownDate)));
        assertFalse(unknownRes.get(3).getAnswerText().isBlank());

        ObjectNode approxDate = mapper.createObjectNode().put("precision", "APPROXIMATE").put("date", "2026-01-15");
        List<ClaimAnswer> approxRes = service.validateAndSnapshot(post, submitWith(
                new ClaimAnswerRequest("Q_COMMON_1", TextNode.valueOf("Đen")),
                new ClaimAnswerRequest("Q_COMMON_2", choice("Không", null)),
                new ClaimAnswerRequest("Q_CAT_1", TextNode.valueOf("Mèo")),
                new ClaimAnswerRequest("Q_CAT_2", approxDate)));
        assertEquals("Khoảng ngày 15/01/2026 (giờ Việt Nam)", approxRes.get(3).getAnswerText());
    }

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
                mapper.createObjectNode().put("precision", "UNKNOWN").put("date", "2026-01-15"),
                mapper.createObjectNode().put("precision", "APPROX"),
                mapper.createObjectNode().put("precision", "EXACT").put("date", "15/01/2026"),
                mapper.createObjectNode().put("precision", "EXACT").put("date", "2026-02-30"),
                mapper.createObjectNode().put("precision", "EXACT").put("date", tomorrow),
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

    @Test
    void validateAndSnapshot_whenVisibilityCycleOrUnsupportedInputType_throwsUnavailable() {
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

