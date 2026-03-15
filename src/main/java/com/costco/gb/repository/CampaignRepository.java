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


    @Modifying
    @Query("UPDATE Campaign c SET c.availableQuantity = c.availableQuantity - :quantity " +
            "WHERE c.id = :campaignId AND c.availableQuantity >= :quantity AND c.status = 'OPEN'")
    int decrementQuantity(@Param("campaignId") Long campaignId, @Param("quantity") Integer quantity);

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

}
