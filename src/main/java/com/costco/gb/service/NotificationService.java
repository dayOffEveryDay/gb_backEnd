package com.costco.gb.service;

import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate; // 🌟 用來發送 WebSocket 訊息的工具

    @Transactional
    public void notifyCampaignFull(Campaign campaign) {
        // 1. 通知團主
        sendNotification(campaign.getHost(), "CAMPAIGN_FULL", campaign.getId(),
                "🎉 狂賀！您的合購單「" + campaign.getItemName() + "」已滿單，請準備安排面交！");

        // 2. 通知所有團員
        List<Participant> participants = participantRepository.findByCampaignId(campaign.getId());
        for (Participant p : participants) {
            sendNotification(p.getUser(), "CAMPAIGN_FULL", campaign.getId(),
                    "🔔 您參與的合購單「" + campaign.getItemName() + "」已成團！請隨時留意聊天室訊息。");
        }
    }

    private void sendNotification(User user, String type, Long refId, String content) {
        // 存入資料庫
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .referenceId(refId)
                .content(content)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        // 🌟 透過 WebSocket 即時推播給該使用者的小鈴鐺
        // 這會發送到 /user/{userId}/queue/notifications 頻道
        messagingTemplate.convertAndSendToUser(
                user.getId().toString(),
                "/queue/notifications",
                Map.of("content", content, "type", type, "referenceId", refId)
        );
    }
}