package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.dto.request.ChatMessageRequest;
import com.costco.gb.dto.response.ChatMessageResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final CampaignChatAccessService chatAccessService;
    private final ChatReadStateRepository chatReadStateRepository;

    @Transactional
    public long markRead(Long userId, Long campaignId, Long messageId) {
        chatAccessService.requireReadable(campaignId, userId);
        if (messageId == null || messageId <= 0
                || !chatMessageRepository.existsByIdAndCampaignId(messageId, campaignId)) {
            throw new IllegalArgumentException("已讀訊息不存在或不屬於此聊天室");
        }
        chatReadStateRepository.advance(campaignId, userId, messageId);
        return chatMessageRepository.countUnread(campaignId, userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId, Campaign campaign) {
        if (chatAccessService.getAccessLevel(campaign, userId) == CampaignChatAccessService.AccessLevel.NONE) {
            return 0;
        }
        return chatMessageRepository.countUnread(campaign.getId(), userId);
    }

    @Transactional
    public ChatMessageResponse saveMessage(Long senderId, Long campaignId, ChatMessageRequest request) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單"));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        // 每次送出訊息都重新確認成員與團購狀態，避免退出、被踢或封存後繼續發言。
        chatAccessService.requireWritable(campaign, senderId);

        ChatMessage message = ChatMessage.builder()
                .campaign(campaign)
                .sender(sender)
                .content(request.getContent())
                .messageType("TEXT")
                .build();
        try {
            chatMessageRepository.save(message);
        } catch (RuntimeException e) {
            log.error("event=CAMPAIGN_CHAT_PERSIST_FAILED actorId={} campaignId={} errorType={}", senderId, campaignId, e.getClass().getSimpleName());
            throw e;
        }
        BusinessLog.debug(log, "event=CAMPAIGN_CHAT_MESSAGE_SAVED actorId={} campaignId={} messageId={} messageType=TEXT", senderId, campaignId, message.getId());

        return ChatMessageResponse.builder()
                .messageId(message.getId())
                .senderId(sender.getId())
                .senderName(sender.getDisplayName() != null ? sender.getDisplayName() : "匿名會員")
                .content(message.getContent())
                .avatarUrl(sender.getProfileImageUrl())
                .timestamp(message.getCreatedAt())
                .build();
    }
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long userId, Long campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單"));

        // 歷史訊息允許有效成員與唯讀成員查看，主動退出或被踢除者不得存取。
        chatAccessService.requireReadable(campaign, userId);

        // 依建立時間查詢並排序聊天紀錄
        List<ChatMessage> messages = chatMessageRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);

        // 將 Entity 轉換成前端要的 DTO 格式
        return messages.stream()
                .map(msg -> ChatMessageResponse.builder()
                        .messageId(msg.getId())
                        .senderId(msg.getSender().getId())
                        .senderName(msg.getSender().getDisplayName() != null ? msg.getSender().getDisplayName() : "匿名會員")
                        .avatarUrl(msg.getSender().getProfileImageUrl())
                        .content(msg.getContent())
                        .timestamp(msg.getCreatedAt())
                        .build())
                .toList();
    }
}
