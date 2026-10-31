package com.example.base.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public class GoogleLoginRequest {

    @Schema(description = "Email tài khoản Google đã được hệ thống cung cấp", example = "khanhtrinh1204@gmail.com")
    private String email;

    @Schema(description = "Google OAuth2 Access Token (tùy chọn nếu gọi qua Google Identity Services)")
    private String accessToken;

    @Schema(description = "Google ID Token (tùy chọn)")
    private String idToken;

    public GoogleLoginRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
