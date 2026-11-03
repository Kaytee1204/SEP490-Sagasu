package com.example.base.config.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration @EnableWebSocketMessageBroker @RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final ChatSocketAuthentication authentication;
    private final ChatSocketSessions sessions;
    @Autowired @Lazy @Qualifier("chatHeartbeatScheduler") private ThreadPoolTaskScheduler heartbeatScheduler;
    @Value("${app.chat.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") private String origins;
    @Bean public static ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("chat-heartbeat-"); return scheduler;
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat").setAllowedOrigins(origins.split(","));
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/queue").setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(heartbeatScheduler);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(authentication); }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16384).setSendBufferSizeLimit(524288).setSendTimeLimit(10000);
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.add(session); super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId()); super.afterConnectionClosed(session, status);
            }
        });
    }
}
