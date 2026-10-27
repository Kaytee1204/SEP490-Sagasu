package com.example.base.controller.post;

import com.example.base.config.SecurityConfig;
import com.example.base.dto.post.PostDetailDto;
import com.example.base.repository.PostRepository;
import com.example.base.security.CustomUserDetailsService;
import com.example.base.security.JwtAuthenticationFilter;
import com.example.base.security.JwtTokenProvider;
import com.example.base.security.JwtAuthenticationEntryPoint;
import com.example.base.service.post.PostService;
import com.example.base.service.post.PostCreationService;
import com.example.base.storage.ImageStorage;
import com.example.base.entity.Post;
import com.example.base.entity.Category;
import com.example.base.mapper.PostMapper;
import com.example.base.dto.post.CreatedPostDto;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@Import({ PostService.class, PostMapper.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class })
class PostControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    PostRepository posts;
    @MockBean
    com.example.base.repository.CategoryRepository categories;
    @MockBean
    JwtTokenProvider tokens;
    @MockBean
    CustomUserDetailsService users;
    @MockBean
    ImageStorage storage;
    @MockBean
    PostCreationService creation;
    @MockBean
    com.example.base.service.post.PostPrivateIdentifiersService privateIdentifiers;

    @Test
    void anonymousCannotAccessPrivateIdentifiers() throws Exception {
        mvc.perform(get("/api/posts/1/private-identifiers")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/posts/1/private-identifiers")
                .contentType("application/json").content("{\"privateIdentifiers\":\"secret\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(privateIdentifiers);
    }

    @Test
    @WithMockUser
    void privateResponsesAreNotCachedAndUpdatesDoNotEchoSecrets() throws Exception {
        when(privateIdentifiers.read(eq(1L), any()))
                .thenReturn(new com.example.base.dto.post.PrivateIdentifiersDto("SECRET"));
        mvc.perform(get("/api/posts/1/private-identifiers")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.privateIdentifiers").value("SECRET"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/posts/1/private-identifiers")
                .contentType("application/json").content("{\"privateIdentifiers\":\"SECRET\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
    }

    @Test
    @WithMockUser
    void missingOrOversizedIdentifiersAreRejected() throws Exception {
        for (String json : List.of("{}", "{\"privateIdentifiers\":\"" + "a".repeat(5001) + "\"}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/posts/1/private-identifiers")
                    .contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(privateIdentifiers);
    }

    @ParameterizedTest
    @ValueSource(strings = { "P2P", "OFFICE" })
    @WithMockUser(roles = "USER")
    void createsFoundPost(String custody) throws Exception {
        when(creation.createFound(any(), any(), any())).thenReturn(new CreatedPostDto(99L, "FOUND", "OPEN", "VISIBLE"));
        String json = new String(data("2026-01-01T10:00:00+07:00").getBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("}", ",\"custodyMode\":\"" + custody + "\"}");
        mvc.perform(multipart("/api/posts/found")
                .file(new MockMultipartFile("data", "data.json", "application/json", json.getBytes())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.postType").value("FOUND"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void foundRequiresValidCustodyChoice() throws Exception {
        mvc.perform(multipart("/api/posts/found").file(data("2026-01-01T10:00:00+07:00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(creation);
    }

    @Test
    void anonymousCannotListPosts() throws Exception {
        mvc.perform(get("/api/posts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(posts);
    }

    @Test
    @WithMockUser
    void emptyListHasPaginationAndSkipsBulkFetch() throws Exception {
        when(posts.findVisibleIds(any())).thenReturn(Page.empty(PageRequest.of(0, 12)));
        mvc.perform(get("/api/posts")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(posts, never()).findVisibleByIds(any());
    }

    @Test
    @WithMockUser
    void listPreservesPageOrderAndExcludesSecrets() throws Exception {
        var first = mock(Post.class);
        var second = mock(Post.class);
        var category = mock(Category.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(first.getCategory()).thenReturn(category);
        when(second.getCategory()).thenReturn(category);
        when(first.getPrivateIdentifiers()).thenReturn("PRIVATE-FIRST");
        when(posts.findVisibleIds(any())).thenReturn(new PageImpl<>(List.of(2L, 1L), PageRequest.of(0, 12), 2));
        when(posts.findVisibleByIds(List.of(2L, 1L))).thenReturn(List.of(first, second));
        mvc.perform(get("/api/posts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(2))
                .andExpect(jsonPath("$.data.content[1].id").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[1].privateIdentifiers").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].moderationNote").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].storageKey").doesNotExist());
        verify(first, never()).getPrivateIdentifiers();
    }

    @ParameterizedTest
    @CsvSource({ "0,12", "1,0", "1,101", "abc,12", "9999999999999,12" })
    @WithMockUser
    void invalidPaginationIsRejected(String page, String size) throws Exception {
        mvc.perform(get("/api/posts").param("page", page).param("size", size)).andExpect(status().isBadRequest());
        verifyNoInteractions(posts);
    }

    private MockMultipartFile data(String eventAt) {
        String json = """
                {"title":"Lost wallet","description":"Public description","categoryId":1,
                 "locationText":"Hall A","eventAt":"%s"}
                """.formatted(eventAt);
        return new MockMultipartFile("data", "data.json", "application/json",
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void anonymousCannotCreatePost() throws Exception {
        mvc.perform(multipart("/api/posts/lost").file(data("2026-01-01T10:00:00+07:00")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(creation);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void managementCannotUseSelfPostingEndpoint() throws Exception {
        mvc.perform(multipart("/api/posts/lost").file(data("2026-01-01T10:00:00+07:00")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(creation);
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidFutureEventIsRejectedBeforeWritingOrUploading() throws Exception {
        mvc.perform(multipart("/api/posts/lost").file(data("2999-01-01T10:00:00+07:00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(creation);
    }

    @Test
    @WithMockUser(roles = "USER")
    void missingJsonPartIsRejected() throws Exception {
        mvc.perform(multipart("/api/posts/lost")).andExpect(status().isBadRequest());
        verifyNoInteractions(creation);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createsImmediatelyVisibleUserPostWith201() throws Exception {
        when(creation.createLost(any(), any(), any())).thenReturn(new CreatedPostDto(99L, "LOST", "OPEN", "VISIBLE"));
        mvc.perform(multipart("/api/posts/lost").file(data("2026-01-01T10:00:00+07:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.moderationStatus").value("VISIBLE"))
                .andExpect(jsonPath("$.message").value("Đăng bài thành công"));
    }

    @Test
    @WithMockUser(roles = "CHECKER")
    void checkerCreatesPendingLostPost() throws Exception {
        when(creation.createLost(any(), any(), any())).thenReturn(new CreatedPostDto(99L, "LOST", "OPEN", "PENDING"));
        mvc.perform(multipart("/api/posts/lost").file(data("2026-01-01T10:00:00+07:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.moderationStatus").value("PENDING"));

    }

    @Test
    void anonymousCannotReadPost() throws Exception {
        mvc.perform(get("/api/posts/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(posts);
    }

    @Test
    @WithMockUser(roles = "USER")
    void returnsPublicFieldsOnlyAndPreventsCaching() throws Exception {
        var post = mock(Post.class);
        var category = mock(Category.class);
        when(post.getId()).thenReturn(1L);
        when(post.getTitle()).thenReturn("Ví thất lạc");
        when(post.getCategory()).thenReturn(category);
        when(category.getCategoryId()).thenReturn(1L);
        when(category.getName()).thenReturn("Ví");
        when(post.getPrivateIdentifiers()).thenReturn("NEVER-EXPOSE-THIS");
        when(posts.findVisibleDetail(1)).thenReturn(Optional.of(post));
        mvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.title").value("Ví thất lạc"))
                .andExpect(jsonPath("$.data.images").isArray())
                .andExpect(jsonPath("$.data.privateIdentifiers").doesNotExist())
                .andExpect(jsonPath("$.data.private_identifiers").doesNotExist())
                .andExpect(jsonPath("$.data.moderationNote").doesNotExist());
    }

    @Test
    @WithMockUser
    void unavailablePostReturns404() throws Exception {
        when(posts.findVisibleDetail(42)).thenReturn(Optional.empty());
        mvc.perform(get("/api/posts/42")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-1", "abc", "99999999999999999999999" })
    @WithMockUser
    void invalidIdReturns400(String id) throws Exception {
        mvc.perform(get("/api/posts/" + id)).andExpect(status().isBadRequest());
        verifyNoInteractions(posts);
    }
}
