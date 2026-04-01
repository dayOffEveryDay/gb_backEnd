package com.costco.gb.service;

import com.costco.gb.dto.response.NotificationResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate; // 🌟 用來發送 WebSocket 訊息的工具

    @Transactional
    public void notifyCampaignFull(Campaign campaign) {
        String itemName = campaign.getItemName();
        Long campaignId = campaign.getId();

        // 1. 🔔 通知團主 (Host)
        String hostContent = "🎉 狂賀！您的合購單「" + itemName + "」已滿單，請準備安排面交！";
        sendNotification(campaign.getHost(), "CAMPAIGN_FULL", campaignId, hostContent);

        // 2. 🔔 找出所有還在車上的團員 (JOINED)，逐一發送通知
        List<Participant> participants = participantRepository.findByCampaignIdAndStatus(campaignId, "JOINED");
        String participantContent = "✨ 報喜！您參與的合購單「" + itemName + "」已經達成目標數量囉！";

        for (Participant p : participants) {
            sendNotification(p.getUser(), "CAMPAIGN_FULL", campaignId, participantContent);
        }

        log.info("合購單 {} 滿單推播發送完畢，共計通知 {} 位團員與 1 位團主", campaignId, participants.size());
    }

    // 🌟 專屬：發送合購單被取消的通知給無辜團員
    @Transactional
    public void notifyCampaignCancelled(Campaign campaign, List<Participant> participants) {
        String content = "😢 很抱歉，您參與的合購單「" + campaign.getItemName() + "」已被團主取消，系統已為您變更狀態。";

        for (Participant p : participants) {
            // 呼叫我們之前寫好的底層 private sendNotification 方法
            // 發送到 CAMPAIGN_CANCELLED 類型
            sendNotification(p.getUser(), "CAMPAIGN_CANCELLED", campaign.getId(), content);
        }
        log.info("已發送合購單取消通知給 {} 位團員", participants.size());
    }

    public void sendNotification(User user, String type, Long refId, String content) {
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


    // 🌟 1. 撈取使用者的「未讀」通知列表
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        List<Notification> unreadList = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        return unreadList.stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .type(n.getType())
                        .referenceId(n.getReferenceId())
                        .content(n.getContent())
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();
    }

    // 🌟 2. 標記為已讀
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("找不到此通知"));

        // 🛡️ 資安防護：只能已讀自己的通知！
        if (!notification.getUser().getId().equals(userId)) {
            throw new RuntimeException("無權操作此通知");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }
}