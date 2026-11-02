package com.example.base.security;

import com.example.base.entity.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);
        String emailSubject = userDetails.getEmail();

        return Jwts.builder()
                .subject(emailSubject)
                .claim("email", emailSubject)
                .claim("accountId", userDetails.getId())
                .claim("role", userDetails.getRole().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateToken(Account account) {
        return generateAccessToken(new CustomUserDetails(account));
    }

    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        String emailClaim = claims.get("email", String.class);
        if (StringUtils.hasText(emailClaim)) {
            return emailClaim.trim();
        }
        String subject = claims.getSubject();
        return StringUtils.hasText(subject) ? subject.trim() : null;
    }

    public String getEmailFromToken(String token) {
        return extractUsername(token);
    }

    public Long extractAccountId(String token) {
        Claims claims = extractAllClaims(token);
        Object accountIdObj = claims.get("accountId");
        if (accountIdObj instanceof Number number) {
            return number.longValue();
        }
        if (accountIdObj instanceof String str && StringUtils.hasText(str)) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public Claims extractAllClaims(String token) {
        String cleaned = cleanToken(token);
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(cleaned)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            String cleaned = cleanToken(token);
            if (!StringUtils.hasText(cleaned)) {
                return false;
            }
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(cleaned);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("JWT validation failed: {}", ex.getMessage(), ex);
            return false;
        }
    }

    public static String cleanToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        String cleaned = rawToken.trim();
        // Xử lý trường hợp người dùng dán cả chữ "Bearer " vào ô Authorize của Swagger UI
        while (cleaned.regionMatches(true, 0, "Bearer ", 0, 7)) {
            cleaned = cleaned.substring(7).trim();
        }
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }
}
