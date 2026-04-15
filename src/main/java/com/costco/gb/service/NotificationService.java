package com.costco.gb.service;

import com.costco.gb.dto.response.NotificationResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable; // ✅ 正確的 Spring 分頁
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

    // 🌟 專屬：合購單結案，通知大家去互給評價！
    @Transactional
    public void notifyReviewTime(Campaign campaign) {
        String itemName = campaign.getItemName();
        Long campaignId = campaign.getId();

        // 1. 🔔 通知團主：您開的團順利結束啦！
        String hostContent = "🏆 結案恭喜！您的合購單「" + itemName + "」已全數交貨完成。快去給優秀的團員們留下評價吧！";
        sendNotification(campaign.getHost(), "CAMPAIGN_COMPLETED", campaignId, hostContent);

        // 2. 🔔 通知所有已經完成收貨的團員 (CONFIRMED)
        List<Participant> participants = participantRepository.findByCampaignIdAndStatus(campaignId, "CONFIRMED");
        String participantContent = "🛍️ 交易完成！您參與的「" + itemName + "」已順利結案，別忘了給辛苦的團主一個五星好評喔！";

        for (Participant p : participants) {
            sendNotification(p.getUser(), "CAMPAIGN_COMPLETED", campaignId, participantContent);
        }

        log.info("合購單 {} 結案推播發送完畢，共計通知 {} 位團員與 1 位團主", campaignId, participants.size());
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
    // 🌟 取得分頁後的「已讀」通知
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getReadNotifications(Long userId, Pageable pageable) {
        Page<Notification> readPage = notificationRepository
                .findByUserIdAndIsReadTrueOrderByCreatedAtDesc(userId, pageable);

        return readPage.map(n -> NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .referenceId(n.getReferenceId())
                .content(n.getContent())
                .createdAt(n.getCreatedAt())
                .build());
    }

    // 🌟 專屬：警告團員被標記棄單，提醒他可以申訴！
    @Transactional
    public void notifyUserNoShowWarning(User targetUser, Campaign campaign) {
        String content = "🚨 警告：團主已將您在「" + campaign.getItemName() + "」的狀態標記為「未現身/棄單」。若有異議，請盡速前往該合購單提出仲裁！";

        // 呼叫底層推播方法
        sendNotification(targetUser, "NO_SHOW_WARNING", campaign.getId(), content);
        log.info("已發送棄單警告通知給團員 {}", targetUser.getId());
    }

    // 🌟 專屬：通知團主有人提出仲裁，合購單凍結！
    @Transactional
    public void notifyHostAboutDispute(Campaign campaign) {
        String content = "⚖️ 仲裁通知：您的合購單「" + campaign.getItemName() + "」有團員提出異議。訂單已進入凍結狀態，請盡速前往聊天室進行協商或上傳相關證據。";

        // 直接從 campaign 抓出 Host 來通知
        sendNotification(campaign.getHost(), "DISPUTE_RAISED", campaign.getId(), content);
        log.info("已發送仲裁通知給團主 {}", campaign.getHost().getId());
    }

    // 🌟 新增：對特定合購單的聊天室廣播狀態變更事件
    public void broadcastCampaignStatus(Long campaignId, String status, String message) {
        // 依照你定義的 Payload 結構建立 Map
        Map<String, Object> payload = Map.of(
                "type", "CAMPAIGN_STATUS_CHANGED",
                "campaignId", campaignId,
                "status", status,
                "message", message
        );

        // 廣播到該合購單的專屬 Topic (所有訂閱此頻道的連線都會收到)
        String destination = "/topic/campaigns/" + campaignId;
        messagingTemplate.convertAndSend(destination, payload);

        log.info("已向聊天室 {} 廣播狀態變更: {}", campaignId, status);
    }

}