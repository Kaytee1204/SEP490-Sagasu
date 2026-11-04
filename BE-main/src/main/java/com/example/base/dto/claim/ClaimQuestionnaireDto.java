package com.example.base.dto.claim;

import java.util.List;

public record ClaimQuestionnaireDto(String version, Long categoryId, String categoryName,
        List<QuestionDto> commonQuestions, List<QuestionDto> categoryQuestions) {
    public record QuestionDto(String code, String label, String inputType, boolean required,
            List<OptionDto> options, VisibilityDto visibility, boolean allowDetails, String choicePrompt) {}
    public record OptionDto(String value, String label, boolean detailsRequired) {}
    public record VisibilityDto(List<ConditionDto> anyOf) {}
    public record ConditionDto(String questionCode, List<String> values) {}
}
