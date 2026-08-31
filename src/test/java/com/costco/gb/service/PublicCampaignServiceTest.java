package com.costco.gb.service;

import com.costco.gb.dto.response.PublicCampaignDetailResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Category;
import com.costco.gb.entity.Store;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @InjectMocks
    private PublicCampaignService publicCampaignService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publicCampaignService, "baseUrl", "https://gb.ttgtool.com/");
    }

    @Test
    void returnsOnlyPublicCampaignDetailWithTaipeiOffset() {
        Campaign campaign = campaign("OPEN", LocalDateTime.now().plusDays(1));
        when(campaignRepository.findPublicDetailsById(8L)).thenReturn(Optional.of(campaign));

        PublicCampaignDetailResponse response = publicCampaignService.getCampaign(8L);

        assertEquals("金色三麥包種茶啤酒", response.getItemName());
        assertEquals("飲料", response.getCategoryName());
        assertEquals("中和店", response.getStoreName());
        assertEquals("https://gb.ttgtool.com/images/campaign-8-1.jpg", response.getImageUrls().get(0));
        assertEquals(ZoneOffset.ofHours(8), response.getMeetupTime().getOffset());
        assertTrue(response.isCanJoin());
    }

    @Test
    void fullAndExpiredCampaignsRemainVisibleButCannotJoin() {
        Campaign fullCampaign = campaign("FULL", LocalDateTime.now().plusDays(1));
        when(campaignRepository.findPublicDetailsById(8L)).thenReturn(Optional.of(fullCampaign));
        assertFalse(publicCampaignService.getCampaign(8L).isCanJoin());

        Campaign expiredCampaign = campaign("OPEN", LocalDateTime.now().minusMinutes(1));
        when(campaignRepository.findPublicDetailsById(9L)).thenReturn(Optional.of(expiredCampaign));
        assertFalse(publicCampaignService.getCampaign(9L).isCanJoin());
    }

    @Test
    void missingCampaignReturnsNotFound() {
        when(campaignRepository.findPublicDetailsById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> publicCampaignService.getCampaign(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void cancelledCampaignReturnsGone() {
        when(campaignRepository.findPublicDetailsById(8L))
                .thenReturn(Optional.of(campaign("CANCELLED", LocalDateTime.now().plusDays(1))));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> publicCampaignService.getCampaign(8L));

        assertEquals(HttpStatus.GONE, exception.getStatusCode());
    }

    private Campaign campaign(String status, LocalDateTime expireTime) {
        return Campaign.builder()
                .id(8L)
                .scenarioType("SCHEDULED")
                .status(status)
                .itemName("金色三麥包種茶啤酒")
                .category(Category.builder().name("飲料").build())
                .availableQuantity(5)
                .totalQuantity(12)
                .pricePerUnit(85)
                .store(Store.builder().name("中和店").build())
                .meetupLocation("聊天討論")
                .meetupTime(LocalDateTime.of(2026, 9, 6, 12, 27))
                .expireTime(expireTime)
                .imageUrls("campaign-8-1.jpg")
                .host(User.builder()
                        .id(4L)
                        .displayName("開發者 4")
                        .profileImageUrl("https://example.com/avatar.jpg")
                        .creditScore(100)
                        .build())
                .build();
    }
}
