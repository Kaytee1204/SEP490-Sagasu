package com.example.base.security;

import com.example.base.entity.Account;
import com.example.base.entity.AccountStatus;
import com.example.base.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock AccountRepository accounts;
    @InjectMocks CustomUserDetailsService service;

    @Test
    void utc019_loadUserByUsername_trimsEmail() {
        when(accounts.findByEmailIgnoreCase("user7@gmail.com")).thenReturn(Optional.of(JwtTokenProviderTest.account()));

        UserDetails d = service.loadUserByUsername("  user7@gmail.com ");

        assertThat(d.getUsername()).isEqualTo("user7@gmail.com");
    }

    @Test
    void utc020_loadUserByUsername_missing_throws() {
        when(accounts.findByEmailIgnoreCase("")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void utc021_loadUserById_foundAndMissing() {
        when(accounts.findById(7L)).thenReturn(Optional.of(JwtTokenProviderTest.account()));
        when(accounts.findById(9L)).thenReturn(Optional.empty());

        assertThat(((CustomUserDetails) service.loadUserById(7L)).getId()).isEqualTo(7L);
        assertThatThrownBy(() -> service.loadUserById(9L)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void utc022_customUserDetails_statusFlags() {
        Account a = JwtTokenProviderTest.account();
        a.setPasswordHash("hash");
        CustomUserDetails active = new CustomUserDetails(a);
        assertThat(active.isEnabled()).isTrue();
        assertThat(active.isAccountNonLocked()).isTrue();
        assertThat(active.isAccountNonExpired()).isTrue();
        assertThat(active.isCredentialsNonExpired()).isTrue();
        assertThat(active.getPassword()).isEqualTo("hash");
        assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        a.setStatus(AccountStatus.PENDING_ACTIVATION);
        assertThat(new CustomUserDetails(a).isEnabled()).isTrue();

        a.setStatus(AccountStatus.SUSPENDED);
        CustomUserDetails suspended = new CustomUserDetails(a);
        assertThat(suspended.isEnabled()).isFalse();
        assertThat(suspended.isAccountNonLocked()).isFalse();
    }

    @Test
    void utc023_customUserDetails_lockAndValidity() {
        Account a = JwtTokenProviderTest.account();
        a.setLockedUntil(OffsetDateTime.now().plusMinutes(5));
        a.setValidUntil(OffsetDateTime.now().minusSeconds(1));
        CustomUserDetails d = new CustomUserDetails(a);
        assertThat(d.isAccountNonLocked()).isFalse();
        assertThat(d.isAccountNonExpired()).isFalse();

        a.setLockedUntil(OffsetDateTime.now().minusMinutes(5));
        a.setValidUntil(OffsetDateTime.now().plusDays(1));
        d = new CustomUserDetails(a);
        assertThat(d.isAccountNonLocked()).isTrue();
        assertThat(d.isAccountNonExpired()).isTrue();
    }

    @Test
    void utc024_entryPoint_writes401Json() throws Exception {
        var entryPoint = new JwtAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());
        var request = new MockHttpServletRequest("GET", "/api/posts");
        request.setAttribute("jwt_auth_error", "expired");
        var response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"success\":false");
    }
}
