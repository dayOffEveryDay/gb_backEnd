package com.costco.gb.config;

import com.costco.gb.repository.PurchaseChatRoomMemberRepository;
import com.costco.gb.service.CampaignChatAccessService;
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
public class ChatRoomInterceptor implements ChannelInterceptor {

    private final CampaignChatAccessService campaignChatAccessService;
    private final PurchaseChatRoomMemberRepository purchaseChatRoomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 只攔截「訂閱 (SUBSCRIBE)」頻道的動作
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination(); // 例如: "/topic/campaigns/105"

            if (destination != null && destination.startsWith("/topic/campaigns/")) {
                Long campaignId = extractCampaignId(destination);

                // 檢查使用者是否具有進入聊天室的權限
                Principal userPrincipal = accessor.getUser();
                if (userPrincipal == null) {
                    log.warn("event=CHAT_SUBSCRIPTION_REJECTED sessionId={} reasonCode=UNAUTHENTICATED", accessor.getSessionId());
                    throw new IllegalArgumentException("未授權的連線！");
                }

                // 從 WebSocket Principal 取得已驗證的使用者 ID
                Long currentUserId = Long.parseLong(userPrincipal.getName());

                // 即時訂閱只提供可讀寫聊天室；唯讀紀錄改由 REST 歷史訊息 API 取得。
                if (campaignChatAccessService.getAccessLevel(campaignId, currentUserId)
                        != CampaignChatAccessService.AccessLevel.READ_WRITE) {
                    log.warn("event=CAMPAIGN_CHAT_SUBSCRIPTION_REJECTED actorId={} campaignId={} reasonCode=NO_WRITE_ACCESS", currentUserId, campaignId);
                    throw new IllegalArgumentException("此聊天室目前無法訂閱。");
                }
            }
            if (destination != null && destination.startsWith("/topic/chat/rooms/")) {
                Principal principal = accessor.getUser();
                if (principal == null) {
                    log.warn("event=CHAT_SUBSCRIPTION_REJECTED sessionId={} reasonCode=UNAUTHENTICATED", accessor.getSessionId());
                    throw new IllegalArgumentException("未授權的連線");
                }
                Long roomId = extractCampaignId(destination);
                Long userId = Long.parseLong(principal.getName());
                if (purchaseChatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId).isEmpty()) {
                    log.warn("event=PURCHASE_CHAT_SUBSCRIPTION_REJECTED actorId={} roomId={} reasonCode=NOT_MEMBER", userId, roomId);
                    throw new IllegalArgumentException("您不是此託購聊天室成員");
                }
            }
        }
        return message;
    }

    // 小工具：把 "/topic/campaigns/105" 切割出 105
    private Long extractCampaignId(String destination) {
        try {
            String[] parts = destination.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return -1L;
        }
    }
}
