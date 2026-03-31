package com.costco.gb.config;

import com.costco.gb.security.JwtService; // 替換成你實際解析 JWT 的 Service
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

                    // 🌟 2. 終極修復：製作一張名牌 (Principal)，並掛在連線上！
                    // 這樣 Spring 才知道這個連線的主人是誰，convertAndSendToUser 才能精準投遞！
                    Principal userPrincipal = () -> userIdStr; // 這裡的名字必須是字串型態的 userId
                    accessor.setUser(userPrincipal);

                    log.info("WebSocket 連線成功，已綁定使用者 ID: {}", userId);
                } catch (Exception e) {
                    log.error("WebSocket JWT 驗證失敗: {}", e.getMessage());
                    throw new IllegalArgumentException("無效的 Token，拒絕連線！");
                }
            } else {
                throw new IllegalArgumentException("缺少 Token，拒絕連線！");
            }
        }
        return message;
    }
}