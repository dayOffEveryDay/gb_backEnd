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

    // 包含：進行中(JOINED)、已完成(COMPLETED)、爭議中(DISPUTED)、被標放鳥(NO_SHOW)
    // 刻意排除：自己跳車(CANCELLED)、被踢除(KICKED)
    @Query("SELECT c FROM Campaign c JOIN Participant p ON c.id = p.campaign.id " +
            "WHERE p.user.id = :userId AND p.status IN ('JOINED', 'COMPLETED', 'DISPUTED', 'NO_SHOW','CONFIRMED') " +
            "ORDER BY c.createdAt DESC")
    Page<Campaign> findJoinedCampaignsByUserId(@Param("userId") Long userId, Pageable pageable);

    // 🌟 終極防護版大廳查詢：支援動態條件 + 雙向黑名單隱藏！
    @Query("SELECT c FROM Campaign c WHERE " +
            "(:storeId IS NULL OR c.store.id = :storeId) AND " +
            "(:categoryId IS NULL OR c.category.id = :categoryId) AND " +
            "(:keyword IS NULL OR c.itemName LIKE %:keyword%) AND " +
            "c.status IN ('OPEN', 'FULL') AND " +
            // 🛡️ 黑名單雙向過濾：如果 userId 存在，就執行過濾
            "(:userId IS NULL OR (" +
            "  NOT EXISTS (SELECT b FROM Block b WHERE b.blocker.id = :userId AND b.blocked.id = c.host.id) AND " +
            "  NOT EXISTS (SELECT b FROM Block b WHERE b.blocker.id = c.host.id AND b.blocked.id = :userId)" +
            "))")
    Page<Campaign> findCampaignsWithFilters(
            @Param("storeId") Integer storeId,
            @Param("categoryId") Integer categoryId,
            @Param("keyword") String keyword,
            @Param("userId") Long userId, // ✨ 新增這行：傳入當前登入者 ID
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

    // 🌟 升級版大廳查詢：加入雙向黑名單過濾！
    // 條件 1：合購單狀態要是 OPEN
    // 條件 2：我不可以看到「我拉黑的人」開的團 (blocker = 我, blocked = 團主)
    // 條件 3：我不可以看到「拉黑我的人」開的團 (blocker = 團主, blocked = 我)
    @Query("SELECT c FROM Campaign c " +
            "WHERE c.status = 'OPEN' " +
            "AND NOT EXISTS (SELECT b FROM Block b WHERE b.blocker.id = :userId AND b.blocked.id = c.host.id) " +
            "AND NOT EXISTS (SELECT b FROM Block b WHERE b.blocker.id = c.host.id AND b.blocked.id = :userId) " +
            "ORDER BY c.createdAt DESC")
    Page<Campaign> findAvailableCampaignsWithFilter(@Param("userId") Long userId, Pageable pageable);


    // 🌟 專屬個人頁面用：撈取某位團主「正在進行中」的合購單
    @Query("SELECT c FROM Campaign c WHERE c.host.id = :hostId AND c.status IN ('OPEN') ORDER BY c.createdAt DESC")
    List<Campaign> findActiveCampaignsByHostId(@Param("hostId") Long hostId);

    // 🌟 計算該使用者作為「團主」成功結案的數量
    int countByHostIdAndStatus(Long hostId, String status);

}
