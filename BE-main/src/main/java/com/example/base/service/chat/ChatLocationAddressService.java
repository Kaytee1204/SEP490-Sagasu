package com.example.base.service.chat;

import com.example.base.dto.chat.ChatPlaceDto;
import com.example.base.exception.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ChatLocationAddressService {
    private final RestClient client;
    private final String providerUrl;
    private final ReentrantLock upstream = new ReentrantLock();
    private final Map<String, Cached> cache = new LinkedHashMap<>(256, .75f, true);
    private long nextRequest;
    private record Cached(String label, long expiresAt) {}

    public ChatLocationAddressService(@Value("${app.chat.geocoder-url:https://photon.komoot.io}") String providerUrl) {
        this.providerUrl = providerUrl.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Sagasu-P2P-Chat/1.0 (+https://github.com/Kaytee1204/SEP490-Sagasu)").build();
    }

    public ChatPlaceDto address(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180)
            throw new AppException(ErrorCode.CHAT_INPUT_INVALID);
        boolean acquired = false;
        try {
            acquired = upstream.tryLock(2, TimeUnit.SECONDS);
            if (!acquired) throw new AppException(ErrorCode.CHAT_ADDRESS_UNAVAILABLE);
            String key = String.format(Locale.ROOT, "%.5f:%.5f", latitude, longitude);
            var previous = cache.get(key);
            if (previous != null && previous.expiresAt() > System.currentTimeMillis())
                return new ChatPlaceDto(latitude, longitude, previous.label());
            long wait = nextRequest - System.nanoTime();
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
            nextRequest = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            var uri = UriComponentsBuilder.fromHttpUrl(providerUrl).path("/reverse")
                    .queryParam("lat", Math.round(latitude*100000d)/100000d)
                    .queryParam("lon", Math.round(longitude*100000d)/100000d).queryParam("limit", 1).build().encode().toUri();
            JsonNode response = client.get().uri(uri).retrieve().body(JsonNode.class);
            if (response == null || !response.path("features").isArray()) throw new IllegalStateException();
            String label = label(response.path("features").path(0).path("properties"));
            cache.put(key, new Cached(label, System.currentTimeMillis()+3_600_000));
            while (cache.size()>256) cache.remove(cache.keySet().iterator().next());
            // Return the original coordinates: reverse geocoding must not move the pin.
            return new ChatPlaceDto(latitude, longitude, label);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new AppException(ErrorCode.CHAT_ADDRESS_UNAVAILABLE);
        } catch (AppException ex) { throw ex; }
        catch (RuntimeException ex) { throw new AppException(ErrorCode.CHAT_ADDRESS_UNAVAILABLE); }
        finally { if (acquired) upstream.unlock(); }
    }
    private String label(JsonNode properties) {
        List<String> parts = new ArrayList<>();
        add(parts, properties.path("name").asText(""));
        add(parts, (properties.path("housenumber").asText("")+" "+properties.path("street").asText("")).strip());
        for (String field : List.of("district","locality","city","county","state","country")) add(parts, properties.path(field).asText(""));
        if (parts.isEmpty()) return null;
        String label = String.join(", ", parts);
        return label.length()>200 ? label.substring(0,197)+"…" : label;
    }
    private void add(List<String> parts, String text) {
        if (!text.isBlank() && parts.stream().noneMatch(part -> part.equalsIgnoreCase(text.strip()))) parts.add(text.strip());
    }
}
