package com.costco.gb.controller;

import com.costco.gb.dto.response.ChatMessageResponse;
import com.costco.gb.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/chat-messages")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    // 🌟 載入歷史聊天紀錄
    @GetMapping
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable("campaignId") Long campaignId,
            @RequestAttribute("userId") Long userId) {

        List<ChatMessageResponse> history = chatService.getChatHistory(userId, campaignId);

        return ResponseEntity.ok(history);
    }
}