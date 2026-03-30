package com.costco.gb.repository;

import com.costco.gb.entity.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // 首頁大廳用：查詢指定門市、特定狀態 (如 OPEN)，且「還沒過期」的合購單，並支援分頁
    Page<Campaign> findByStoreIdAndStatusAndExpireTimeAfter(
            Integer storeId,
            String status,
            LocalDateTime now,
            Pageable pageable
    );
    // 不指定門市，查詢所有特定狀態且未過期的單
    Page<Campaign> findByStatusAndExpireTimeAfter(
            String status, LocalDateTime now, Pageable pageable);
    // 團主個人頁面：查詢某位團主發起的所有合購單 (依建立時間倒序)
    Page<Campaign> findByHostIdOrderByCreatedAtDesc(Long hostId, Pageable pageable);

    // 🌟 修正版：撈取「我跟的團」，必須確保 Participant 的狀態是 JOINED！
    @Query("SELECT c FROM Campaign c JOIN Participant p ON c.id = p.campaign.id " +
            "WHERE p.user.id = :userId AND p.status = 'JOINED' " +
            "ORDER BY c.createdAt DESC")
    Page<Campaign> findJoinedCampaignsByUserId(@Param("userId") Long userId, Pageable pageable);

    // 支援動態條件的超強大查詢 (如果參數傳 null，該條件就會自動被忽略)
    @Query("SELECT c FROM Campaign c WHERE " +
            "(:storeId IS NULL OR c.store.id = :storeId) AND " +
            "(:categoryId IS NULL OR c.category.id = :categoryId) AND " +
            "(:keyword IS NULL OR c.itemName LIKE %:keyword%) AND " +
            "c.status IN ('OPEN', 'FULL')") // 首頁通常只顯示還在進行中的單
    Page<Campaign> findCampaignsWithFilters(
            @Param("storeId") Integer storeId,
            @Param("categoryId") Integer categoryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Modifying
    @Query("UPDATE Campaign c SET c.availableQuantity = c.availableQuantity - :quantity " +
            "WHERE c.id = :campaignId AND c.availableQuantity >= :quantity AND c.status = 'OPEN'")
    int decrementQuantity(@Param("campaignId") Long campaignId, @Param("quantity") Integer quantity);


    // 🌟 新增：安全地歸還庫存 (加回去)
    @Modifying
    @Query("UPDATE Campaign c SET c.availableQuantity = c.availableQuantity + :quantity WHERE c.id = :campaignId")
    int incrementQuantity(@Param("campaignId") Long campaignId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("UPDATE Campaign c SET c.availableQuantity = c.availableQuantity + :quantity, " +
            "c.status = 'OPEN' " + // 不管原本是 OPEN 還是 FULL，只要有人退，就一定是 OPEN
            "WHERE c.id = :campaignId AND c.status IN ('OPEN', 'FULL')")
    int incrementQuantityAndReopen(@Param("campaignId") Long campaignId, @Param("quantity") Integer quantity);

    // 找出所有狀態為 OPEN 且過期的合購單，並將狀態改為 FAILED (流局/過期)
    @Modifying
    @Query("UPDATE Campaign c SET c.status = 'FAILED' " +
            "WHERE c.status = 'OPEN' AND c.expireTime < :now")
    int updateExpiredCampaignsStatus(@Param("now") LocalDateTime now);

    // 找出面交時間已經過了指定時間，但狀態還卡在 OPEN 或 FULL 的幽靈單
    @Query("SELECT c FROM Campaign c WHERE c.status IN ('OPEN', 'FULL') AND c.meetupTime < :timeoutLimit")
    List<Campaign> findGhostedCampaigns(@Param("timeoutLimit") LocalDateTime timeoutLimit);

    // 找出超過指定時間 (如 3 個月前)，且已經不再活動的歷史合購單
    @Query("SELECT c FROM Campaign c WHERE c.meetupTime < :timeLimit AND c.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'HOST_NO_SHOW')")
    List<Campaign> findOldCampaignsToCleanup(@Param("timeLimit") LocalDateTime timeLimit);


    // 🧹 大掃除專用：找出超過 3 個月、已結案/失敗，且「還有圖片沒清掉」的歷史合購單
    @Query("SELECT c FROM Campaign c WHERE c.meetupTime < :timeLimit AND c.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'HOST_NO_SHOW') AND c.imageUrls IS NOT NULL AND c.imageUrls != ''")
    List<Campaign> findOldCampaignsWithImagesToCleanup(@Param("timeLimit") LocalDateTime timeLimit);

    // 🧹 找出需要瘦身的舊單 (排除已經壓縮過的 _thumb)
    @Query("SELECT c FROM Campaign c WHERE c.meetupTime < :timeLimit " +
            "AND c.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'HOST_NO_SHOW') " +
            "AND c.imageUrls IS NOT NULL AND c.imageUrls NOT LIKE '%_thumb%'")
    List<Campaign> findOldCampaignsForImageCompression(@Param("timeLimit") LocalDateTime timeLimit);


}
