package com.costco.gb.service;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.dto.request.JoinCampaignRequest;
import com.costco.gb.entity.Category;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Participant;
import com.costco.gb.entity.Store;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.CategoryRepository;
import com.costco.gb.repository.ParticipantRepository;
import com.costco.gb.repository.StoreRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // 啟用 Mockito 支援
class CampaignServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private CreditScoreService creditScoreService;
    @Mock
    private CampaignChatAccessService campaignChatAccessService;

    @InjectMocks
    private CampaignService campaignService; // 會自動把上面 @Mock 的物件注入進來

    private User validHost;
    private Store validStore;
    private Category validCategory;
    private CreateCampaignRequest validRequest;

    @BeforeEach
    void setUp() {
        // 每次執行測試前，先準備好假資料
        validHost = User.builder().id(1L).displayName("Howard 輝").build();
        validStore = Store.builder().id(1).name("中和店").build();
        validCategory = Category.builder().id(1).name("生鮮").build();

        validRequest = new CreateCampaignRequest();
        validRequest.setStoreId(1);
        validRequest.setCategoryId(1);
        validRequest.setScenarioType("INSTANT");
        validRequest.setItemName("科克蘭鮮乳");
        validRequest.setPricePerUnit(135);
        validRequest.setProductTotalQuantity(2);
        validRequest.setOpenQuantity(2);
        validRequest.setMeetupLocation("結帳區");
        validRequest.setMeetupTime(LocalDateTime.now().plusHours(2));
        validRequest.setExpireTime(LocalDateTime.now().plusHours(1));
    }

    @Test
    @DisplayName("✅ 測試成功發起合購單：資料應正確儲存且初始庫存需等於總數")
    void createCampaign_Success() {
        // Arrange: 設定 Mock 物件的行為 (當去資料庫找資料時，回傳我們準備好的假資料)
        when(userRepository.findById(1L)).thenReturn(Optional.of(validHost));
        when(storeRepository.findById(1)).thenReturn(Optional.of(validStore));
        when(categoryRepository.findById(1)).thenReturn(Optional.of(validCategory));

        // Act: 執行我們要測試的方法
        campaignService.createCampaign(1L, validRequest);

        // Assert: 驗證
        // 1. 驗證 campaignRepository.save() 有被呼叫過「剛好 1 次」
        ArgumentCaptor<Campaign> campaignCaptor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository, times(1)).save(campaignCaptor.capture());

        // 2. 攔截存進去的 Campaign 物件，檢查裡面的邏輯對不對
        Campaign savedCampaign = campaignCaptor.getValue();
        assertEquals("科克蘭鮮乳", savedCampaign.getItemName());
        assertEquals("OPEN", savedCampaign.getStatus(), "剛建立的訂單狀態必須是 OPEN");
        assertEquals(2, savedCampaign.getAvailableQuantity(), "初始剩餘數量必須等於總數量");
        assertEquals(validHost, savedCampaign.getHost(), "開團人必須綁定正確");
    }

    @Test
    @DisplayName("❌ 測試防呆：找不到指定門市應拋出例外")
    void createCampaign_Fail_StoreNotFound() {
        // Arrange: 模擬有會員，但門市 ID 亂填，資料庫找不到
        when(userRepository.findById(1L)).thenReturn(Optional.of(validHost));
        when(storeRepository.findById(1)).thenReturn(Optional.empty()); // 模擬找不到

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            campaignService.createCampaign(1L, validRequest);
        });

        assertEquals("找不到指定的門市", exception.getMessage());
        verify(campaignRepository, never()).save(any(Campaign.class));
    }

    @Test
    @DisplayName("加入最後名額後應滿單並通知")
    void joinCampaign_LastAvailableQuantity_MarksCampaignFull() {
        Campaign campaign = campaign(2, "OPEN");
        User participantUser = User.builder().id(2L).totalJoinedCount(0).build();
        JoinCampaignRequest request = new JoinCampaignRequest();
        request.setQuantity(2);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.decrementQuantity(10L, 2)).thenReturn(1);
        when(participantRepository.findByCampaignIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(participantUser));

        campaignService.joinCampaign(2L, 10L, request);

        ArgumentCaptor<Participant> participantCaptor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        assertEquals("JOINED", participantCaptor.getValue().getStatus());
        assertEquals(2, participantCaptor.getValue().getQuantity());
        assertEquals("FULL", campaign.getStatus());
        assertEquals(0, campaign.getAvailableQuantity());
        assertEquals(1, participantUser.getTotalJoinedCount());
        verify(notificationService).notifyCampaignFull(campaign);
    }

    @Test
    @DisplayName("庫存原子扣除失敗時不得建立參與紀錄")
    void joinCampaign_InsufficientQuantity_DoesNotCreateParticipant() {
        Campaign campaign = campaign(1, "OPEN");
        JoinCampaignRequest request = new JoinCampaignRequest();
        request.setQuantity(2);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.decrementQuantity(10L, 2)).thenReturn(0);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> campaignService.joinCampaign(2L, 10L, request));

        assertEquals("剩餘數量不足。請確認認購數量或是是否已滿團。", exception.getMessage());
        assertEquals(1, campaign.getAvailableQuantity());
        verify(participantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("團員退出應釋放名額、恢復招募並記錄取消次數")
    void withdrawCampaign_JoinedParticipant_ReopensCampaignAndNotifiesHost() {
        Campaign campaign = campaign(0, "FULL");
        campaign.setAllowRevision(true);
        User participantUser = User.builder().id(2L).participantCancelCount(null).build();
        Participant participant = Participant.builder().user(participantUser).campaign(campaign)
                .quantity(2).status("JOINED").build();
        when(participantRepository.findByCampaignIdAndUserIdAndStatus(10L, 2L, "JOINED"))
                .thenReturn(Optional.of(participant));
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(userRepository.findById(2L)).thenReturn(Optional.of(participantUser));

        campaignService.withdrawCampaign(2L, 10L);

        assertEquals("CANCELLED", participant.getStatus());
        assertEquals("OPEN", campaign.getStatus());
        assertFalse(campaign.isAllowRevision());
        assertEquals(2, campaign.getAvailableQuantity());
        assertEquals(1, participantUser.getParticipantCancelCount());
        verify(notificationService).sendNotification(campaign.getHost(), "MEMBER_WITHDRAW", 10L,
                "😢 團員已退出您的合購單「測試商品」，目前已恢復招募狀態。");
        verify(notificationService).broadcastChatAccessRevoked(10L, 2L, "WITHDRAWN");
    }

    @Test
    @DisplayName("團主在有有效團員時可宣告面交並廣播")
    void deliverCampaign_HostWithJoinedParticipants_MarksDeliveredAndBroadcasts() {
        Campaign campaign = campaign(1, "OPEN");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(participantRepository.countByCampaignIdAndStatus(10L, "JOINED")).thenReturn(1L);

        campaignService.deliverCampaign(1L, 10L);

        assertEquals("DELIVERED", campaign.getStatus());
        verify(campaignRepository).save(campaign);
        verify(notificationService).broadcastCampaignStatus(10L, "DELIVERED", "主揪已發起面交");
    }

    @Test
    @DisplayName("最後一位團員確認收貨時應完成合購並開放評價")
    void confirmReceipt_LastJoinedParticipant_CompletesCampaignAndNotifiesReview() {
        Campaign campaign = campaign(0, "DELIVERED");
        Participant participant = Participant.builder().campaign(campaign)
                .user(User.builder().id(2L).build()).quantity(1).status("JOINED").build();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(participantRepository.findByCampaignIdAndUserId(10L, 2L)).thenReturn(Optional.of(participant));
        when(participantRepository.countByCampaignIdAndStatus(10L, "JOINED")).thenReturn(0L);

        campaignService.confirmReceipt(2L, 10L);

        assertEquals("CONFIRMED", participant.getStatus());
        assertEquals("COMPLETED", campaign.getStatus());
        assertNotNull(campaign.getCompletedAt());
        verify(campaignRepository).save(campaign);
        verify(notificationService).notifyReviewTime(campaign);
        verify(notificationService).broadcastCampaignStatus(
                10L, "COMPLETED", "團購已完成，聊天室將保留 3 天");
    }

    @Test
    @DisplayName("團主取消已有團員的合購應取消全員並扣除信用分")
    void cancelCampaignByHost_WithJoinedParticipants_CancelsParticipantsAndPenalizesHost() {
        Campaign campaign = campaign(2, "OPEN");
        User joinedUser = User.builder().id(2L).build();
        Participant participant = Participant.builder().campaign(campaign).user(joinedUser)
                .quantity(1).status("JOINED").build();
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(participantRepository.findByCampaignIdAndStatus(10L, "JOINED")).thenReturn(List.of(participant));

        campaignService.cancelCampaignByHost(1L, 10L);

        assertEquals("CANCELLED", campaign.getStatus());
        assertEquals(2, campaign.getAvailableQuantity());
        assertEquals(campaign.getHost(), campaign.getBlameUser());
        assertEquals("CANCELLED_BY_HOST", participant.getStatus());
        assertEquals(1, campaign.getHost().getHostCancelCount());
        verify(creditScoreService).recordScoreChange(1L, -10,
                "取消已有團員的合購單：「測試商品」", 10L);
        verify(participantRepository).saveAll(List.of(participant));
        verify(notificationService).notifyCampaignCancelled(campaign, List.of(participant));
        verify(notificationService).broadcastCampaignStatus(
                10L, "CANCELLED", "團購已由團主取消，聊天室改為唯讀");
    }

    private Campaign campaign(int availableQuantity, String status) {
        return Campaign.builder()
                .id(10L)
                .host(User.builder().id(1L).hostCancelCount(0).build())
                .itemName("測試商品")
                .totalQuantity(2)
                .availableQuantity(availableQuantity)
                .expireTime(LocalDateTime.now().plusHours(1))
                .status(status)
                .build();
    }
}
