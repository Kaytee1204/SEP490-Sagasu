package com.example.base.service.chat;

import com.example.base.dto.chat.ChatPlaceDto;
import com.example.base.exception.AppException;
import com.example.base.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLocationAddressServiceTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"features\":[{\"properties\":{\"name\":\"FPT University\",\"locality\":\"Hoa Lac\",\"city\":\"Hanoi\"}}]}";
    private volatile CountDownLatch blockLatch;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reverse", exchange -> {
            calls.incrementAndGet();
            CountDownLatch latch = blockLatch;
            if (latch != null) {
                try {
                    latch.await(6, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // UTC092, UTC093 - address reverse-geocodes valid coordinates and caches repeated lookups
    @Test
    void address_whenValidCoordinates_resolvesAndCachesAddress() {
        ChatLocationAddressService service = new ChatLocationAddressService(baseUrl);

        // UTC092 - reverse geocodes coordinates to formatted ChatPlaceDto label
        ChatPlaceDto first = service.address(21.0124123, 105.5252891);
        // UTC093 - serves repeated identical lookup from cache (1 HTTP call total)
        ChatPlaceDto second = service.address(21.0124123, 105.5252891);

        assertEquals("FPT University, Hoa Lac, Hanoi", first.label());
        assertEquals(21.0124123, first.latitude());
        assertEquals(105.5252891, first.longitude());
        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    // UTC094 - address throws CHAT_INPUT_INVALID when lat/lon out of range or NaN
    @Test
    void address_whenCoordinatesOutOfRange_throwsChatInputInvalid() {
        ChatLocationAddressService service = new ChatLocationAddressService(baseUrl);

        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(91.0, 105.0)).getErrorCode());
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(21.0, 181.0)).getErrorCode());
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(Double.NaN, 105.0)).getErrorCode());
        assertEquals(0, calls.get());
    }

    // UTC097 - address throws CHAT_ADDRESS_UNAVAILABLE when geocoder omits features array (plus upstream 500)
    @Test
    void address_whenUpstreamReturnsErrorOrMissingFeatures_throwsChatAddressUnavailable() {
        ChatLocationAddressService service = new ChatLocationAddressService(baseUrl);

        // Supplementary (ChatLocationAddressService.address): upstream HTTP 500 throws CHAT_ADDRESS_UNAVAILABLE
        responseStatus = 500;
        assertEquals(ErrorCode.CHAT_ADDRESS_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.address(21.01, 105.52)).getErrorCode());

        // UTC097 - response JSON without features throws CHAT_ADDRESS_UNAVAILABLE
        responseStatus = 200;
        responseBody = "{}";
        assertEquals(ErrorCode.CHAT_ADDRESS_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.address(10.84, 106.80)).getErrorCode());
    }

    // UTC095, UTC096 - address validates infinite longitude and upstream timeouts
    @Test
    void address_whenLonInfiniteOrUpstreamTimesOut_throwsExpectedError() {
        ChatLocationAddressService service = new ChatLocationAddressService(baseUrl);
        // UTC095 - lon=Infinity throws CHAT_INPUT_INVALID
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(21.0, Double.POSITIVE_INFINITY))
                        .getErrorCode());

        // UTC096 - geocoder timeout throws CHAT_ADDRESS_UNAVAILABLE
        CountDownLatch latch = new CountDownLatch(1);
        blockLatch = latch;
        try {
            assertEquals(ErrorCode.CHAT_ADDRESS_UNAVAILABLE,
                    assertThrows(AppException.class, () -> service.address(21.05, 105.55)).getErrorCode());
        } finally {
            blockLatch = null;
            latch.countDown();
        }
    }

    // UTC098, UTC099, UTC100 - address formats empty, oversized, and duplicate property labels
    @Test
    void address_whenPropertiesEmptyLongOrContainCaseDuplicate_formatsLabelCorrectly() {
        ChatLocationAddressService service = new ChatLocationAddressService(baseUrl);

        // UTC098 - empty properties produce label=null
        responseBody = "{\"features\":[{\"properties\":{}}]}";
        assertNull(service.address(21.02, 105.81).label());

        // UTC099 - label > 200 chars truncated to 197 chars + ellipsis
        responseBody = "{\"features\":[{\"properties\":{\"name\":\"" + "X".repeat(250) + "\"}}]}";
        ChatPlaceDto truncated = service.address(21.03, 105.82);
        assertEquals(198, truncated.label().length());
        assertTrue(truncated.label().endsWith("…"));

        // UTC100 - case-insensitive duplicate parts removed
        responseBody = "{\"features\":[{\"properties\":{\"name\":\"Hà Nội\",\"city\":\"hà nội\"}}]}";
        assertEquals("Hà Nội", service.address(21.04, 105.83).label());
    }
}

