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

/**
 * Reverse geocoding for 'Chia sẻ vị trí' (UC-38). The geocoder is a local in-process HTTP server,
 * so the real HTTP client, JSON parsing, cache and timeout are exercised without network access.
 */
class ChatLocationAddressServiceTest {

    private HttpServer server;
    private ChatLocationAddressService service;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String responseBody =
            "{\"features\":[{\"properties\":{\"name\":\"FPT University\",\"city\":\"Hà Nội\",\"country\":\"Vietnam\"}}]}";
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
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        service = new ChatLocationAddressService("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void utc098_address_validCoordinatesReturnsFormattedLabel() {
        ChatPlaceDto place = service.address(21.0133, 105.5262);
        assertEquals(new ChatPlaceDto(21.0133, 105.5262, "FPT University, Hà Nội, Vietnam"), place);
    }

    @Test
    void utc099_address_sameCoordinatesTwiceServedFromCache() {
        ChatPlaceDto first = service.address(21.0133, 105.5262);
        ChatPlaceDto second = service.address(21.0133, 105.5262);
        assertEquals(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void utc100_address_latitude95ThrowsInputInvalid() {
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(95.0, 105.0)).getErrorCode());
        assertEquals(0, calls.get());
    }

    @Test
    void utc101_address_longitudeInfinityThrowsInputInvalid() {
        assertEquals(ErrorCode.CHAT_INPUT_INVALID,
                assertThrows(AppException.class, () -> service.address(21.0, Double.POSITIVE_INFINITY)).getErrorCode());
        assertEquals(0, calls.get());
    }

    @Test
    void utc102_address_geocoderTimeoutThrowsAddressUnavailable() {
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

    @Test
    void utc103_address_noFeaturesArrayThrowsAddressUnavailable() {
        responseBody = "{}";
        assertEquals(ErrorCode.CHAT_ADDRESS_UNAVAILABLE,
                assertThrows(AppException.class, () -> service.address(10.84, 106.80)).getErrorCode());
    }

    @Test
    void utc104_address_emptyPropertiesReturnsNullLabel() {
        responseBody = "{\"features\":[{\"properties\":{}}]}";
        assertNull(service.address(21.02, 105.81).label());
    }

    @Test
    void utc105_address_labelOver200CharsTruncatedTo197PlusEllipsis() {
        responseBody = "{\"features\":[{\"properties\":{\"name\":\"" + "X".repeat(250) + "\"}}]}";
        String label = service.address(21.03, 105.82).label();
        assertEquals(198, label.length());
        assertTrue(label.endsWith("…"));
    }

    @Test
    void utc106_address_caseInsensitiveDuplicatePartRemoved() {
        responseBody = "{\"features\":[{\"properties\":{\"name\":\"Hà Nội\",\"city\":\"hà nội\"}}]}";
        assertEquals("Hà Nội", service.address(21.04, 105.83).label());
    }
}
