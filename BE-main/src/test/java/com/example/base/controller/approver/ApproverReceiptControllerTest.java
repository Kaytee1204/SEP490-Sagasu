package com.example.base.controller.approver;

import com.example.base.config.SecurityConfig;
import com.example.base.security.*;
import com.example.base.service.approver.ApproverIntakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApproverReceiptController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ApproverReceiptControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ApproverIntakeService service;
    @MockBean JwtTokenProvider tokens;
    @MockBean CustomUserDetailsService users;

    @Test void anonymousCannotReadIntakes() throws Exception {
        mvc.perform(get("/api/approver/receipts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "USER")
    void userCannotConfirmIntake() throws Exception {
        mvc.perform(post("/api/approver/receipts/1/submit").contentType("application/json")
                .content("{\"expectedUpdatedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "CHECKER")
    void checkerCannotUseApproverWorkspace() throws Exception {
        mvc.perform(get("/api/approver/receipts")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "APPROVER")
    void approverListUsesAllFilterAndNoCache() throws Exception {
        mvc.perform(get("/api/approver/receipts")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).list(1, 10, "", "ALL", null);
    }

    @Test @WithMockUser(roles = "APPROVER")
    void confirmationRequiresExpectedVersion() throws Exception {
        mvc.perform(post("/api/approver/receipts/1/submit").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
