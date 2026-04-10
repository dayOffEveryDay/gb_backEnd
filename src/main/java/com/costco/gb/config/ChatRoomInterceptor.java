package com.costco.gb.config;

import com.costco.gb.entity.Campaign;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ParticipantRepository; // 🌟 1. 新增匯入
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
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomInterceptor implements ChannelInterceptor {

    private final CampaignRepository campaignRepository;
    private final ParticipantRepository participantRepository; // 🌟 2. 注入 ParticipantRepository

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 只攔截「訂閱 (SUBSCRIBE)」頻道的動作
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination(); // 例如: "/topic/campaigns/105"

            if (destination != null && destination.startsWith("/topic/campaigns/")) {
                Long campaignId = extractCampaignId(destination);

                Campaign campaign = campaignRepository.findById(campaignId).orElse(null);

                // 🛡️ 防護一：檢查房間狀態
                if (campaign == null || "CANCELLED".equals(campaign.getStatus()) ) {
                    log.warn("拒絕連線！聊天室 {} 已關閉或不存在。", campaignId);
                    throw new IllegalArgumentException("此聊天室已關閉，拒絕連線！");
                }
                // 在 ChatRoomInterceptor 的 preSend 邏輯中
                if ("COMPLETED".equals(campaign.getStatus())) {
                    // 假設我們允許結案後 3 天內仍可連線
                    LocalDateTime gracePeriodEnd = campaign.getCompletedAt().plusDays(3);

                    if (LocalDateTime.now().isAfter(gracePeriodEnd)) {
                        log.warn("連線拒絕：合購單 {} 已結案超過 3 天緩衝期", campaignId);
                        throw new IllegalArgumentException("聊天室已過期封存。");
                    }
                    // 如果在 3 天內，則放行 (allow subscription)
                }

                // 🌟 防護二：檢查使用者身分與權限 (核心改動)
                Principal userPrincipal = accessor.getUser();
                if (userPrincipal == null) {
                    log.warn("拒絕連線！找不到 WebSocket 使用者身分。");
                    throw new IllegalArgumentException("未授權的連線！");
                }

                // 從 Principal 拿出我們在 AuthInterceptor 塞進去的 userId
                Long currentUserId = Long.parseLong(userPrincipal.getName());

                // 判斷 1：他是團主嗎？
                boolean isHost = campaign.getHost().getId().equals(currentUserId);

                // 判斷 2：他是有效的團員嗎？
                // (細節：我們只允許還在車上的狀態進入，如果他已經退團/被踢，就不給進)
                boolean isParticipant = participantRepository.existsByCampaignIdAndUserIdAndStatusIn(
                        campaignId,
                        currentUserId,
                        List.of("JOINED", "CONFIRMED", "DISPUTED", "NO_SHOW")
                );

                // 💥 終極判決：既不是團主，也不是有效團員，直接踢掉！
                if (!isHost && !isParticipant) {
                    log.warn("安全攔截！使用者 {} 嘗試偷看合購單 {} 的聊天室被拒絕", currentUserId, campaignId);
                    throw new IllegalArgumentException("您不是此合購單的成員，無法進入聊天室！");
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