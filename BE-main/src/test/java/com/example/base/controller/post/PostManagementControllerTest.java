package com.example.base.controller.post;

import com.example.base.config.SecurityConfig;
import com.example.base.dto.post.*;
import com.example.base.security.*;
import com.example.base.service.post.PostManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostManagementController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class,JwtAuthenticationEntryPoint.class})
class PostManagementControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PostManagementService management;
    @MockBean JwtTokenProvider tokens;
    @MockBean CustomUserDetailsService users;

    @Test void guestsCannotReadOrMutate() throws Exception {
        mvc.perform(get("/api/posts/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/posts/1/edit")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/posts/1").contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(management);
    }

    @Test @WithMockUser(roles="MANAGER") void managementRoleCannotMutateUserPosts() throws Exception {
        mvc.perform(delete("/api/posts/1").contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(management);
    }

    @Test @WithMockUser(roles="EXTERNAL_USER") void mineReturnsPaginationAndPreventsCaching() throws Exception {
        when(management.listMine(eq(1),eq(12),eq("ALL"),any()))
                .thenReturn(new MyPostPageDto(List.of(),1,12,0,0,true,true));
        mvc.perform(get("/api/posts/mine")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test @WithMockUser void invalidPageIsRejected() throws Exception {
        mvc.perform(get("/api/posts/mine").param("page","abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(management);
    }

    @ParameterizedTest @ValueSource(strings={"", "   "}) @WithMockUser
    void softDeleteRequiresNonBlankReason(String reason) throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("reason", reason));
        mvc.perform(delete("/api/posts/1").contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(management);
    }

    @Test @WithMockUser
    void softDeleteReturnsSuccessWithoutExposingPostData() throws Exception {
        String reason = "No longer needed";
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("reason", reason));
        mvc.perform(delete("/api/posts/1").contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true));
        verify(management).delete(eq(1L), eq(new DeletePostRequest(reason)), any());
    }

    @Test @WithMockUser void updatesValidateFutureTimeAndImageCount() throws Exception {
        String json="""
                {"title":"Changed","description":"Description","categoryId":1,"locationText":"Hall A",
                 "eventAt":"2999-01-01T00:00:00Z","custodyMode":"P2P","keptImageIds":[1,2,3,4,5,6],
                 "expectedUpdatedAt":"2026-01-01T00:00:00Z"}
                """;
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT,"/api/posts/1")
                .file(new MockMultipartFile("data","data.json","application/json",json.getBytes())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(management);
    }
}
