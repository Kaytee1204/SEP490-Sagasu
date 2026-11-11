package com.example.base.service.post;

import com.example.base.dto.post.SmartMatchItemDto;
import com.example.base.entity.Account;
import com.example.base.entity.AccountRole;
import com.example.base.entity.Category;
import com.example.base.entity.Post;
import com.example.base.entity.PostImage;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.example.base.repository.PostRepository;
import com.example.base.storage.ImageStorage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartMatchServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private ImageStorage imageStorage;

    private SmartMatchService service;
    private HttpServer aiServer;
    private final OffsetDateTime baseTime = OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new SmartMatchService(postRepository, imageStorage);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:19999");
    }

    @AfterEach
    void tearDown() {
        if (aiServer != null) {
            aiServer.stop(0);
            aiServer = null;
        }
    }

    @Test
    void findSmartMatches_whenOwnerAndAiReturnsMatches_mapsDtosFromAiResponse() throws IOException {
        Account owner = account(5L, "Owner Five", AccountRole.USER);
        Category cat = category(1L, "Wallet");
        Post source = lostPost(10L, owner, owner, cat, "Black wallet", "Leather wallet", "Hall A", baseTime);
        Post cand1 = foundPost(101L, owner, cat, "Found wallet 1", "Desc 1", "Hall A", baseTime);
        Post cand2 = foundPost(102L, owner, cat, "Found wallet 2", "Desc 2", "Hall B", baseTime);

        String responseJson = """
                {
                  "matches": [
                    {
                      "id": 101,
                      "title": "Found wallet 1",
                      "postType": "FOUND",
                      "categoryName": "Wallet",
                      "locationText": "Hall A",
                      "eventAt": "2026-05-10T10:00:00Z",
                      "imageUrl": "https://cdn/101.png",
                      "authorName": "Owner Five",
                      "status": "OPEN",
                      "matchScore": 91.5,
                      "matchBreakdown": {"visual": 92.0},
                      "matchReason": "AI matched visually"
                    },
                    {
                      "id": 102,
                      "title": "Found wallet 2",
                      "postType": "FOUND",
                      "categoryName": "Wallet",
                      "locationText": "Hall B",
                      "eventAt": "2026-05-10T10:00:00Z",
                      "imageUrl": null,
                      "authorName": "Owner Five",
                      "status": "OPEN",
                      "matchScore": 77.0,
                      "matchBreakdown": {"text": 80.0},
                      "matchReason": "Text overlap"
                    }
                  ]
                }
                """;
        AtomicReference<String> requestedPath = new AtomicReference<>();
        int port = startAiServer(responseJson, requestedPath);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:" + port);

        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(source));
        when(postRepository.findCandidateIdsForMatching("FOUND", 1L, 10L, PageRequest.of(0, 20)))
                .thenReturn(List.of(101L, 102L));
        when(postRepository.findVisibleByIds(List.of(101L, 102L))).thenReturn(new ArrayList<>(List.of(cand2, cand1)));

        List<SmartMatchItemDto> result = service.findSmartMatches(10L, 5L, AccountRole.USER);

        assertEquals("/api/match", requestedPath.get());
        assertEquals(2, result.size());
        assertEquals(101L, result.get(0).getId());
        assertEquals(91.5, result.get(0).getMatchScore());
        assertEquals("AI matched visually", result.get(0).getMatchReason());
        assertEquals(102L, result.get(1).getId());
    }

    @Test
    void findSmartMatches_whenUnauthorizedOrInvalidPostOrNotVisible_throwsExpectedError() {
        AppException invalidId = assertThrows(AppException.class,
                () -> service.findSmartMatches(0L, 5L, AccountRole.USER));
        assertEquals(ErrorCode.POST_ID_INVALID, invalidId.getErrorCode());

        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.empty());
        when(postRepository.findOwnDetail(10L, 6L)).thenReturn(Optional.empty());
        AppException notVisible = assertThrows(AppException.class,
                () -> service.findSmartMatches(10L, 6L, AccountRole.USER));
        assertEquals(ErrorCode.POST_NOT_VISIBLE, notVisible.getErrorCode());

        Account owner = account(5L, "Owner", AccountRole.USER);
        Post source = lostPost(10L, owner, owner, category(1L, "Wallet"), "Lost", "Desc", "Hall A", baseTime);
        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(source));

        AppException outsider = assertThrows(AppException.class,
                () -> service.findSmartMatches(10L, 6L, AccountRole.USER));
        assertEquals(ErrorCode.FORBIDDEN, outsider.getErrorCode());

        AppException nullViewer = assertThrows(AppException.class,
                () -> service.findSmartMatches(10L, null, null));
        assertEquals(ErrorCode.FORBIDDEN, nullViewer.getErrorCode());
    }

    @Test
    void findSmartMatches_whenAdminManagerOrCreatedByOwner_allowsAccess() {
        Account accountOwner = account(5L, "Account Owner", AccountRole.USER);
        Account creator = account(8L, "Author", AccountRole.USER);
        Post source = lostPost(10L, accountOwner, creator, category(1L, "Wallet"), "Lost", "Desc", "Hall A", baseTime);
        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(source));
        when(postRepository.findCandidateIdsForMatching("FOUND", 1L, 10L, PageRequest.of(0, 20)))
                .thenReturn(List.of());

        assertTrue(service.findSmartMatches(10L, 1L, AccountRole.ADMIN).isEmpty());
        assertTrue(service.findSmartMatches(10L, 2L, AccountRole.MANAGER).isEmpty());
        assertTrue(service.findSmartMatches(10L, 8L).isEmpty());
    }

    @Test
    void findSmartMatches_whenSourceLostOrFound_queriesOppositeTypeAndHandlesEmptyCandidates() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category cat = category(3L, "Phone");
        Post lost = lostPost(10L, owner, owner, cat, "Phone", "Black", "Hall A", baseTime);
        Post found = foundPost(11L, owner, cat, "Phone", "Black", "Hall A", baseTime);

        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(lost));
        when(postRepository.findCandidateIdsForMatching("FOUND", 3L, 10L, PageRequest.of(0, 20)))
                .thenReturn(List.of());
        assertTrue(service.findSmartMatches(10L, 5L).isEmpty());
        verify(postRepository, never()).findVisibleByIds(any());

        when(postRepository.findVisibleDetail(11L)).thenReturn(Optional.of(found));
        when(postRepository.findCandidateIdsForMatching("LOST", 3L, 11L, PageRequest.of(0, 20)))
                .thenReturn(List.of(99L));
        when(postRepository.findVisibleByIds(List.of(99L))).thenReturn(List.of());
        assertTrue(service.findSmartMatches(11L, 5L).isEmpty());
        verify(postRepository).findCandidateIdsForMatching("LOST", 3L, 11L, PageRequest.of(0, 20));
    }

    @Test
    void findSmartMatches_whenAiTrailingSlashesMissingDefaultsOrEmptyMatches_handlesAiResponses() throws IOException {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category cat = category(1L, "Wallet");
        Post source = lostPost(10L, owner, owner, cat, "Black wallet", "Leather", "Hall A", baseTime);
        Post cand = foundPost(20L, owner, cat, "Black wallet", "Leather", "Hall A", baseTime);

        AtomicReference<String> pathWithSlashes = new AtomicReference<>();
        int port = startAiServer("""
                {
                  "matches": [
                    {
                      "id": 20,
                      "title": "Black wallet"
                    }
                  ]
                }
                """, pathWithSlashes);
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:" + port + "///");
        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(source));
        when(postRepository.findCandidateIdsForMatching("FOUND", 1L, 10L, PageRequest.of(0, 20)))
                .thenReturn(List.of(20L));
        when(postRepository.findVisibleByIds(List.of(20L))).thenReturn(new ArrayList<>(List.of(cand)));

        List<SmartMatchItemDto> defaultsResult = service.findSmartMatches(10L, 5L, AccountRole.USER);
        assertEquals("/api/match", pathWithSlashes.get());
        assertEquals(1, defaultsResult.size());
        assertEquals(50.0, defaultsResult.get(0).getMatchScore());
        assertEquals("Trùng khớp đặc điểm", defaultsResult.get(0).getMatchReason());

        aiServer.stop(0);
        aiServer = null;
        startAiServer("{\"matches\": []}", new AtomicReference<>());
        ReflectionTestUtils.setField(service, "aiServiceUrl", "http://127.0.0.1:" + aiServer.getAddress().getPort());

        List<SmartMatchItemDto> fallbackFromEmptyAi = service.findSmartMatches(10L, 5L, AccountRole.USER);
        assertEquals(1, fallbackFromEmptyAi.size());
        assertEquals(20L, fallbackFromEmptyAi.get(0).getId());
        assertTrue(fallbackFromEmptyAi.get(0).getMatchScore() >= 45.0);
    }

    @Test
    void findSmartMatches_whenAiThrowsResourceAccessException_fallsBackToNativeScoring() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category cat = category(1L, "Wallet");
        Post source = lostPost(10L, owner, owner, cat, "Black leather wallet", "Lost card holder", "Hall A", baseTime);
        Post cand = foundPost(21L, owner, cat, "Black leather wallet", "Found card holder", "Hall A", baseTime);

        when(postRepository.findVisibleDetail(10L)).thenReturn(Optional.of(source));
        when(postRepository.findCandidateIdsForMatching("FOUND", 1L, 10L, PageRequest.of(0, 20)))
                .thenReturn(List.of(21L));
        when(postRepository.findVisibleByIds(List.of(21L))).thenReturn(new ArrayList<>(List.of(cand)));

        try (MockedConstruction<RestTemplate> ignored = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(java.util.Map.class)))
                        .thenThrow(new ResourceAccessException("Read timed out")))) {
            List<SmartMatchItemDto> results = service.findSmartMatches(10L, 5L, AccountRole.USER);
            assertEquals(1, results.size());
            assertEquals(21L, results.get(0).getId());
            assertTrue(results.get(0).getMatchScore() >= 90.0);
        }
    }

    @Test
    void nativeFallback_whenSameCategoryLocationDayTextNoImages_scoresUpToMax97_5WithReasons() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category cat = category(1L, "Wallet");
        Post source = lostPost(10L, owner, owner, cat, "Black leather wallet", "Contains silver zipper", "Hall A", baseTime);
        Post cand = foundPost(30L, owner, cat, "Black leather wallet", "Contains silver zipper", "Hall A", baseTime);

        List<SmartMatchItemDto> results = invokeFallbackWithMockedTimeout(source, List.of(cand));

        assertEquals(1, results.size());
        SmartMatchItemDto dto = results.get(0);
        assertEquals(97.5, dto.getMatchScore());
        assertTrue(dto.getMatchReason().contains("Cùng danh mục Wallet"));
        assertTrue(dto.getMatchReason().contains("Thời gian & địa điểm gần nhau"));
        assertTrue(dto.getMatchReason().contains("Mô tả khớp"));
    }

    @Test
    void nativeFallback_whenLowScoreOrBothImagesVisualMismatch_filtersOutCandidates() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Post sourceNoImg = lostPost(10L, owner, owner, category(1L, "Wallet"), "brown coin pouch", "leather", "North Gate", baseTime);
        Post farDiffCand = foundPost(31L, owner, category(2L, "Bottle"), "stainless thermo flask", "blue cap", "South Canteen",
                baseTime.minusDays(60));
        assertTrue(invokeFallbackWithMockedTimeout(sourceNoImg, List.of(farDiffCand)).isEmpty());

        when(imageStorage.readUrl(any(), any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
        Post sourceWithImg = lostPost(12L, owner, owner, category(1L, "Wallet"), "silver watch", "metal band", "Hall A", baseTime);
        ReflectionTestUtils.setField(sourceWithImg, "images", List.of(new PostImage("src.jpg", 0, "image/jpeg", 100, 10, 10, "h1")));
        Post candWithImg = foundPost(32L, owner, category(2L, "Backpack"), "orange umbrella", "plastic handle", "Hall A", baseTime);
        ReflectionTestUtils.setField(candWithImg, "images", List.of(new PostImage("cand.jpg", 0, "image/jpeg", 100, 10, 10, "h2")));

        assertTrue(invokeFallbackWithMockedTimeout(sourceWithImg, List.of(candWithImg)).isEmpty());
    }

    @Test
    void nativeFallback_whenSharedLocationTokensNullEventAtNullAuthorOrPunctuationOnly_handlesBoundaries() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category wallet = category(1L, "Wallet");
        Category phone = category(2L, "Phone");

        Post sourceTokenLoc = lostPost(10L, owner, owner, wallet, "Leather wallet", "Brown color", "Library floor 2", baseTime);
        Post candTokenLoc = foundPost(40L, owner, wallet, "Leather wallet", "Brown color", "Library floor 3", baseTime);
        List<SmartMatchItemDto> tokenLocRes = invokeFallbackWithMockedTimeout(sourceTokenLoc, List.of(candTokenLoc));
        assertEquals(1, tokenLocRes.size());
        assertEquals(89.0, ((Number) tokenLocRes.get(0).getMatchBreakdown().get("spatioTemporal")).doubleValue());

        Post sourceNullTime = lostPost(10L, owner, owner, wallet, "Leather wallet", "Brown color", "Hall A", null);
        Post candNoAuthor = foundPost(41L, null, wallet, "Leather wallet", "Brown color", "Hall A", baseTime);
        List<SmartMatchItemDto> nullTimeRes = invokeFallbackWithMockedTimeout(sourceNullTime, List.of(candNoAuthor));
        assertEquals(1, nullTimeRes.size());
        assertEquals("Người dùng", nullTimeRes.get(0).getAuthorName());
        assertEquals(75.0, ((Number) nullTimeRes.get(0).getMatchBreakdown().get("spatioTemporal")).doubleValue());

        Post punctSource = lostPost(10L, owner, owner, wallet, "a ! ?", "b . ,", "Hall A", baseTime);
        Post punctCand = foundPost(42L, owner, phone, "x ; :", "y / -", "Hall A", baseTime);
        List<SmartMatchItemDto> punctRes = invokeFallbackWithMockedTimeout(punctSource, List.of(punctCand));
        assertEquals(1, punctRes.size());
        assertEquals(35.0, ((Number) punctRes.get(0).getMatchBreakdown().get("text")).doubleValue());
        assertNotNull(punctRes.get(0).getMatchBreakdown());
    }

    @Test
    void nativeFallback_whenMultipleCandidates_sortsDescendingByMatchScore() {
        Account owner = account(5L, "Owner", AccountRole.USER);
        Category wallet = category(1L, "Wallet");
        Category other = category(2L, "Other");
        Post source = lostPost(10L, owner, owner, wallet, "Black leather wallet", "Silver zipper card", "Hall A", baseTime);

        Post candMedium = foundPost(51L, owner, wallet, "Blue backpack", "Fabric pocket", "Dorm B", baseTime.minusDays(10));
        Post candHigh = foundPost(52L, owner, wallet, "Black leather wallet", "Silver zipper card", "Hall A", baseTime);
        Post candLow = foundPost(53L, owner, other, "Black bag", "Zipper", "Hall A", baseTime);

        List<SmartMatchItemDto> res = invokeFallbackWithMockedTimeout(source, List.of(candMedium, candLow, candHigh));

        assertEquals(3, res.size());
        assertTrue(res.get(0).getMatchScore() >= res.get(1).getMatchScore());
        assertTrue(res.get(1).getMatchScore() >= res.get(2).getMatchScore());
        assertEquals(52L, res.get(0).getId());
    }

    private List<SmartMatchItemDto> invokeFallbackWithMockedTimeout(Post source, List<Post> candidates) {
        when(postRepository.findVisibleDetail(anyLong())).thenReturn(Optional.of(source));
        List<Long> ids = candidates.stream().map(Post::getId).toList();
        when(postRepository.findCandidateIdsForMatching(anyString(), any(), anyLong(), any())).thenReturn(ids);
        when(postRepository.findVisibleByIds(ids)).thenReturn(new ArrayList<>(candidates));

        try (MockedConstruction<RestTemplate> ignored = mockConstruction(RestTemplate.class,
                (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(java.util.Map.class)))
                        .thenThrow(new ResourceAccessException("Connection refused")))) {
            return service.findSmartMatches(source.getId(), 1L, AccountRole.ADMIN);
        }
    }

    private int startAiServer(String bodyJson, AtomicReference<String> pathCapture) throws IOException {
        aiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        aiServer.createContext("/", exchange -> {
            pathCapture.set(exchange.getRequestURI().getPath());
            byte[] bytes = bodyJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        aiServer.start();
        return aiServer.getAddress().getPort();
    }

    private static Account account(long id, String fullName, AccountRole role) {
        Account acc = new Account();
        acc.setId(id);
        acc.setFullName(fullName);
        acc.setEmail("user" + id + "@fpt.edu.vn");
        acc.setRole(role);
        return acc;
    }

    private static Category category(long id, String name) {
        Category c = new Category();
        ReflectionTestUtils.setField(c, "categoryId", id);
        c.setName(name);
        return c;
    }

    private static Post lostPost(long id, Account account, Account createdBy, Category cat,
                                 String title, String desc, String loc, OffsetDateTime eventAt) {
        Post p = Post.lost(account, cat, title, desc, loc, eventAt);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "createdBy", createdBy);
        return p;
    }

    private static Post foundPost(long id, Account createdBy, Category cat,
                                  String title, String desc, String loc, OffsetDateTime eventAt) {
        Post p = Post.found(createdBy, cat, title, desc, loc, eventAt, "P2P", null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "createdBy", createdBy);
        return p;
    }
}

