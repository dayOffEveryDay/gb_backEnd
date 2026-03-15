package com.costco.gb.service;

import com.costco.gb.dto.request.CreateCampaignRequest;
import com.costco.gb.dto.response.CampaignSummaryResponse;
import com.costco.gb.entity.Campaign;
import com.costco.gb.entity.Category;
import com.costco.gb.entity.Store;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.CategoryRepository;
import com.costco.gb.repository.StoreRepository;
import com.costco.gb.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.costco.gb.entity.Participant;
import com.costco.gb.repository.ParticipantRepository;
import com.costco.gb.dto.request.JoinCampaignRequest;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipantRepository participantRepository;

    public Page<CampaignSummaryResponse> getActiveCampaigns(Integer storeId, int page, int size) {

        // 1. 設定分頁與排序 (依建立時間倒序，越新的在越上面)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        LocalDateTime now = LocalDateTime.now();
        Page<Campaign> campaignPage;

        // 2. 判斷是否有傳入門市 ID 進行過濾
        if (storeId != null) {
            campaignPage = campaignRepository.findByStoreIdAndStatusAndExpireTimeAfter(
                    storeId, "OPEN", now, pageable);
        } else {
            campaignPage = campaignRepository.findByStatusAndExpireTimeAfter(
                    "OPEN", now, pageable);
        }

        // 3. 將 Entity 轉換為 DTO 回傳
        return campaignPage.map(this::mapToSummaryResponse);
    }
    @Transactional
    public void createCampaign(Long hostId, CreateCampaignRequest request) {

        // 1. 驗證團主身分與開團權限
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        if (!host.getHasCostcoMembership()) {
            throw new RuntimeException("拒絕存取：必須擁有好市多會員卡才能發起合購！");
        }

        // 2. 取得並驗證關聯實體 (門市與分類)
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new RuntimeException("找不到指定的門市"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("找不到指定的分類"));

        // 3. 建立 Campaign 實體
        Campaign campaign = Campaign.builder()
                .host(host)
                .store(store)
                .category(category)
                .scenarioType(request.getScenarioType())
                .itemName(request.getItemName())
                .itemImageUrl(request.getItemImageUrl())
                .pricePerUnit(request.getPricePerUnit())
                .totalQuantity(request.getTotalQuantity())
                .availableQuantity(request.getTotalQuantity()) // 💡 關鍵：初始剩餘數量 = 總數量
                .meetupLocation(request.getMeetupLocation())
                .meetupTime(request.getMeetupTime())
                .expireTime(request.getExpireTime())
                .status("OPEN") // 狀態預設為招募中
                .build();

        // 4. 寫入資料庫
        campaignRepository.save(campaign);

        // TODO: V2 進階版，我們要在這裡把 availableQuantity 寫入 Redis，準備迎接高併發搶單！

        log.info("User {} 成功發起了合購單: {}", hostId, campaign.getItemName());
    }

    // 輔助方法：Entity 轉 DTO
    private CampaignSummaryResponse mapToSummaryResponse(Campaign campaign) {
        return CampaignSummaryResponse.builder()
                .id(campaign.getId())
                .itemName(campaign.getItemName())
                .itemImageUrl(campaign.getItemImageUrl())
                .pricePerUnit(campaign.getPricePerUnit())
                .totalQuantity(campaign.getTotalQuantity())
                .availableQuantity(campaign.getAvailableQuantity())
                .meetupLocation(campaign.getMeetupLocation())
                .meetupTime(campaign.getMeetupTime())
                .status(campaign.getStatus())
                .storeName(campaign.getStore().getName())
                .categoryName(campaign.getCategory().getName())
                .host(CampaignSummaryResponse.HostSummary.builder()
                        .id(campaign.getHost().getId())
                        .displayName(campaign.getHost().getDisplayName())
                        .profileImageUrl(campaign.getHost().getProfileImageUrl())
                        .creditScore(campaign.getHost().getCreditScore())
                        .build())
                .build();
    }
    @Transactional
    public void joinCampaign(Long userId, Long campaignId, JoinCampaignRequest request) {

        // 1. 基本防呆檢查
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));
        // 👇 拿現在的時間跟合購單的到期時間比對
        if (campaign.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("來不及啦！這筆合購單已經過了截止時間囉。");
        }
        if (!"OPEN".equals(campaign.getStatus())) {
            throw new RuntimeException("手腳太慢啦！此合購單已滿單或關閉。");
        }

        if (campaign.getHost().getId().equals(userId)) {
            throw new RuntimeException("您是團主，不能參加自己發起的合購單喔！");
        }

        if (request.getQuantity() <= 0) {
            throw new RuntimeException("認購數量必須大於 0");
        }

        // 2. 防連點：檢查是否已經參加過
        if (participantRepository.findByCampaignIdAndUserId(campaignId, userId).isPresent()) {
            throw new RuntimeException("您已經參加過這團了，請勿重複點擊！");
        }

        // 3. 🛡️ 核心防禦：原子性扣庫存
        int updatedRows = campaignRepository.decrementQuantity(campaignId, request.getQuantity());

        // 如果 updatedRows 是 0，代表條件不成立 (庫存不足，或是狀態變了)
        if (updatedRows == 0) {
            throw new RuntimeException("剩餘數量不足。請確認認購數量或是是否已滿團。");
        }

        // 4. 建立認購明細
        User user = userRepository.getReferenceById(userId); // 使用 getReferenceById 節省一次 DB 查詢
        Participant participant = Participant.builder()
                .campaign(campaign)
                .user(user)
                .quantity(request.getQuantity())
                .status("JOINED")
                .build();
        participantRepository.save(participant);

        // 5. 檢查是否滿單 (滿單則自動更改狀態)
        // 因為前面已經扣除成功，我們把目前的數量減去請求數量，看看是不是剛好歸零
        int remaining = campaign.getAvailableQuantity() - request.getQuantity();
        if (remaining == 0) {
            campaign.setStatus("FULL");
            campaignRepository.save(campaign); // Hibernate 會幫我們 UPDATE 狀態
            log.info("合購單 {} 已達滿單狀態！", campaignId);
            // TODO: 未來在這裡觸發 WebSocket 或推播通知給團主和所有團員
        }

        log.info("User {} 成功認購了合購單 {}，數量: {}", userId, campaignId, request.getQuantity());
    }
    @Transactional
    public void withdrawCampaign(Long userId, Long campaignId) {

        // 1. 檢查該會員是否真的有加入這團
        Participant participant = participantRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("您並未參加此合購單！"));

        // 如果他早就退出過了，防呆擋下
        if (!"JOINED".equals(participant.getStatus())) {
            throw new RuntimeException("您已經退出過了，無法重複操作！");
        }

        // 2. 檢查合購單狀態 (如果已經完成交易，或是流局取消，就不能退出了)
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("找不到該合購單"));

        if (!"OPEN".equals(campaign.getStatus()) && !"FULL".equals(campaign.getStatus())) {
            throw new RuntimeException("此合購單已進入交易階段或已結束，無法退出！請直接聯繫團主。");
        }

        // 3. 變更參與明細狀態為「已取消」
        participant.setStatus("CANCELLED");
        participantRepository.save(participant);

        // 4. 🛡️ 原子性加回庫存，並將狀態強制切回 OPEN
        int updatedRows = campaignRepository.incrementQuantityAndReopen(campaignId, participant.getQuantity());
        if (updatedRows == 0) {
            throw new RuntimeException("退出失敗，合購單狀態可能發生異常！");
        }

        // 5. ⚠️ 信用記點：增加該使用者的「反悔次數」
        User user = userRepository.findById(userId).orElseThrow();
        user.setParticipantCancelCount(user.getParticipantCancelCount() + 1);
        // (V2 進階：如果反悔次數過高，可以在這裡順便扣他的 creditScore 信用分數)
        userRepository.save(user);

        // 6. 紀錄 MDC TraceId 追蹤日誌
        log.info("User {} 成功退出了合購單 {}，釋放數量: {}，累積反悔次數: {}",
                userId, campaignId, participant.getQuantity(), user.getParticipantCancelCount());

        // TODO: 未來可在這裡觸發推播 (Notification)，通知團主有人跳車了
    }
}