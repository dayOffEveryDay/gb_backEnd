package com.costco.gb.repository;

import com.costco.gb.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 進入聊天室時，撈出該房間所有的歷史訊息 (依時間正序，舊的在上面)
    List<ChatMessage> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

    // 系統排程用：刪除超過 3 個月的歷史訊息
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}
