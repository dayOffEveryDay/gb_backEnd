package com.costco.gb.config;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentMap<String, Long> userIdsBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, Long userId) {
        if (sessionId != null && userId != null) {
            userIdsBySessionId.put(sessionId, userId);
        }
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            userIdsBySessionId.remove(sessionId);
        }
    }

    public Optional<Long> findUserId(String sessionId) {
        return Optional.ofNullable(userIdsBySessionId.get(sessionId));
    }
}
