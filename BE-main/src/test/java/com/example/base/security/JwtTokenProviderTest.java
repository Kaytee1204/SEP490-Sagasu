package com.example.base.security;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    static final String SECRET = "unit-test-secret-key-that-is-at-least-32-bytes-long!!";
    final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

    static Account account() {
        Account a = new Account();
        a.setId(7L);
        a.setEmail("user7@gmail.com");
        a.setRole(AccountRole.APPROVER);
        a.setStatus(AccountStatus.ACTIVE);
        return a;
    }

    private String sign(io.jsonwebtoken.JwtBuilder b, String secret) {
        return b.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void utc001_generateToken_containsEmailAccountIdAndRole() {
        String token = provider.generateToken(account());

        var claims = provider.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("user7@gmail.com");
        assertThat(claims.get("role", String.class)).isEqualTo("APPROVER");
        assertThat(provider.extractAccountId(token)).isEqualTo(7L);
        assertThat(provider.extractUsername(token)).isEqualTo("user7@gmail.com");
        assertThat(provider.getEmailFromToken(token)).isEqualTo("user7@gmail.com");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void utc002_validateToken_valid_true() {
        assertThat(provider.validateToken(provider.generateToken(account()))).isTrue();
    }

    @Test
    void utc003_validateToken_bearerPrefixAndQuotes_cleanedAndValid() {
        String token = provider.generateToken(account());
        assertThat(provider.validateToken("Bearer Bearer \"" + token + "\"")).isTrue();
    }

    @Test
    void utc004_validateToken_wrongSignature_false() {
        String other = sign(Jwts.builder().subject("x"), "another-secret-key-that-is-at-least-32-bytes!!");
        assertThat(provider.validateToken(other)).isFalse();
    }

    @Test
    void utc005_validateToken_expired_false() {
        String expired = sign(Jwts.builder().subject("x").expiration(new Date(System.currentTimeMillis() - 1000)), SECRET);
        assertThat(provider.validateToken(expired)).isFalse();
        assertThatThrownBy(() -> provider.extractAllClaims(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void utc006_validateToken_blankOrGarbage_false() {
        assertThat(provider.validateToken(null)).isFalse();
        assertThat(provider.validateToken("   ")).isFalse();
        assertThat(provider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void utc007_extractUsername_noEmailClaim_fallsBackToSubject() {
        String token = sign(Jwts.builder().subject("  sub@gmail.com "), SECRET);
        assertThat(provider.extractUsername(token)).isEqualTo("sub@gmail.com");
    }

    @Test
    void utc008_extractUsername_noEmailNoSubject_null() {
        String token = sign(Jwts.builder().claim("accountId", 7), SECRET);
        assertThat(provider.extractUsername(token)).isNull();
    }

    @Test
    void utc009_extractAccountId_stringNumericOrInvalid() {
        assertThat(provider.extractAccountId(sign(Jwts.builder().claim("accountId", " 42 "), SECRET))).isEqualTo(42L);
        assertThat(provider.extractAccountId(sign(Jwts.builder().claim("accountId", "abc"), SECRET))).isNull();
        assertThat(provider.extractAccountId(sign(Jwts.builder().subject("x"), SECRET))).isNull();
    }

    @Test
    void utc010_cleanToken_edgeCases() {
        assertThat(JwtTokenProvider.cleanToken(null)).isNull();
        assertThat(JwtTokenProvider.cleanToken("  ")).isNull();
        assertThat(JwtTokenProvider.cleanToken("bearer abc")).isEqualTo("abc");
        assertThat(JwtTokenProvider.cleanToken("\"abc\"")).isEqualTo("abc");
        assertThat(JwtTokenProvider.cleanToken("\"\"")).isEqualTo("\"\"");
    }
}
