package com.example.base.service.office;

import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InvitationTokensTest {

    @Test
    void utc046_create_returns43CharUrlSafeToken() {
        String token = InvitationTokens.create();
        assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    void utc047_create_twice_returnsDifferentTokens() {
        String t1 = InvitationTokens.create();
        String t2 = InvitationTokens.create();
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void utc048_hash_valid43CharToken_returns64CharLowerHex() {
        String token = InvitationTokens.create();
        String hex = InvitationTokens.hash(token);
        assertThat(hex).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    void utc049_hash_null_throwsBadRequest() {
        AppException ex = catchThrowableOfType(() -> InvitationTokens.hash(null), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("Liên kết không hợp lệ hoặc đã được thay thế.");
    }

    @Test
    void utc050_hash_42CharToken_throwsBadRequest() {
        AppException ex = catchThrowableOfType(() -> InvitationTokens.hash("a".repeat(42)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc051_hash_44CharToken_throwsBadRequest() {
        AppException ex = catchThrowableOfType(() -> InvitationTokens.hash("a".repeat(44)), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void utc052_hash_tokenContainingPlus_throwsBadRequest() {
        String invalid = "a".repeat(42) + "+";
        AppException ex = catchThrowableOfType(() -> InvitationTokens.hash(invalid), AppException.class);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
    }
}

