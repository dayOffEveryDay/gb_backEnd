package com.costco.gb.config;

import com.costco.gb.service.CampaignChatAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignChatOutboundInterceptor implements ChannelInterceptor {

    private final WebSocketSessionRegistry sessionRegistry;
    private final CampaignChatAccessService campaignChatAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/campaigns/")) {
            return message;
        }

        Long userId = sessionRegistry.findUserId(accessor.getSessionId()).orElse(null);
        if (userId == null) {
            log.warn("阻擋無法識別接收者的合購聊天室訊息，sessionId={}", accessor.getSessionId());
            return null;
        }

        Long campaignId = extractCampaignId(destination);
        try {
            // 每次投遞都重新查驗，確保已退出或被踢除的既有訂閱不再收到後續訊息。
            if (campaignChatAccessService.getAccessLevel(campaignId, userId)
                    == CampaignChatAccessService.AccessLevel.NONE) {
                log.info("已阻擋聊天室 {} 向失效成員 {} 投遞訊息", campaignId, userId);
                return null;
            }
        } catch (RuntimeException exception) {
            log.warn("聊天室 {} 投遞前權限檢查失敗: {}", campaignId, exception.getMessage());
            return null;
        }

        return message;
    }

    private Long extractCampaignId(String destination) {
        try {
            String[] parts = destination.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (RuntimeException exception) {
            return -1L;
        }
    }
}
