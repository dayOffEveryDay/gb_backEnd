package com.costco.gb.controller;

import com.costco.gb.dto.request.PurchaseChatSendRequest;
import com.costco.gb.dto.response.PurchaseChatRoomResponse;
import com.costco.gb.service.PurchaseChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-chat-rooms")
@RequiredArgsConstructor
public class PurchaseChatController {
    private final PurchaseChatService chatService;

    @GetMapping
    public ResponseEntity<List<PurchaseChatRoomResponse>> list(Authentication auth) {
        return ResponseEntity.ok(chatService.listRooms(userId(auth)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unread(Authentication auth) {
        return ResponseEntity.ok(Map.of("unreadCount", chatService.getTotalUnread(userId(auth))));
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> read(Authentication auth, @PathVariable Long roomId) {
        chatService.markRead(userId(auth), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/messages")
    public ResponseEntity<?> send(Authentication auth, @PathVariable Long roomId,
                                  @Valid @RequestBody PurchaseChatSendRequest request) {
        return ResponseEntity.ok(chatService.sendByRoom(
                userId(auth), roomId, request.getMessageType(), request.getContent()));
    }

    private Long userId(Authentication auth) { return Long.parseLong(auth.getPrincipal().toString()); }
}
