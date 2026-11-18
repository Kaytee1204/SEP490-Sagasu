package com.example.base.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void utc001_appException_usesErrorCodeStatusAndMessage() {
        ResponseEntity<ErrorResponse> res = handler.handleAppException(new AppException(ErrorCode.REPORT_NOT_FOUND));
        assertThat(res.getStatusCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND.getHttpStatus());
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND.getCode());
        assertThat(res.getBody().isSuccess()).isFalse();
        assertThat(res.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void utc002_appException_customMessage() {
        ResponseEntity<ErrorResponse> res = handler.handleAppException(new AppException(ErrorCode.BAD_REQUEST, "Custom"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("Custom");
    }

    @Test
    void utc003_optimisticLock_409DataConflict() {
        ResponseEntity<ErrorResponse> res = handler.handleConcurrentChange(new OptimisticLockingFailureException("x"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getCode()).isEqualTo("DATA_CONFLICT");
    }

    @Test
    void utc004_dataIntegrity_409DataConflict() {
        ResponseEntity<ErrorResponse> res = handler.handleDataConflict(new DataIntegrityViolationException("dup"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().getCode()).isEqualTo("DATA_CONFLICT");
    }

    @Test
    void utc005_accessDenied_403Forbidden() {
        ResponseEntity<ErrorResponse> res = handler.handleAccessDenied(new AccessDeniedException("no"));
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void utc006_uploadTooLarge_413() {
        ResponseEntity<ErrorResponse> res = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10));
        assertThat(res.getStatusCode().value()).isEqualTo(413);
        assertThat(res.getBody().getCode()).isEqualTo("UPLOAD_413");
    }

    @Test
    void utc007_invalidBody_400() {
        ResponseEntity<ErrorResponse> res = handler.handleInvalidBody(new RuntimeException("bad json"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getCode()).isEqualTo(ErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    void utc008_validation_joinsFieldMessagesAndMapsFields() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "Email không hợp lệ"));
        binding.addError(new FieldError("req", "name", "Tên bắt buộc"));
        MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);

        ResponseEntity<ErrorResponse> res = handler.handleValidationException(new MethodArgumentNotValidException(param, binding));

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("Email không hợp lệ; Tên bắt buộc");
        assertThat(res.getBody().getErrors()).containsEntry("email", "Email không hợp lệ").containsEntry("name", "Tên bắt buộc");
    }

    @Test
    void utc009_validation_noFieldErrors_defaultMessage() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "req");
        MethodParameter param = new MethodParameter(Object.class.getMethod("toString"), -1);

        ResponseEntity<ErrorResponse> res = handler.handleValidationException(new MethodArgumentNotValidException(param, binding));

        assertThat(res.getBody().getMessage()).isEqualTo(ErrorCode.BAD_REQUEST.getDefaultMessage());
    }

    @Test
    void utc010_genericException_500WithMessageOrDefault() {
        assertThat(handler.handleGenericException(new IllegalStateException("boom")).getBody().getMessage()).isEqualTo("boom");
        ResponseEntity<ErrorResponse> res = handler.handleGenericException(new NullPointerException());
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage());
    }
}
