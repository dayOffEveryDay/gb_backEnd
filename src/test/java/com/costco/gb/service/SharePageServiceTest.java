package com.costco.gb.service;

import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.PurchaseRequest;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharePageServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;
    @InjectMocks
    private SharePageService sharePageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sharePageService, "backendBaseUrl", "https://api.example.com/");
        ReflectionTestUtils.setField(sharePageService, "frontendBaseUrl", "https://app.example.com/");
        ReflectionTestUtils.setField(sharePageService, "campaignDetailPath", "/campaigns/{id}");
        ReflectionTestUtils.setField(sharePageService, "purchaseRequestDetailPath", "/purchase-requests/{id}");
    }

    @Test
    @DisplayName("團購分享頁包含 Open Graph 商品資訊並跳脫使用者內容")
    void campaignPageContainsOpenGraphMetadata() {
        User host = User.builder().displayName("小明 & 小華").build();
        Campaign campaign = Campaign.builder()
                .id(10L)
                .host(host)
                .itemName("堅果 <限量>")
                .imageUrls("abc.jpg,def.jpg")
                .pricePerUnit(399)
                .meetupLocation("台北車站")
                .status("OPEN")
                .build();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));

        String html = sharePageService.renderCampaignPage(10L);

        assertTrue(html.contains("property=\"og:title\""));
        assertTrue(html.contains("https://api.example.com/images/abc.jpg"));
        assertTrue(html.contains("https://app.example.com/campaigns/10"));
        assertTrue(html.contains("堅果 &lt;限量&gt;"));
        assertTrue(html.contains("小明 &amp; 小華"));
        assertFalse(html.contains("堅果 <限量>"));
    }

    @Test
    @DisplayName("託購分享頁不會揭露私密交付資料")
    void purchaseRequestPageOnlyContainsPublicDeliverySummary() {
        User requester = User.builder().displayName("阿美").build();
        PurchaseRequest request = PurchaseRequest.builder()
                .id(20L)
                .requester(requester)
                .itemName("咖啡豆")
                .imageUrls(List.of("purchase-requests/20/coffee.jpg"))
                .rewardType("FIXED")
                .fixedRewardAmount(150)
                .requesterCity("台中市")
                .deliveryPublicSummary("台中市西區面交")
                .deliveryPrivateDetail(Map.of("address", "私密地址 123 號"))
                .status("OPEN")
                .build();
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(request));

        String html = sharePageService.renderPurchaseRequestPage(20L);

        assertTrue(html.contains("https://api.example.com/uploads/purchase-requests/20/coffee.jpg"));
        assertTrue(html.contains("酬勞 NT$ 150"));
        assertTrue(html.contains("台中市西區面交"));
        assertFalse(html.contains("私密地址 123 號"));
    }
}
