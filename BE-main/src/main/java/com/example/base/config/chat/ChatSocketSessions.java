package com.example.base.config.chat;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import java.time.Instant;
import java.util.concurrent.*;

@Component
public class ChatSocketSessions {
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> expirations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "chat-token-expiration"); thread.setDaemon(true); return thread;
    });
    public void add(WebSocketSession session) { sessions.put(session.getId(), session); }
    public void remove(String id) {
        sessions.remove(id);
        var task = expirations.remove(id);
        if (task != null) task.cancel(false);
    }
    public void expire(String id, Instant at) {
        var task = scheduler.schedule(() -> {
            var session = sessions.get(id);
            if (session != null) try { session.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
            remove(id);
        }, Math.max(0, at.toEpochMilli() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        var previous = expirations.put(id, task);
        if (previous != null) previous.cancel(false);
    }
    @PreDestroy void shutdown() { scheduler.shutdownNow(); }
}
