package com.conekt.chat_App.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketPresenceTracker {
    private final SimpMessagingTemplate messagingTemplate;
    // Maps WebSocket sessionId -> userId
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    public WebSocketPresenceTracker(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String userId = accessor.getFirstNativeHeader("userId");

        if (sessionId != null && userId != null) {
            sessionUserMap.put(sessionId, userId);
            broadcastOnlineUsers();
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null && sessionUserMap.remove(sessionId) != null) {
            broadcastOnlineUsers();
        }
    }

    public boolean isUserOnline(String userId) {
        return sessionUserMap.containsValue(userId);
    }

    public Set<String> getOnlineUsers() {
        return Set.copyOf(sessionUserMap.values());
    }

    private void broadcastOnlineUsers() {
        messagingTemplate.convertAndSend("/topic/presence", getOnlineUsers());
    }
}
