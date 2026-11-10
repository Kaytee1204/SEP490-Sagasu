package com.example.base.config.chat;

import com.example.base.repository.AccountRepository;
import com.example.base.security.JwtTokenProvider;
import com.example.base.service.chat.ChatAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;
import java.security.Principal;

@Component @RequiredArgsConstructor
public class ChatSocketAuthentication implements ChannelInterceptor {
    private final JwtTokenProvider tokens;
    private final AccountRepository accounts;
    private final ChatAccessPolicy policy;
    private final ChatSocketSessions sessions;
    public record ChatPrincipal(long id) implements Principal {
        @Override public String getName() { return Long.toString(id); }
    }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new MessagingException("Invalid chat frame");
        if (headers.getCommand() == StompCommand.CONNECT) {
            if (headers.getUser() instanceof ChatPrincipal) throw new MessagingException("Chat session already authenticated");
            try {
                var claims = tokens.extractAllClaims(headers.getFirstNativeHeader("Authorization"));
                long id = Long.parseLong(claims.get("accountId").toString());
                var actor = accounts.findById(id).orElseThrow();
                if (!policy.active(actor) || claims.getExpiration() == null) throw new IllegalArgumentException();
                headers.setUser(new ChatPrincipal(id));
                headers.removeNativeHeader("Authorization");
                sessions.expire(headers.getSessionId(), claims.getExpiration().toInstant());
            } catch (RuntimeException ex) { throw new MessagingException("Chat authentication required"); }
        } else if (headers.getCommand() != StompCommand.DISCONNECT) {
            if (!(headers.getUser() instanceof ChatPrincipal)) throw new MessagingException("Chat authentication required");
            if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                if (!"/user/queue/chat-events".equals(headers.getDestination())
                        && !"/user/queue/notifications".equals(headers.getDestination())) {
                    throw new MessagingException("Subscription denied");
                }
            } else if (headers.getCommand() != null && headers.getCommand() != StompCommand.UNSUBSCRIBE) {
                throw new MessagingException("Use the authenticated REST chat API");
            }
        }
        return message;
    }
}
