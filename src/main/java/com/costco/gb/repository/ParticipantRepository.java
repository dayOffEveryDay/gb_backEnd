package com.costco.gb.repository;

import com.costco.gb.entity.Participant;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // 檢查某個使用者是否已經加入過這個合購單 (防連點防呆)
    Optional<Participant> findByCampaignIdAndUserId(Long campaignId, Long userId);

    // 我的跟團紀錄：查詢某位使用者參與的所有單
    Page<Participant> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 計算某人針對同一單的反悔次數 (防惡意加入又退出)
    int countByCampaignIdAndUserIdAndStatus(Long campaignId, Long userId, String status);
    // 計算這張單目前處於某個特定狀態（例如 'JOINED'）的團員有幾人
    long countByCampaignIdAndStatus(Long campaignId, String status);
    @Modifying
    @Query("UPDATE Participant p SET p.status = 'CANCELLED_BY_SYSTEM' WHERE p.campaign.id = :campaignId AND p.status = 'JOINED'")
    int cancelAllByCampaignId(Long campaignId);
    // 團主取消時，釋放所有團員並標記原因
    @Modifying
    @Query("UPDATE Participant p SET p.status = 'CANCELLED_BY_HOST' WHERE p.campaign.id = :campaignId AND p.status = 'JOINED'")
    int cancelAllByHost(@Param("campaignId") Long campaignId);

}
