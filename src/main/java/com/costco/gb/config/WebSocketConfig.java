package com.costco.gb.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 🌟 啟動 WebSocket 與 STOMP 支援
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final ChatRoomInterceptor chatRoomInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 註冊前端連線的入口端點 (Endpoint)
        // 前端之後會用 ws://localhost:8080/ws 來建立連線
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 允許跨域連線 (CORS)
                .withSockJS(); // 啟用 SockJS 降級機制 (如果瀏覽器不支援 WebSocket，會自動切換成輪詢)
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 2. 設定訊息代理 (Message Broker) - 也就是伺服器發送訊息給前端的「廣播頻道」
        // "/topic" -> 用於群播 (例如：某個合購單的聊天室)
        // "/queue" -> 用於單播 (例如：專屬個人的滿單推播通知)
        registry.enableSimpleBroker("/topic", "/queue");

        // 3. 設定前端發送訊息給伺服器時的「前綴字」
        // 前端如果要發訊息到後端的 Controller，路徑都要加上 /app (例如: /app/chat.sendMessage)
        registry.setApplicationDestinationPrefixes("/app");

        // 4. 設定單播給特定使用者的前綴字
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 5. 將 JWT 驗證攔截器接上 inbound channel，讓 CONNECT 時能解析 Authorization
        // 5-1. 先過 authInterceptor 確保是合法登入的使用者
        // 5-2. 再過 chatRoomInterceptor 確保他有權限/房間未關閉
        registration.interceptors(webSocketAuthInterceptor,chatRoomInterceptor);
    }
}
