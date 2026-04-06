package com.costco.gb.repository;

import com.costco.gb.entity.Participant;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // (連 CANCELLED 也會抓出來)
     Optional<Participant> findByCampaignIdAndUserId(Long campaignId, Long userId);

    List<Participant> findByCampaignId(Long campaignId);

    // ✅ 修正版 1：專門抓「還在車上 (狀態為 JOINED)」的參團紀錄
    Optional<Participant> findByCampaignIdAndUserIdAndStatus(Long campaignId, Long userId, String status);

    // ✅ 修正版 2：撈出這張合購單「所有未取消」的團員 (發送滿單通知時要用這個，不然跳車的人也會收到通知！)
    List<Participant> findByCampaignIdAndStatus(Long campaignId, String status);
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

    // 在 ParticipantRepository 裡面補上這段：
    // 🧹 大掃除專用：批次刪除指定合購單底下的所有參與明細
    @Modifying
    @Query("DELETE FROM Participant p WHERE p.campaign.id = :campaignId")
    int deleteByCampaignId(@Param("campaignId") Long campaignId);

    // 🌟 計算該使用者作為「團員」成功結案的數量
    int countByUserIdAndStatus(Long userId, String status);


}
