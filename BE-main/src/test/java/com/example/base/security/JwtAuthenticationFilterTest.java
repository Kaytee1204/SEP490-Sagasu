package com.example.base.security;

import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.AccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock CustomUserDetailsService userDetailsService;
    @Mock FilterChain chain;

    JwtTokenProvider tokens = new JwtTokenProvider(JwtTokenProviderTest.SECRET, 60_000);
    JwtAuthenticationFilter filter;
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Account account;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokens, userDetailsService);
        SecurityContextHolder.clearContext();
        account = JwtTokenProviderTest.account();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void utc011_validBearerToken_authenticatesWithBothRoleForms() throws Exception {
        when(userDetailsService.loadUserByUsername("user7@gmail.com")).thenReturn(new CustomUserDetails(account));
        request.addHeader("Authorization", "Bearer " + tokens.generateToken(account));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNotNull();
        assertThat(auth().getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_APPROVER", "APPROVER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void utc012_tokenInCookie_authenticates() throws Exception {
        when(userDetailsService.loadUserByUsername("user7@gmail.com")).thenReturn(new CustomUserDetails(account));
        request.setCookies(new Cookie("other", "x"), new Cookie("access_token", tokens.generateToken(account)));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNotNull();
    }

    @Test
    void utc013_noToken_continuesUnauthenticated() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void utc014_invalidToken_setsErrorAttributeAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNull();
        assertThat(request.getAttribute("jwt_auth_error")).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void utc015_suspendedAccount_rejectedWithMessage() throws Exception {
        account.setStatus(AccountStatus.SUSPENDED);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(new CustomUserDetails(account));
        request.addHeader("Authorization", tokens.generateToken(account));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNull();
        assertThat((String) request.getAttribute("jwt_auth_error")).contains("khóa");
    }

    @Test
    void utc016_expiredAccount_rejected() throws Exception {
        account.setValidUntil(OffsetDateTime.now().minusDays(1));
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(new CustomUserDetails(account));
        request.addHeader("Authorization", tokens.generateToken(account));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNull();
    }

    @Test
    void utc017_emailNotFound_fallsBackToAccountId() throws Exception {
        when(userDetailsService.loadUserByUsername(anyString())).thenThrow(new UsernameNotFoundException("x"));
        when(userDetailsService.loadUserById(7L)).thenReturn(new CustomUserDetails(account));
        request.addHeader("Authorization", tokens.generateToken(account));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isNotNull();
        verify(userDetailsService).loadUserById(7L);
    }

    @Test
    void utc018_alreadyAuthenticated_contextNotReplaced() throws Exception {
        Authentication existing = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("Authorization", tokens.generateToken(account));

        filter.doFilterInternal(request, response, chain);

        assertThat(auth()).isSameAs(existing);
        verifyNoInteractions(userDetailsService);
    }
}
