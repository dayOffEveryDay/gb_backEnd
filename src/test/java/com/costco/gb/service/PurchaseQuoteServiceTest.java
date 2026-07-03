package com.costco.gb.service;

import com.costco.gb.dto.request.PurchaseQuoteRequest;
import com.costco.gb.dto.response.PurchaseOrderResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.BlockRepository;
import com.costco.gb.repository.PurchaseOrderEventRepository;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseQuoteServiceTest {

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private PurchaseQuoteRepository quoteRepository;
    @Mock
    private PurchaseOrderRepository orderRepository;
    @Mock
    private PurchaseOrderEventRepository orderEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PurchaseChatService purchaseChatService;

    @InjectMocks
    private PurchaseQuoteService purchaseQuoteService;

    private User requester;
    private User runner;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).displayName("委託人").creditScore(100).build();
        runner = User.builder().id(2L).displayName("接單人").creditScore(100).build();
    }

    @Test
    @DisplayName("提出第一筆報價時應將委託單轉為 QUOTING")
    void createQuoteChangesRequestStatus() {
        PurchaseRequest purchaseRequest = quoteRequest("OPEN");
        PurchaseQuoteRequest request = quotePayload(150);
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(runner));
        when(quoteRepository.existsByPurchaseRequestIdAndQuoterId(10L, 2L)).thenReturn(false);
        when(quoteRepository.save(any(PurchaseQuote.class))).thenAnswer(invocation -> {
            PurchaseQuote quote = invocation.getArgument(0);
            quote.setId(20L);
            return quote;
        });

        purchaseQuoteService.createQuote(2L, 10L, request);

        assertEquals("QUOTING", purchaseRequest.getStatus());
        verify(purchaseRequestRepository).findByIdForUpdate(10L);
        verify(purchaseRequestRepository).save(purchaseRequest);
        verify(notificationService).sendNotification(
                requester,
                "PURCHASE_QUOTED",
                10L,
                "接單人 已對「指定商品」提出報價"
        );
    }

    @Test
    @DisplayName("選定報價時應成立訂單並淘汰其他有效報價")
    void selectQuoteCreatesOrderAndRejectsOtherQuotes() {
        PurchaseRequest purchaseRequest = quoteRequest("QUOTING");
        PurchaseQuote selected = PurchaseQuote.builder()
                .id(20L)
                .purchaseRequest(purchaseRequest)
                .quoter(runner)
                .quoteAmount(150)
                .status("ACTIVE")
                .build();
        User otherRunner = User.builder().id(3L).displayName("其他報價者").creditScore(100).build();
        PurchaseQuote notSelected = PurchaseQuote.builder()
                .id(21L)
                .purchaseRequest(purchaseRequest)
                .quoter(otherRunner)
                .quoteAmount(160)
                .status("ACTIVE")
                .build();

        when(quoteRepository.findById(20L)).thenReturn(Optional.of(selected));
        when(quoteRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(selected));
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(quoteRepository.findByPurchaseRequestIdAndStatusAndIdNot(10L, "ACTIVE", 20L))
                .thenReturn(List.of(notSelected));
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(30L);
            return order;
        });
        when(purchaseChatService.findRoomId(30L)).thenReturn(40L);

        PurchaseOrderResponse response = purchaseQuoteService.selectQuote(1L, 20L);

        assertEquals(30L, response.getId());
        assertEquals("ORDERED", purchaseRequest.getStatus());
        assertEquals("SELECTED", selected.getStatus());
        verify(quoteRepository).markOtherActiveQuotesNotSelected(10L, 20L);
        verify(orderEventRepository).save(any());
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("報價者可以修改自己的有效報價")
    void updateOwnActiveQuote() {
        PurchaseRequest purchaseRequest = quoteRequest("QUOTING");
        PurchaseQuote quote = PurchaseQuote.builder()
                .id(20L)
                .purchaseRequest(purchaseRequest)
                .quoter(runner)
                .quoteAmount(150)
                .status("ACTIVE")
                .build();
        when(quoteRepository.findById(20L)).thenReturn(Optional.of(quote));
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(quoteRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);

        purchaseQuoteService.updateQuote(2L, 20L, quotePayload(180));

        assertEquals(180, quote.getQuoteAmount());
        assertEquals("可協助購買", quote.getQuoteNote());
    }

    @Test
    @DisplayName("最後一筆報價撤回後委託單應恢復 OPEN")
    void withdrawLastQuoteReopensRequest() {
        PurchaseRequest purchaseRequest = quoteRequest("QUOTING");
        PurchaseQuote quote = PurchaseQuote.builder()
                .id(20L)
                .purchaseRequest(purchaseRequest)
                .quoter(runner)
                .quoteAmount(150)
                .status("ACTIVE")
                .build();
        when(quoteRepository.findById(20L)).thenReturn(Optional.of(quote));
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(quoteRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(quote));
        when(quoteRepository.existsByPurchaseRequestIdAndStatus(10L, "ACTIVE")).thenReturn(false);

        purchaseQuoteService.withdrawQuote(2L, 20L);

        assertEquals("WITHDRAWN", quote.getStatus());
        assertEquals("OPEN", purchaseRequest.getStatus());
        verify(purchaseRequestRepository).save(purchaseRequest);
    }

    @Test
    @DisplayName("固定酬金接單應以鎖定查詢成立 WAITING_CONFIRMATION 訂單")
    void acceptFixedRequestCreatesOrder() {
        PurchaseRequest purchaseRequest = fixedRequest();
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(runner));
        when(orderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            order.setId(30L);
            return order;
        });
        when(purchaseChatService.findRoomId(30L)).thenReturn(40L);

        PurchaseOrderResponse response = purchaseQuoteService.acceptFixedRequest(2L, 10L);

        assertEquals("ORDERED", purchaseRequest.getStatus());
        assertEquals("WAITING_CONFIRMATION", response.getStatus());
        assertEquals(2L, response.getRunnerId());
        verify(purchaseRequestRepository).findByIdForUpdate(10L);
        verify(orderEventRepository).save(any());
    }

    @Test
    @DisplayName("雙方存在封鎖關係時不得直接承接")
    void acceptFixedRequestRejectsBlockedUsers() {
        PurchaseRequest purchaseRequest = fixedRequest();
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(userRepository.findById(2L)).thenReturn(Optional.of(runner));
        when(blockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> purchaseQuoteService.acceptFixedRequest(2L, 10L)
        );

        assertEquals("雙方存在封鎖關係，無法成立交易", exception.getMessage());
        verify(orderRepository, never()).save(any());
    }

    private PurchaseQuoteRequest quotePayload(int amount) {
        PurchaseQuoteRequest request = new PurchaseQuoteRequest();
        request.setQuoteAmount(amount);
        request.setQuoteNote("可協助購買");
        return request;
    }

    private PurchaseRequest quoteRequest(String status) {
        return PurchaseRequest.builder()
                .id(10L)
                .requester(requester)
                .itemName("指定商品")
                .rewardType("QUOTE")
                .requesterCity("台北市")
                .deliveryMethod("FACE_TO_FACE")
                .deliveryPublicSummary("台北車站")
                .requestExpireTime(LocalDateTime.now().plusDays(3))
                .deliveryDeadlineHours(72)
                .minCreditScore(80)
                .status(status)
                .build();
    }

    private PurchaseRequest fixedRequest() {
        PurchaseRequest purchaseRequest = quoteRequest("OPEN");
        purchaseRequest.setRewardType("FIXED");
        purchaseRequest.setFixedRewardAmount(100);
        return purchaseRequest;
    }
}
