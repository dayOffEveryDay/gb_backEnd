package com.costco.gb.service;

import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseChatServiceTest {
    @Mock PurchaseChatRoomRepository roomRepository;
    @Mock PurchaseChatRoomMemberRepository memberRepository;
    @Mock PurchaseChatMessageRepository messageRepository;
    @Mock PurchaseOrderRepository orderRepository;
    @Mock NotificationService notificationService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks PurchaseChatService service;

    @Test
    void memberCanSendAndMessageIsBroadcast() {
        User requester = User.builder().id(1L).displayName("委託人").build();
        User runner = User.builder().id(2L).displayName("接單人").build();
        PurchaseRequest request = PurchaseRequest.builder().id(10L).itemName("商品").build();
        PurchaseOrder order = PurchaseOrder.builder().id(20L).purchaseRequest(request)
                .requester(requester).runner(runner).status("IN_PROGRESS").build();
        PurchaseChatRoom room = PurchaseChatRoom.builder().id(30L).purchaseOrder(order).build();
        PurchaseChatRoomMember member = PurchaseChatRoomMember.builder().room(room).user(requester).build();
        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(roomRepository.findByPurchaseOrderId(20L)).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(30L, 1L)).thenReturn(Optional.of(member));
        when(messageRepository.save(any())).thenAnswer(inv -> { PurchaseChatMessage m = inv.getArgument(0); m.setId(40L); return m; });

        var response = service.sendByOrder(1L, 20L, "TEXT", "你好");

        assertEquals(40L, response.getId());
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/rooms/30"), any(Object.class));
        verify(notificationService).sendNotification(runner, "PURCHASE_CHAT_MESSAGE", 20L, "委託人 傳送了託購聊天室訊息");
    }

    @Test
    void terminalRoomAfterThreeDaysIsReadOnly() {
        User requester = User.builder().id(1L).build();
        User runner = User.builder().id(2L).build();
        PurchaseOrder order = PurchaseOrder.builder().id(20L).requester(requester).runner(runner).status("COMPLETED").build();
        order.setCompletedAt(LocalDateTime.now().minusDays(4));
        PurchaseChatRoom room = PurchaseChatRoom.builder().id(30L).purchaseOrder(order).build();
        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(roomRepository.findByPurchaseOrderId(20L)).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(30L, 1L)).thenReturn(Optional.of(
                PurchaseChatRoomMember.builder().room(room).user(requester).build()));

        assertThrows(RuntimeException.class, () -> service.sendByOrder(1L, 20L, "TEXT", "test"));
    }

    @Test
    void markRead_AdvancesOnlyToDisplayedMessage() {
        when(memberRepository.findByRoomIdAndUserId(30L, 1L))
                .thenReturn(Optional.of(new PurchaseChatRoomMember()));
        when(messageRepository.existsByIdAndRoomId(40L, 30L)).thenReturn(true);
        service.markRead(1L, 30L, 40L);
        verify(memberRepository).advanceRead(30L, 1L, 40L);
        verify(messageRepository, never()).findFirstByRoomIdOrderByIdDesc(anyLong());
    }

    @Test
    void markRead_RejectsForeignMessageOrMissingCursor() {
        when(memberRepository.findByRoomIdAndUserId(30L, 1L))
                .thenReturn(Optional.of(new PurchaseChatRoomMember()));
        assertThrows(IllegalArgumentException.class, () -> service.markRead(1L, 30L, 99L));
        assertThrows(IllegalArgumentException.class, () -> service.markRead(1L, 30L, null));
        verify(memberRepository, never()).advanceRead(anyLong(), anyLong(), anyLong());
    }

    @Test
    void markRead_RejectsNonMember() {
        assertThrows(RuntimeException.class, () -> service.markRead(1L, 30L, 40L));
        verify(memberRepository, never()).advanceRead(anyLong(), anyLong(), anyLong());
    }
}
