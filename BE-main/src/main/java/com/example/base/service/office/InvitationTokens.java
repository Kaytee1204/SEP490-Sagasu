package com.example.base.service.office;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class InvitationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private InvitationTokens() {}
    static String create() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw OfficeAccess.bad("Liên kết không hợp lệ hoặc đã được thay thế. Hãy mở thư lịch hẹn mới nhất.");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
