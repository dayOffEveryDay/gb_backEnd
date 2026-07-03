package com.costco.gb.service;

import com.costco.gb.dto.response.PurchaseChatMessageResponse;
import com.costco.gb.dto.response.PurchaseChatRoomResponse;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseChatService {
    private static final Set<String> TERMINAL = Set.of(
            "COMPLETED", "CANCELLED", "UNAVAILABLE_CANCELLED",
            "PENALIZED_CANCELLED", "DISPUTE_CLOSED_NO_PENALTY"
    );

    private final PurchaseChatRoomRepository roomRepository;
    private final PurchaseChatRoomMemberRepository memberRepository;
    private final PurchaseChatMessageRepository messageRepository;
    private final PurchaseOrderRepository orderRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public PurchaseChatRoom createForOrder(PurchaseOrder order) {
        return roomRepository.findByPurchaseOrderId(order.getId()).orElseGet(() -> {
            PurchaseChatRoom room = roomRepository.save(PurchaseChatRoom.builder()
                    .purchaseOrder(order).status("ACTIVE").build());
            memberRepository.save(PurchaseChatRoomMember.builder().room(room).user(order.getRequester()).build());
            memberRepository.save(PurchaseChatRoomMember.builder().room(room).user(order.getRunner()).build());
            return room;
        });
    }

    @Transactional(readOnly = true)
    public Long findRoomId(Long orderId) {
        return roomRepository.findByPurchaseOrderId(orderId).map(PurchaseChatRoom::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PurchaseChatRoomResponse> listRooms(Long userId) {
        return memberRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(member -> toRoomResponse(member.getRoom(), userId, member.getLastReadMessageId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long getTotalUnread(Long userId) {
        return memberRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .mapToLong(member -> unread(member.getRoom().getId(), userId, member.getLastReadMessageId()))
                .sum();
    }

    @Transactional(readOnly = true)
    public List<PurchaseChatMessageResponse> getMessagesByOrder(Long userId, Long orderId) {
        PurchaseChatRoom room = getRoomByOrder(orderId);
        requireMember(room.getId(), userId);
        return messageRepository.findByRoomIdOrderByCreatedAtAsc(room.getId()).stream()
                .map(this::toMessageResponse).toList();
    }

    @Transactional
    public PurchaseChatMessageResponse sendByOrder(Long userId, Long orderId, String type, String content) {
        PurchaseChatRoom room = getRoomByOrder(orderId);
        PurchaseChatRoomMember member = requireMember(room.getId(), userId);
        if (isReadOnly(room.getPurchaseOrder())) throw new RuntimeException("聊天室已轉為唯讀");
        if (content == null || content.isBlank()) throw new RuntimeException("訊息內容不可空白");
        String normalizedType = type == null ? "TEXT" : type.trim().toUpperCase();
        if (!Set.of("TEXT", "IMAGE").contains(normalizedType)) throw new RuntimeException("不支援的訊息類型");

        PurchaseChatMessage saved = messageRepository.save(PurchaseChatMessage.builder()
                .room(room).sender(member.getUser()).messageType(normalizedType).content(content.trim()).build());
        room.setUpdatedAt(LocalDateTime.now());
        roomRepository.save(room);
        member.setLastReadMessageId(saved.getId());
        member.setLastReadAt(LocalDateTime.now());
        memberRepository.save(member);
        PurchaseChatMessageResponse response = toMessageResponse(saved);
        messagingTemplate.convertAndSend("/topic/chat/rooms/" + room.getId(), response);

        User counterpart = counterpart(room.getPurchaseOrder(), userId);
        notificationService.sendNotification(counterpart, "PURCHASE_CHAT_MESSAGE", orderId,
                member.getUser().getDisplayName() + " 傳送了託購聊天室訊息");
        return response;
    }

    @Transactional
    public PurchaseChatMessageResponse sendByRoom(Long userId, Long roomId, String type, String content) {
        PurchaseChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("找不到託購聊天室"));
        return sendByOrder(userId, room.getPurchaseOrder().getId(), type, content);
    }

    @Transactional
    public void markRead(Long userId, Long roomId) {
        PurchaseChatRoomMember member = requireMember(roomId, userId);
        Long lastId = messageRepository.findFirstByRoomIdOrderByIdDesc(roomId)
                .map(PurchaseChatMessage::getId).orElse(0L);
        member.setLastReadMessageId(lastId);
        member.setLastReadAt(LocalDateTime.now());
        memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public boolean canAccessRoom(Long roomId, Long userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId).isPresent();
    }

    private PurchaseChatRoom getRoomByOrder(Long orderId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購訂單"));
        return roomRepository.findByPurchaseOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("找不到託購聊天室"));
    }

    private PurchaseChatRoomMember requireMember(Long roomId, Long userId) {
        return memberRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("您不是此託購聊天室成員"));
    }

    private boolean isReadOnly(PurchaseOrder order) {
        if (!TERMINAL.contains(order.getStatus())) return false;
        LocalDateTime terminalAt = order.getCompletedAt() != null ? order.getCompletedAt() : order.getCancelledAt();
        if (terminalAt == null) terminalAt = order.getUpdatedAt();
        return terminalAt != null && !LocalDateTime.now().isBefore(terminalAt.plusDays(3));
    }

    private long unread(Long roomId, Long userId, Long lastReadId) {
        return messageRepository.countByRoomIdAndIdGreaterThanAndSenderIdNot(
                roomId, lastReadId == null ? 0L : lastReadId, userId);
    }

    private PurchaseChatRoomResponse toRoomResponse(PurchaseChatRoom room, Long userId, Long lastReadId) {
        PurchaseOrder order = room.getPurchaseOrder();
        return PurchaseChatRoomResponse.builder()
                .id(room.getId()).orderId(order.getId()).itemName(order.getPurchaseRequest().getItemName())
                .counterpartId(counterpart(order, userId).getId())
                .counterpartName(counterpart(order, userId).getDisplayName())
                .counterpartAvatarUrl(counterpart(order, userId).getProfileImageUrl())
                .orderStatus(order.getStatus()).readOnly(isReadOnly(order))
                .unreadCount(unread(room.getId(), userId, lastReadId))
                .lastMessage(messageRepository.findFirstByRoomIdOrderByIdDesc(room.getId())
                        .map(this::toMessageResponse).orElse(null))
                .updatedAt(room.getUpdatedAt()).build();
    }

    private User counterpart(PurchaseOrder order, Long userId) {
        return order.getRequester().getId().equals(userId) ? order.getRunner() : order.getRequester();
    }

    private PurchaseChatMessageResponse toMessageResponse(PurchaseChatMessage message) {
        return PurchaseChatMessageResponse.builder()
                .id(message.getId()).chatRoomId(message.getRoom().getId())
                .senderId(message.getSender().getId()).senderName(message.getSender().getDisplayName())
                .senderAvatarUrl(message.getSender().getProfileImageUrl())
                .messageType(message.getMessageType()).content(message.getContent())
                .createdAt(message.getCreatedAt()).build();
    }
}
