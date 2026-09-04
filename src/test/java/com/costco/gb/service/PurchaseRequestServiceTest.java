package com.costco.gb.service;

import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseRequestDetailResponse;
import com.costco.gb.dto.response.PurchaseRequestDeliveryDetailResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequestServiceTest {

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @Mock
    private PurchaseDeliveryService purchaseDeliveryService;
    @Mock
    private PurchaseQuoteRepository quoteRepository;
    @Mock
    private PurchaseOrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PurchaseRequestService purchaseRequestService;

    private User requester;

    @BeforeEach
    void setUp() {
        requester = User.builder()
                .id(1L)
                .displayName("委託人")
                .creditScore(100)
                .build();
    }

    @Test
    @DisplayName("建立面交託購時應從多筆常用交付資料保存快照")
    void createFaceToFaceRequestSavesSnapshot() {
        CreatePurchaseRequestRequest request = validFaceToFaceRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(invocation -> {
            PurchaseRequest entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });
        Long requestId = purchaseRequestService.create(1L, request);

        assertEquals(10L, requestId);
        ArgumentCaptor<PurchaseRequest> requestCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestRepository).save(requestCaptor.capture());
        assertEquals("FACE_TO_FACE", requestCaptor.getValue().getDeliveryMethod());
        verify(purchaseDeliveryService).replaceSnapshots(requestCaptor.getValue(), List.of(11L, 12L));
    }

    @Test
    @DisplayName("已有有效報價後不得修改期限以外的欄位")
    void updateWithActiveQuoteRejectsBusinessFieldChanges() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("OPEN", "FACE_TO_FACE");
        when(purchaseRequestRepository.findById(10L)).thenReturn(Optional.of(purchaseRequest));
        when(quoteRepository.existsByPurchaseRequestIdAndStatus(10L, "ACTIVE")).thenReturn(true);

        UpdatePurchaseRequestRequest request = new UpdatePurchaseRequestRequest();
        request.setItemName("更改後商品");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> purchaseRequestService.update(1L, 10L, request)
        );

        assertEquals("已有有效報價後只允許延長委託期限", exception.getMessage());
        verify(purchaseRequestRepository, never()).save(any(PurchaseRequest.class));
    }

    @Test
    @DisplayName("取消委託時應失效有效報價且不設定信用扣分")
    void cancelExpiresActiveQuotes() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("QUOTING", "FACE_TO_FACE");
        when(purchaseRequestRepository.findById(10L)).thenReturn(Optional.of(purchaseRequest));

        purchaseRequestService.cancel(1L, 10L);

        assertEquals("CANCELLED", purchaseRequest.getStatus());
        assertTrue(purchaseRequest.getCancelledAt() != null);
        verify(quoteRepository).expireActiveQuotes(10L);
        verify(purchaseRequestRepository).save(purchaseRequest);
    }

    @Test
    @DisplayName("完整交付快照只提供給已成立訂單的交易雙方")
    void detailOnlyShowsPrivateDeliveryToOrderParties() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("ORDERED", "HOME_DELIVERY");
        User runner = User.builder().id(2L).displayName("接單人").build();
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseRequest(purchaseRequest)
                .requester(requester)
                .runner(runner)
                .build();
        when(purchaseRequestRepository.findById(10L)).thenReturn(Optional.of(purchaseRequest));
        when(orderRepository.findFirstByPurchaseRequestIdOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.of(order));
        PurchaseRequestDeliveryDetailResponse privateDetail = PurchaseRequestDeliveryDetailResponse.builder()
                .id(21L).city("台北市").address("中山路 1 號").recipientPhone("0912345678").build();
        PurchaseRequestDeliveryDetailResponse publicDetail = PurchaseRequestDeliveryDetailResponse.builder()
                .id(21L).city("台北市").build();
        when(purchaseDeliveryService.read(purchaseRequest, true)).thenReturn(List.of(privateDetail));
        when(purchaseDeliveryService.read(purchaseRequest, false)).thenReturn(List.of(publicDetail));
        when(purchaseDeliveryService.publicSummary(any(), any())).thenReturn("台北市");

        PurchaseRequestDetailResponse runnerView = purchaseRequestService.getDetail(10L, 2L);
        PurchaseRequestDetailResponse strangerView = purchaseRequestService.getDetail(10L, 3L);

        assertEquals("中山路 1 號", runnerView.getDeliveryDetails().get(0).getAddress());
        assertEquals(null, strangerView.getDeliveryDetails().get(0).getAddress());
    }

    @Test
    @DisplayName("固定價缺貨委託重新開放時應自動補足二十四小時期限")
    void reopenUnavailableFixedRequestExtendsExpiredDeadline() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("AWAITING_REOPEN", "FACE_TO_FACE");
        purchaseRequest.setRequestExpireTime(LocalDateTime.now().minusHours(2));
        purchaseRequest.setReopenCount(0);
        purchaseRequest.setExpiredAt(LocalDateTime.now().minusHours(1));
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));
        when(purchaseDeliveryService.read(purchaseRequest, true)).thenReturn(List.of());

        LocalDateTime beforeReopen = LocalDateTime.now();
        PurchaseRequestDetailResponse response = purchaseRequestService.reopen(1L, 10L);

        assertEquals("OPEN", response.getStatus());
        assertEquals(1, response.getReopenCount());
        assertTrue(response.getRequestExpireTime().isAfter(beforeReopen.plusHours(23)));
        assertEquals(null, response.getExpiredAt());
        assertEquals("ITEM_UNAVAILABLE", purchaseRequest.getLastReopenReason());
        verify(purchaseRequestRepository).save(purchaseRequest);
    }

    @Test
    @DisplayName("非委託人不得重新開放缺貨委託")
    void reopenUnavailableFixedRequestRejectsNonOwner() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("AWAITING_REOPEN", "FACE_TO_FACE");
        when(purchaseRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(purchaseRequest));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> purchaseRequestService.reopen(2L, 10L)
        );

        assertEquals("只有委託人可以重新開放此委託單", exception.getMessage());
        verify(purchaseRequestRepository, never()).save(any(PurchaseRequest.class));
    }

    private CreatePurchaseRequestRequest validFaceToFaceRequest() {
        CreatePurchaseRequestRequest request = new CreatePurchaseRequestRequest();
        request.setItemName("Costco 商品");
        request.setRewardType("FIXED");
        request.setFixedRewardAmount(100);
        request.setDeliveryMethod("FACE_TO_FACE");
        request.setRequestExpireTime(LocalDateTime.now().plusDays(3));
        request.setDeliveryDeadlineHours(72);
        request.setSelectedProfileIds(List.of(11L, 12L));
        return request;
    }

    private PurchaseRequest basePurchaseRequest(String status, String deliveryMethod) {
        return PurchaseRequest.builder()
                .id(10L)
                .requester(requester)
                .itemName("Costco 商品")
                .rewardType("FIXED")
                .fixedRewardAmount(100)
                .deliveryMethod(deliveryMethod)
                .requestExpireTime(LocalDateTime.now().plusDays(3))
                .deliveryDeadlineHours(72)
                .minCreditScore(0)
                .status(status)
                .build();
    }
}
