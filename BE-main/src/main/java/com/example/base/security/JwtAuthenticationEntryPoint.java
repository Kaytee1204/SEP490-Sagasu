package com.example.base.security;

import com.example.base.exception.ErrorCode;
import com.example.base.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String jwtError = (String) request.getAttribute("jwt_auth_error");
        String authHeader = request.getHeader("Authorization");
        boolean hasAuthHeader = StringUtils.hasText(authHeader);

        log.warn("401 Unauthorized on [{} {}] | hasAuthorizationHeader={} | jwtError={} | springMessage={}",
                request.getMethod(),
                request.getRequestURI(),
                hasAuthHeader,
                jwtError != null ? jwtError : "none",
                authException != null ? authException.getMessage() : "none");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getDefaultMessage()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
