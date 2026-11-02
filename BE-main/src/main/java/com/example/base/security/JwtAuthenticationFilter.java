package com.example.base.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (StringUtils.hasText(token)) {
                String email = jwtTokenProvider.extractUsername(token);
                if (!StringUtils.hasText(email)) {
                    email = jwtTokenProvider.getEmailFromToken(token);
                }
                Long accountId = jwtTokenProvider.extractAccountId(token);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = resolveUserDetails(email, accountId);

                    if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked() || !userDetails.isAccountNonExpired()) {
                        log.warn("JWT Auth rejected for user [{}]: enabled={}, nonLocked={}, nonExpired={}",
                                userDetails.getUsername(),
                                userDetails.isEnabled(),
                                userDetails.isAccountNonLocked(),
                                userDetails.isAccountNonExpired());
                        request.setAttribute("jwt_auth_error", "Tài khoản đang bị khóa, hết hạn hoặc đã ngừng hoạt động");
                    } else {
                        Collection<? extends GrantedAuthority> authorities = buildAuthorities(userDetails);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        authorities
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception e) {
            log.error("JWT Auth failed: ", e);
            request.setAttribute("jwt_auth_error", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private UserDetails resolveUserDetails(String email, Long accountId) {
        if (StringUtils.hasText(email)) {
            try {
                return userDetailsService.loadUserByUsername(email);
            } catch (UsernameNotFoundException ex) {
                if (accountId != null) {
                    log.warn("Không tìm thấy user theo email/subject '{}', tra cứu dự phòng theo accountId={}", email, accountId);
                    return userDetailsService.loadUserById(accountId);
                }
                throw ex;
            }
        }
        if (accountId != null) {
            return userDetailsService.loadUserById(accountId);
        }
        throw new UsernameNotFoundException("Token không chứa email (sub) hoặc accountId hợp lệ");
    }

    private Collection<? extends GrantedAuthority> buildAuthorities(UserDetails userDetails) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(userDetails.getAuthorities());
        for (GrantedAuthority authority : userDetails.getAuthorities()) {
            String auth = authority.getAuthority();
            if (StringUtils.hasText(auth)) {
                if (auth.startsWith("ROLE_")) {
                    authorities.add(new SimpleGrantedAuthority(auth.substring(5)));
                } else {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + auth));
                }
            }
        }
        return authorities;
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Lấy từ Header Authorization: Bearer <token>
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)) {
            return JwtTokenProvider.cleanToken(bearerToken);
        }

        // 2. Hỗ trợ lấy từ Cookie nếu có (accessToken / access_token / token / jwt)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                String name = cookie.getName();
                if ("accessToken".equalsIgnoreCase(name)
                        || "access_token".equalsIgnoreCase(name)
                        || "token".equalsIgnoreCase(name)
                        || "jwt".equalsIgnoreCase(name)) {
                    String value = JwtTokenProvider.cleanToken(cookie.getValue());
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
