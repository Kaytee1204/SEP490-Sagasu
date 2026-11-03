package com.example.base.service.claim;

import com.example.base.dto.claim.*;
import com.example.base.dto.claim.ClaimQuestionnaireDto.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.ClaimQuestionRepository;
import com.example.base.repository.ClaimQuestionTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClaimQuestionnaireService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> EXISTING_FIELDS = Set.of("DESCRIPTION", "EVIDENCE");
    private final ClaimQuestionTemplateRepository templates;
    private final ClaimQuestionRepository questions;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public ClaimQuestionnaireDto forCategory(Category category) {
        Catalog catalog = load(category);
        return new ClaimQuestionnaireDto(catalog.version(), category.getCategoryId(), category.getName(),
                catalog.common().stream().map(this::dto).toList(), catalog.specific().stream().map(this::dto).toList());
    }

    /** Resolve every label, choice and visibility rule on the server, never from client-supplied labels. */
    @Transactional(readOnly = true)
    public List<ClaimAnswer> validateAndSnapshot(Post post, SubmitClaimRequest request) {
        Catalog catalog = load(post.getCategory());
        if (!catalog.version().equals(request.questionnaireVersion())) {
            throw new AppException(ErrorCode.CLAIM_QUESTIONNAIRE_STALE);
        }
        if (request.answers() == null || request.answers().size() > 24) throw invalid("Danh sách câu trả lời không hợp lệ");
        Map<String, ClaimQuestion> definitions = new LinkedHashMap<>();
        catalog.common().forEach(question -> definitions.put(question.getCode(), question));
        catalog.specific().forEach(question -> definitions.put(question.getCode(), question));
        Map<String, JsonNode> answers = new HashMap<>();
        for (ClaimAnswerRequest answer : request.answers()) {
            if (answer == null || answer.questionCode() == null || answer.value() == null
                    || answer.value().toString().length() > 6000) throw invalid("Câu trả lời không hợp lệ");
            ClaimQuestion question = definitions.get(answer.questionCode());
            if (question == null || EXISTING_FIELDS.contains(question.getInputType())
                    || answers.putIfAbsent(answer.questionCode(), answer.value()) != null) {
                throw invalid("Có câu hỏi không thuộc bộ hiện tại hoặc bị gửi trùng");
            }
        }
        List<ClaimAnswer> snapshots = new ArrayList<>();
        for (ClaimQuestion question : definitions.values()) {
            if (EXISTING_FIELDS.contains(question.getInputType())) continue;
            JsonNode value = answers.get(question.getCode());
            if (!visible(question, answers, definitions, new HashSet<>())) {
                if (value != null && !empty(value)) throw invalid("Không gửi câu trả lời của nhánh đang ẩn: " + question.getLabel());
                continue;
            }
            if (empty(value)) {
                if (question.isRequired()) throw invalid("Vui lòng trả lời: " + question.getLabel());
                continue;
            }
            String text = normalize(question, value);
            int order = question.getSortOrder() + ("COMMON".equals(question.getTemplate().getCode()) ? 0 : 100);
            snapshots.add(new ClaimAnswer(question, text, order));
        }
        return snapshots;
    }

    private Catalog load(Category category) {
        ClaimQuestionTemplate common = templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("COMMON")
                .orElseThrow(this::unavailable);
        ClaimQuestionTemplate specific = templates.findFirstByCategory_CategoryIdAndActiveTrueOrderByVersionDesc(category.getCategoryId())
                .orElseGet(() -> templates.findFirstByCodeAndActiveTrueOrderByVersionDesc("OTHER").orElseThrow(this::unavailable));
        List<ClaimQuestion> commonQuestions = questions.findByTemplate_IdOrderBySortOrderAscIdAsc(common.getId());
        List<ClaimQuestion> categoryQuestions = questions.findByTemplate_IdOrderBySortOrderAscIdAsc(specific.getId());
        if (commonQuestions.size() != 4 || categoryQuestions.isEmpty()) throw unavailable();
        return new Catalog(common.getCode() + ":" + common.getVersion() + "/" + specific.getCode() + ":" + specific.getVersion(),
                commonQuestions, categoryQuestions);
    }

    private QuestionDto dto(ClaimQuestion question) {
        return new QuestionDto(question.getCode(), question.getLabel(), question.getInputType(), question.isRequired(),
                options(question), visibility(question), question.isAllowDetails(), question.getChoicePrompt());
    }

    private List<OptionDto> options(ClaimQuestion question) {
        return mapper.convertValue(question.getOptions(), new TypeReference<List<OptionDto>>() {});
    }

    private VisibilityDto visibility(ClaimQuestion question) {
        return question.getVisibility() == null || question.getVisibility().isNull() ? null
                : mapper.convertValue(question.getVisibility(), VisibilityDto.class);
    }

    private boolean visible(ClaimQuestion question, Map<String, JsonNode> answers,
                            Map<String, ClaimQuestion> definitions, Set<String> visiting) {
        VisibilityDto rule = visibility(question);
        if (rule == null) return true;
        if (!visiting.add(question.getCode())) throw unavailable();
        try {
            return rule.anyOf().stream().anyMatch(condition -> {
                ClaimQuestion parent = definitions.get(condition.questionCode());
                return parent != null && visible(parent, answers, definitions, visiting)
                        && condition.values().contains(choiceValue(answers.get(parent.getCode())));
            });
        } finally {
            visiting.remove(question.getCode());
        }
    }

    private String normalize(ClaimQuestion question, JsonNode value) {
        return switch (question.getInputType()) {
            case "TEXT" -> text(value, question, false);
            case "CHOICE", "CONTENTS" -> normalizeChoice(question, value);
            case "LOST_DATE" -> normalizeLostDate(question, value);
            default -> throw unavailable();
        };
    }

    private String normalizeChoice(ClaimQuestion question, JsonNode value) {
        requireObject(value, Set.of("choice", "details"), question);
        String selected = text(value.get("choice"), question, false);
        OptionDto option = options(question).stream().filter(item -> item.value().equals(selected)).findFirst()
                .orElseThrow(() -> invalid("Lựa chọn không hợp lệ: " + question.getLabel()));
        String details = text(value.get("details"), question, true);
        if (option.detailsRequired() && details.isEmpty()) throw invalid("Vui lòng mô tả thêm: " + question.getLabel());
        if (!details.isEmpty() && !option.detailsRequired() && !question.isAllowDetails()) {
            throw invalid("Mô tả không áp dụng cho lựa chọn này: " + question.getLabel());
        }
        return option.label() + (details.isEmpty() ? "" : "\n" + details);
    }

    private String normalizeLostDate(ClaimQuestion question, JsonNode value) {
        requireObject(value, Set.of("precision", "date", "time"), question);
        String precision = text(value.get("precision"), question, false);
        String dateText = text(value.get("date"), question, true);
        String timeText = text(value.get("time"), question, true);
        if ("UNKNOWN".equals(precision)) {
            if (!dateText.isEmpty() || !timeText.isEmpty()) throw invalid("Không gửi ngày/giờ khi chọn không nhớ rõ");
            return "Không nhớ rõ thời điểm mất";
        }
        if (!Set.of("EXACT", "APPROXIMATE").contains(precision)) throw invalid("Mức độ chính xác của ngày mất không hợp lệ");
        try {
            if (!dateText.matches("\\d{4}-\\d{2}-\\d{2}")) throw invalid("Ngày mất phải có dạng yyyy-MM-dd");
            LocalDate date = LocalDate.parse(dateText);
            LocalTime time = timeText.isEmpty() ? null : LocalTime.parse(timeText);
            ZonedDateTime now = ZonedDateTime.now(VIETNAM);
            if (date.isAfter(now.toLocalDate()) || (date.equals(now.toLocalDate()) && time != null && time.isAfter(now.toLocalTime()))) {
                throw invalid("Thời điểm mất đồ không được ở tương lai (giờ Việt Nam)");
            }
            return ("APPROXIMATE".equals(precision) ? "Khoảng ngày " : "Ngày ")
                    + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + (time == null ? "" : ", lúc " + time.format(DateTimeFormatter.ofPattern("HH:mm"))) + " (giờ Việt Nam)";
        } catch (DateTimeParseException failure) {
            throw invalid("Ngày hoặc giờ mất đồ không hợp lệ");
        }
    }

    private String text(JsonNode value, ClaimQuestion question, boolean optional) {
        if (value == null || value.isNull()) {
            if (optional) return "";
            throw invalid("Vui lòng trả lời: " + question.getLabel());
        }
        if (!value.isTextual() || value.textValue().length() > 2000) throw invalid("Câu trả lời phải là văn bản tối đa 2.000 ký tự: " + question.getLabel());
        String text = value.textValue().strip();
        if (!optional && text.isEmpty()) throw invalid("Vui lòng trả lời: " + question.getLabel());
        return text;
    }

    private void requireObject(JsonNode value, Set<String> allowed, ClaimQuestion question) {
        if (!value.isObject()) throw invalid("Định dạng câu trả lời không hợp lệ: " + question.getLabel());
        value.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw invalid("Thông tin không thuộc câu hỏi: " + question.getLabel());
        });
    }

    private String choiceValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return value.isTextual() ? value.textValue() : value.path("choice").asText(null);
    }

    private boolean empty(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank());
    }

    private AppException invalid(String message) { return new AppException(ErrorCode.CLAIM_QUESTIONNAIRE_INVALID, message); }
    private AppException unavailable() { return new AppException(ErrorCode.CLAIM_QUESTIONNAIRE_UNAVAILABLE); }
    private record Catalog(String version, List<ClaimQuestion> common, List<ClaimQuestion> specific) {}
}
