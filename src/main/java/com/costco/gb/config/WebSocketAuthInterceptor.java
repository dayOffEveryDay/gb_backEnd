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

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService; // 假設你有這個可以用來驗證 Token 的類別

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 當前端試圖建立連線時 (CONNECT)
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 從 Header 拿出 Authorization: Bearer <token>
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                try {
                    // 解析 Token 拿到 userId
                    String userIdStr = jwtService.extractUserId(token);
                    Long userId = Long.parseLong(userIdStr);

                    // 🌟 將驗證過的 userId 存入這次的 WebSocket Session 中！
                    accessor.getSessionAttributes().put("userId", userId);
                    log.info("WebSocket 連線成功，使用者 ID: {}", userId);
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
