package com.costco.gb.repository;

import com.costco.gb.entity.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // 首頁大廳用：查詢指定門市、特定狀態 (如 OPEN)，且「還沒過期」的合購單，並支援分頁
    Page<Campaign> findByStoreIdAndStatusAndExpireTimeAfter(
            Integer storeId,
            String status,
            LocalDateTime now,
            Pageable pageable
    );

    // 團主個人頁面：查詢某位團主發起的所有合購單 (依建立時間倒序)
    Page<Campaign> findByHostIdOrderByCreatedAtDesc(Long hostId, Pageable pageable);

    // 排程用：找出所有狀態是 OPEN 但已經過期的單，準備把它們標記為 EXPIRED
    List<Campaign> findByStatusAndExpireTimeBefore(String status, LocalDateTime now);
}
