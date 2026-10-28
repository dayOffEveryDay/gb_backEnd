package com.costco.gb.config;

import com.costco.gb.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                try {
                    if (!jwtService.isTokenValid(token)) {
                        throw new IllegalArgumentException("Token 已過期或不合法！");
                    }

                    String userIdStr = jwtService.extractUserId(token);
                    Long userId = Long.parseLong(userIdStr);

                    // 1. 存入口袋 (聊天室抓取歷史紀錄可能還是會用到)
                    accessor.getSessionAttributes().put("userId", userId);

                    // 2. 將已驗證的使用者 ID 設為 WebSocket 連線 Principal
                    // 這樣 Spring 才知道這個連線的主人是誰，convertAndSendToUser 才能精準投遞！
                    Principal userPrincipal = () -> userIdStr; // 這裡的名字必須是字串型態的 userId
                    accessor.setUser(userPrincipal);
                    sessionRegistry.register(accessor.getSessionId(), userId);

                    log.debug("event=WEBSOCKET_CONNECTED actorId={} sessionId={}", userId, accessor.getSessionId());
                } catch (Exception e) {
                    log.warn("event=WEBSOCKET_AUTH_REJECTED sessionId={} reasonCode=INVALID_TOKEN errorType={}", accessor.getSessionId(), e.getClass().getSimpleName());
                    throw new IllegalArgumentException("無效的 Token，拒絕連線！");
                }
            } else {
                log.warn("event=WEBSOCKET_AUTH_REJECTED sessionId={} reasonCode=MISSING_TOKEN", accessor.getSessionId());
                throw new IllegalArgumentException("缺少 Token，拒絕連線！");
            }
        }
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            sessionRegistry.remove(accessor.getSessionId());
            log.debug("event=WEBSOCKET_DISCONNECTED sessionId={}", accessor.getSessionId());
        }
        return message;
    }
}
