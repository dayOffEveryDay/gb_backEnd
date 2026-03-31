package com.costco.gb.service;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.entity.Category;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Store;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.CategoryRepository;
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

    @InjectMocks
    private CampaignService campaignService; // 會自動把上面 @Mock 的物件注入進來

    private User validHost;
    private Store validStore;
    private Category validCategory;
    private CreateCampaignRequest validRequest;

    @BeforeEach
    void setUp() {
        // 每次執行測試前，先準備好假資料
        validHost = User.builder().id(1L).displayName("Howard 輝").hasCostcoMembership(true).build();
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
    @DisplayName("❌ 測試權限阻擋：沒有好市多會員卡應拋出例外")
    void createCampaign_Fail_NoMembership() {
        // Arrange: 把會員的卡片權限拔掉
        validHost.setHasCostcoMembership(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(validHost));

        // Act & Assert: 預期執行這個方法時，會拋出 RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            campaignService.createCampaign(1L, validRequest);
        });

        // 驗證錯誤訊息是否如我們設計的
        assertEquals("拒絕存取：必須擁有好市多會員卡才能發起合購！", exception.getMessage());

        // 驗證存檔動作「絕對不能」被執行
        verify(campaignRepository, never()).save(any(Campaign.class));
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
}
