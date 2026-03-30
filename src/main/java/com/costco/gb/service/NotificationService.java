package com.costco.gb.service;

import com.costco.gb.dto.response.NotificationResponse;
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

        // 🌟 修正：只通知狀態是 JOINED 的團員！
        List<Participant> participants = participantRepository.findByCampaignIdAndStatus(campaign.getId(), "JOINED");
        for (Participant p : participants) {
            // ... 發送推播 ...
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