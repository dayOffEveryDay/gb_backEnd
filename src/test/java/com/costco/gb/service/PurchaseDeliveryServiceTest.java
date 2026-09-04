package com.costco.gb.service;

import com.costco.gb.dto.response.PurchaseRequestDeliveryDetailResponse;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.PurchaseRequestDeliveryDetail;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserDeliveryProfile;
import com.costco.gb.repository.PurchaseRequestDeliveryDetailRepository;
import com.costco.gb.repository.UserDeliveryProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseDeliveryServiceTest {

    @Mock
    private PurchaseRequestDeliveryDetailRepository detailRepository;
    @Mock
    private UserDeliveryProfileRepository profileRepository;
    @InjectMocks
    private PurchaseDeliveryService purchaseDeliveryService;

    @Test
    @DisplayName("多筆常用面交資料應依選取順序複製成託購快照")
    void replaceSnapshotsPreservesSelectedOrder() {
        User requester = User.builder().id(1L).build();
        PurchaseRequest request = request(100L, requester, "FACE_TO_FACE");
        UserDeliveryProfile first = profile(11L, requester, "FACE_TO_FACE", "台北車站");
        UserDeliveryProfile second = profile(12L, requester, "FACE_TO_FACE", "市政府站");
        // Repository 回傳順序不保證與輸入相同，服務仍須依前端選取順序建立快照。
        when(profileRepository.findAllById(List.of(11L, 12L))).thenReturn(List.of(second, first));

        purchaseDeliveryService.replaceSnapshots(request, List.of(11L, 12L, 11L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PurchaseRequestDeliveryDetail>> captor = ArgumentCaptor.forClass(List.class);
        verify(detailRepository).deleteByPurchaseRequestId(100L);
        verify(detailRepository).saveAll(captor.capture());
        assertEquals(List.of(11L, 12L), captor.getValue().stream()
                .map(detail -> detail.getSourceProfile().getId()).toList());
        assertEquals(List.of("台北車站", "市政府站"), captor.getValue().stream()
                .map(PurchaseRequestDeliveryDetail::getLocationName).toList());
    }

    @Test
    @DisplayName("不可使用其他使用者或不同交付類型的常用資料")
    void replaceSnapshotsRejectsInvalidProfile() {
        User requester = User.builder().id(1L).build();
        User anotherUser = User.builder().id(2L).build();
        PurchaseRequest request = request(100L, requester, "HOME_DELIVERY");
        UserDeliveryProfile profile = profile(11L, anotherUser, "HOME_DELIVERY", null);
        when(profileRepository.findAllById(List.of(11L))).thenReturn(List.of(profile));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> purchaseDeliveryService.replaceSnapshots(request, List.of(11L)));

        assertEquals("不可使用其他使用者的常用交付資料", exception.getMessage());
        verify(detailRepository, never()).deleteByPurchaseRequestId(100L);
        verify(detailRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("公開查詢不得回傳宅配地址、收件資料或來源 profile ID")
    void publicReadRedactsPrivateDeliveryFields() {
        User requester = User.builder().id(1L).build();
        PurchaseRequest request = request(100L, requester, "HOME_DELIVERY");
        UserDeliveryProfile source = profile(11L, requester, "HOME_DELIVERY", null);
        PurchaseRequestDeliveryDetail detail = PurchaseRequestDeliveryDetail.builder()
                .id(21L)
                .purchaseRequest(request)
                .sourceProfile(source)
                .city("台北市")
                .district("中山區")
                .locationName("家裡")
                .provider("BLACK_CAT")
                .address("中山路 1 號")
                .recipientName("王小明")
                .recipientPhone("0912345678")
                .build();
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(100L)).thenReturn(List.of(detail));

        PurchaseRequestDeliveryDetailResponse publicDetail = purchaseDeliveryService.read(request, false).get(0);
        PurchaseRequestDeliveryDetailResponse privateDetail = purchaseDeliveryService.read(request, true).get(0);

        assertEquals("台北市", publicDetail.getCity());
        assertNull(publicDetail.getSourceProfileId());
        assertNull(publicDetail.getLocationName());
        assertNull(publicDetail.getProvider());
        assertNull(publicDetail.getAddress());
        assertNull(publicDetail.getRecipientName());
        assertNull(publicDetail.getRecipientPhone());
        assertEquals(11L, privateDetail.getSourceProfileId());
        assertEquals("中山路 1 號", privateDetail.getAddress());
        assertEquals("0912345678", privateDetail.getRecipientPhone());
    }

    private PurchaseRequest request(Long id, User requester, String deliveryMethod) {
        return PurchaseRequest.builder()
                .id(id)
                .requester(requester)
                .deliveryMethod(deliveryMethod)
                .build();
    }

    private UserDeliveryProfile profile(
            Long id,
            User user,
            String profileType,
            String locationName
    ) {
        return UserDeliveryProfile.builder()
                .id(id)
                .user(user)
                .profileType(profileType)
                .city("台北市")
                .district("中正區")
                .locationName(locationName)
                .build();
    }
}
