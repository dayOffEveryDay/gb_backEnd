package com.costco.gb.service;

import com.costco.gb.dto.request.CreatePurchaseRequestRequest;
import com.costco.gb.dto.request.MeetupRangeRequest;
import com.costco.gb.dto.request.UpdatePurchaseRequestRequest;
import com.costco.gb.dto.response.PurchaseRequestDetailResponse;
import com.costco.gb.entity.PurchaseOrder;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestMeetupRange;
import com.costco.gb.entity.User;
import com.costco.gb.repository.PurchaseOrderRepository;
import com.costco.gb.repository.PurchaseQuoteRepository;
import com.costco.gb.repository.PurchaseRequestMeetupRangeRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import com.costco.gb.repository.UserDeliveryProfileRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private PurchaseRequestMeetupRangeRepository meetupRangeRepository;
    @Mock
    private UserDeliveryProfileRepository profileRepository;
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
    @DisplayName("建立面交委託時應保存快照並忽略私密交付資料")
    void createFaceToFaceRequestSavesSnapshot() {
        CreatePurchaseRequestRequest request = validFaceToFaceRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(invocation -> {
            PurchaseRequest entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });
        when(profileRepository.findAllById(List.of())).thenReturn(List.of());

        Long requestId = purchaseRequestService.create(1L, request);

        assertEquals(10L, requestId);
        ArgumentCaptor<PurchaseRequest> requestCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestRepository).save(requestCaptor.capture());
        assertNull(requestCaptor.getValue().getDeliveryPrivateDetail());

        ArgumentCaptor<List<PurchaseRequestMeetupRange>> rangesCaptor = ArgumentCaptor.forClass(List.class);
        verify(meetupRangeRepository).saveAll(rangesCaptor.capture());
        assertEquals("台北車站", rangesCaptor.getValue().get(0).getDisplayText());
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
    @DisplayName("私密交付資料只提供給已成立訂單的交易雙方")
    void detailOnlyShowsPrivateDeliveryToOrderParties() {
        PurchaseRequest purchaseRequest = basePurchaseRequest("ORDERED", "HOME_DELIVERY");
        purchaseRequest.setDeliveryPrivateDetail(Map.of("addressDetail", "中山路 1 號"));
        User runner = User.builder().id(2L).displayName("接單人").build();
        PurchaseOrder order = PurchaseOrder.builder()
                .purchaseRequest(purchaseRequest)
                .requester(requester)
                .runner(runner)
                .build();
        when(purchaseRequestRepository.findById(10L)).thenReturn(Optional.of(purchaseRequest));
        when(orderRepository.findFirstByPurchaseRequestIdOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.of(order));

        PurchaseRequestDetailResponse runnerView = purchaseRequestService.getDetail(10L, 2L);
        PurchaseRequestDetailResponse strangerView = purchaseRequestService.getDetail(10L, 3L);

        assertEquals("中山路 1 號", runnerView.getDeliveryPrivateDetail().get("addressDetail"));
        assertNull(strangerView.getDeliveryPrivateDetail());
    }

    private CreatePurchaseRequestRequest validFaceToFaceRequest() {
        MeetupRangeRequest range = new MeetupRangeRequest();
        range.setLocationType("LANDMARK");
        range.setCity("台北市");
        range.setDisplayText("台北車站");

        CreatePurchaseRequestRequest request = new CreatePurchaseRequestRequest();
        request.setItemName("Costco 商品");
        request.setRewardType("FIXED");
        request.setFixedRewardAmount(100);
        request.setRequesterCity("台北市");
        request.setDeliveryMethod("FACE_TO_FACE");
        request.setDeliveryPublicSummary("台北車站");
        request.setDeliveryPrivateDetail(Map.of("shouldNot", "persist"));
        request.setRequestExpireTime(LocalDateTime.now().plusDays(3));
        request.setDeliveryDeadlineHours(72);
        request.setManualMeetupRanges(List.of(range));
        return request;
    }

    private PurchaseRequest basePurchaseRequest(String status, String deliveryMethod) {
        return PurchaseRequest.builder()
                .id(10L)
                .requester(requester)
                .itemName("Costco 商品")
                .rewardType("FIXED")
                .fixedRewardAmount(100)
                .requesterCity("台北市")
                .deliveryMethod(deliveryMethod)
                .deliveryPublicSummary("台北市")
                .requestExpireTime(LocalDateTime.now().plusDays(3))
                .deliveryDeadlineHours(72)
                .minCreditScore(0)
                .status(status)
                .build();
    }
}
