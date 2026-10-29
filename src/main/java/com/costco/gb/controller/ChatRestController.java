package com.costco.gb.controller;

import com.costco.gb.dto.response.ChatMessageResponse;
import com.costco.gb.dto.request.ChatReadRequest;
import jakarta.validation.Valid;
import com.costco.gb.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/chat-messages")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @PostMapping("/read")
    public ResponseEntity<Map<String, Long>> markRead(@PathVariable Long campaignId,
                                                    @Valid @RequestBody ChatReadRequest request) {
        Long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        return ResponseEntity.ok(Map.of("unreadCount",
                chatService.markRead(userId, campaignId, request.getLastReadMessageId())));
    }

    // 🌟 載入歷史聊天紀錄
    @GetMapping
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(
            @PathVariable("campaignId") Long campaignId) {

        // 從 Spring Security Context 取得目前使用者 ID
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        List<ChatMessageResponse> history = chatService.getChatHistory(userId, campaignId);

        return ResponseEntity.ok(history);
    }
}
