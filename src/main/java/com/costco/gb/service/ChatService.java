package com.costco.gb.service;

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
    private final ParticipantRepository participantRepository;

    @Transactional
    public ChatMessageResponse saveMessage(Long senderId, Long campaignId, ChatMessageRequest request) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單"));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        // 🛡️ 防護：確認他是否為該團成員或團主
        boolean isHost = campaign.getHost().getId().equals(senderId);
        boolean isParticipant = participantRepository.findByCampaignIdAndUserId(campaignId, senderId).isPresent();

        if (!isHost && !isParticipant) {
            throw new RuntimeException("您不是此合購單的成員，無法發言！");
        }

        ChatMessage message = ChatMessage.builder()
                .campaign(campaign)
                .sender(sender)
                .content(request.getContent())
                .messageType("TEXT")
                .build();
        chatMessageRepository.save(message);

        return ChatMessageResponse.builder()
                .senderId(sender.getId())
                .senderName(sender.getDisplayName() != null ? sender.getDisplayName() : "匿名會員")
                .content(message.getContent())
                .avatarUrl(sender.getProfileImageUrl())
                .timestamp(message.getCreatedAt())
                .build();
    }
    // ... 原本的 import 與 saveMessage 方法 ...

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long userId, Long campaignId) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到合購單"));

        // 🛡️ 資安防護：查驗身分，不是團員不准看歷史紀錄！
        boolean isHost = campaign.getHost().getId().equals(userId);
        boolean isParticipant = participantRepository.findByCampaignIdAndUserId(campaignId, userId).isPresent();

        if (!isHost && !isParticipant) {
            log.warn("資安警報：User {} 企圖偷看合購單 {} 的聊天紀錄！", userId, campaignId);
            throw new RuntimeException("您不是此合購單的成員，無法查看聊天紀錄！");
        }

        // 撈取並按時間排序 (呼叫我們之前在 Repository 寫好的語法)
        List<ChatMessage> messages = chatMessageRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId);

        // 將 Entity 轉換成前端要的 DTO 格式
        return messages.stream()
                .map(msg -> ChatMessageResponse.builder()
                        .senderId(msg.getSender().getId())
                        .senderName(msg.getSender().getDisplayName() != null ? msg.getSender().getDisplayName() : "匿名會員")
                        .avatarUrl(msg.getSender().getProfileImageUrl())
                        .content(msg.getContent())
                        .timestamp(msg.getCreatedAt())
                        .build())
                .toList();
    }
}