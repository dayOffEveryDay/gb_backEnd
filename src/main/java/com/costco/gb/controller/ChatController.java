package com.costco.gb.controller;

import com.costco.gb.dto.request.ChatMessageRequest;
import com.costco.gb.dto.response.ChatMessageResponse;
import com.costco.gb.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/{campaignId}/sendMessage")
    @SendTo("/topic/campaigns/{campaignId}") // 成功後自動廣播到該房間
    public ChatMessageResponse sendMessage(
            @DestinationVariable Long campaignId,
            @Payload ChatMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        // 🌟 從攔截器存入的 Session 中，安全地取出當前使用者的 ID！
        Long userId = (Long) headerAccessor.getSessionAttributes().get("userId");

        if (userId == null) {
            throw new RuntimeException("未授權的連線");
        }

        // 儲存並回傳 (如果不符合身分，Service 會拋出例外擋下)
        return chatService.saveMessage(userId, campaignId, request);
    }
}