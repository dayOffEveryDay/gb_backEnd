package com.costco.gb.service;

import com.costco.gb.dto.request.CancelPurchaseOrderRequest;
import com.costco.gb.dto.request.ItemUnavailableRequest;
import com.costco.gb.dto.request.ReportPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.RespondPurchaseOrderAbnormalRequest;
import com.costco.gb.dto.request.ShipPurchaseOrderRequest;
import com.costco.gb.dto.response.PurchaseOrderDetailResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseQuote;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseOrderEventRepository;
import com.costco.gb.repository.PurchaseChatRoomRepository;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestMeetupRangeRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository orderRepository;
    @Mock
    private PurchaseOrderEventRepository orderEventRepository;
    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private PurchaseRequestMeetupRangeRepository meetupRangeRepository;
    @Mock
    private PurchaseQuoteRepository quoteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PurchaseChatRoomRepository purchaseChatRoomRepository;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private User requester;
    private User runner;
    private PurchaseRequest purchaseRequest;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(1L).displayName("委託人").creditScore(100).build();
        runner = User.builder().id(2L).displayName("接單人").creditScore(100).build();
        purchaseRequest = PurchaseRequest.builder()
                .id(10L)
                .requester(requester)
                .itemName("指定商品")
                .imageUrls(List.of("item.jpg"))
                .rewardType("FIXED")
                .fixedRewardAmount(100)
                .requesterCity("台北市")
                .deliveryMethod("FACE_TO_FACE")
                .deliveryPublicSummary("台北車站")
                .requestExpireTime(LocalDateTime.now().plusDays(3))
                .deliveryDeadlineHours(72)
                .minCreditScore(0)
                .status("ORDERED")
                .reopenCount(0)
                .build();
    }

    @Test
    @DisplayName("雙方確認後訂單應轉為 CONFIRMED")
    void bothConfirmChangesStatus() {
        PurchaseOrder order = order("WAITING_CONFIRMATION");
        order.setRequesterConfirmedAt(LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        PurchaseOrderDetailResponse response = purchaseOrderService.confirmRunner(2L, 30L);

        assertEquals("CONFIRMED", response.getStatus());
        assertNotNull(order.getRunnerConfirmedAt());
        verify(orderEventRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("固定酬金承接者接單視為已確認，發起人確認後進入 CONFIRMED")
    void fixedAcceptRequesterConfirmChangesStatus() {
        PurchaseOrder order = order("WAITING_CONFIRMATION");
        order.setRunnerConfirmedAt(LocalDateTime.now());
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        PurchaseOrderDetailResponse response = purchaseOrderService.confirmRequester(1L, 30L);

        assertEquals("CONFIRMED", response.getStatus());
        assertNotNull(order.getRequesterConfirmedAt());
        verify(orderEventRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("接單人在已確認階段取消應扣 10 分並重新開放委託")
    void runnerCancelConfirmedOrderPenalizesAndReopens() {
        PurchaseOrder order = order("CONFIRMED");
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        PurchaseOrderDetailResponse response = purchaseOrderService.cancelByRunner(
                2L, 30L, cancelRequest("臨時無法處理")
        );

        assertEquals("PENALIZED_CANCELLED", response.getStatus());
        assertEquals(10, response.getCreditPenalty());
        assertEquals(90, runner.getCreditScore());
        assertEquals("OPEN", purchaseRequest.getStatus());
        assertEquals(1, purchaseRequest.getReopenCount());
        verify(userRepository).save(runner);
        verify(purchaseRequestRepository).save(purchaseRequest);
    }

    @Test
    @DisplayName("委託人在處理中取消應扣 10 分且不重新募集")
    void requesterCancelInProgressDoesNotReopen() {
        PurchaseOrder order = order("IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        purchaseOrderService.cancelByRequester(1L, 30L, cancelRequest("臨時不需要"));

        assertEquals(90, requester.getCreditScore());
        assertEquals("CANCELLED", purchaseRequest.getStatus());
        assertEquals(0, purchaseRequest.getReopenCount());
    }

    @Test
    @DisplayName("回報缺貨不扣分且原得標報價應失效")
    void unavailableCancelsSelectedQuoteWithoutPenalty() {
        PurchaseQuote selectedQuote = PurchaseQuote.builder()
                .id(20L)
                .purchaseRequest(purchaseRequest)
                .quoter(runner)
                .quoteAmount(150)
                .status("SELECTED")
                .build();
        PurchaseOrder order = order("IN_PROGRESS");
        order.setSelectedQuote(selectedQuote);
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        ItemUnavailableRequest request = new ItemUnavailableRequest();
        request.setReason("現場缺貨");
        request.setStoreName("中和店");

        PurchaseOrderDetailResponse response = purchaseOrderService.reportUnavailable(2L, 30L, request);

        assertEquals("UNAVAILABLE_CANCELLED", response.getStatus());
        assertEquals(0, response.getCreditPenalty());
        assertEquals("UNAVAILABLE_CANCELLED", selectedQuote.getStatus());
        assertEquals("OPEN", purchaseRequest.getStatus());
        verify(quoteRepository).save(selectedQuote);
    }

    @Test
    @DisplayName("宅配訂單寄出時應保存物流資料")
    void shipHomeDeliveryStoresTrackingData() {
        purchaseRequest.setDeliveryMethod("HOME_DELIVERY");
        PurchaseOrder order = order("IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        ShipPurchaseOrderRequest request = new ShipPurchaseOrderRequest();
        request.setLogisticsProvider("黑貓宅急便");
        request.setTrackingNumber("123456789");
        request.setTrackingUrl("https://example.com/123456789");

        PurchaseOrderDetailResponse response = purchaseOrderService.ship(2L, 30L, request);

        assertEquals("SHIPPED", response.getStatus());
        assertEquals("123456789", response.getTrackingNumber());
        assertNotNull(response.getShippedAt());
    }

    @Test
    @DisplayName("面交訂單交貨後委託人可以完成")
    void faceToFaceDeliveryCanComplete() {
        PurchaseOrder order = order("IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        PurchaseOrderDetailResponse delivered = purchaseOrderService.deliver(2L, 30L);
        PurchaseOrderDetailResponse completed = purchaseOrderService.complete(1L, 30L);

        assertEquals("DELIVERED_PENDING_CONFIRM", delivered.getStatus());
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertTrue(completed.getAvailableActions().isEmpty());
    }

    @Test
    @DisplayName("交易任一方可在進行中提出異常並指定對方回應")
    void reportAbnormalTargetsCounterparty() {
        PurchaseOrder order = order("IN_PROGRESS");
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        ReportPurchaseOrderAbnormalRequest request = new ReportPurchaseOrderAbnormalRequest();
        request.setAbnormalType("RUNNER_NOT_DELIVERED");
        request.setDescription("超過約定時間仍未收到商品");
        request.setImageUrls(List.of("evidence.jpg"));

        PurchaseOrderDetailResponse response = purchaseOrderService.reportAbnormal(1L, 30L, request);

        assertEquals("ABNORMAL_PENDING_RESPONSE", response.getStatus());
        assertEquals(1L, response.getAbnormalReporterId());
        assertEquals(2L, response.getAbnormalReportedUserId());
        assertTrue(response.getAvailableActions().isEmpty());
        verify(orderEventRepository).save(any());
        verify(notificationService).sendNotification(
                runner,
                "PURCHASE_ABNORMAL_REPORTED",
                30L,
                "交易對方提出異常，請於 12 小時內回應"
        );
    }

    @Test
    @DisplayName("被提出異常者在期限內回應應不扣分結案")
    void respondAbnormalClosesWithoutPenalty() {
        PurchaseOrder order = order("ABNORMAL_PENDING_RESPONSE");
        order.setAbnormalReporter(requester);
        order.setAbnormalReportedUser(runner);
        order.setAbnormalType("RUNNER_NOT_DELIVERED");
        order.setAbnormalReportedAt(LocalDateTime.now().minusHours(1));
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        RespondPurchaseOrderAbnormalRequest request = new RespondPurchaseOrderAbnormalRequest();
        request.setResponseText("我有依約送達");

        PurchaseOrderDetailResponse response = purchaseOrderService.respondAbnormal(2L, 30L, request);

        assertEquals("DISPUTE_CLOSED_NO_PENALTY", response.getStatus());
        assertEquals(100, runner.getCreditScore());
        assertNotNull(response.getAbnormalRespondedAt());
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any());
    }

    @Test
    @DisplayName("異常超過 12 小時未回應應對被提出者扣 10 分")
    void abnormalTimeoutPenalizesReportedUser() {
        PurchaseOrder order = order("ABNORMAL_PENDING_RESPONSE");
        order.setAbnormalReporter(requester);
        order.setAbnormalReportedUser(runner);
        order.setAbnormalType("RUNNER_NOT_DELIVERED");
        order.setAbnormalReportedAt(LocalDateTime.now().minusHours(13));
        when(orderRepository.findByStatusAndAbnormalReportedAtBeforeAndAbnormalRespondedAtIsNull(
                any(), any()
        )).thenReturn(List.of(order));
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(order));

        int processed = purchaseOrderService.processAbnormalTimeouts();

        assertEquals(1, processed);
        assertEquals("PENALIZED_CANCELLED", order.getStatus());
        assertEquals(90, runner.getCreditScore());
        assertEquals(10, order.getCreditPenalty());
        assertEquals(runner, order.getBlameUser());
        verify(userRepository).save(runner);
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any());
    }

    private PurchaseOrder order(String status) {
        PurchaseOrder order = PurchaseOrder.builder()
                .id(30L)
                .purchaseRequest(purchaseRequest)
                .requester(requester)
                .runner(runner)
                .status(status)
                .creditPenalty(0)
                .build();
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private CancelPurchaseOrderRequest cancelRequest(String reason) {
        CancelPurchaseOrderRequest request = new CancelPurchaseOrderRequest();
        request.setReason(reason);
        return request;
    }
}
