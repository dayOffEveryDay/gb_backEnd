package com.costco.gb.repository;

import com.costco.gb.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    boolean existsByIdAndCampaignId(Long id, Long campaignId);

    @Query(value = """
            SELECT COUNT(*) FROM chat_messages m
            WHERE m.campaign_id = :campaignId AND m.sender_id <> :userId
              AND m.id > COALESCE((SELECT s.last_read_message_id FROM chat_read_states s
                                  WHERE s.campaign_id = :campaignId AND s.user_id = :userId), 0)
            """, nativeQuery = true)
    long countUnread(@Param("campaignId") Long campaignId, @Param("userId") Long userId);

    // 進入聊天室時，撈出該房間所有的歷史訊息 (依時間正序，舊的在上面)
    List<ChatMessage> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

    // 系統排程用：刪除超過 3 個月的歷史訊息
    void deleteByCreatedAtBefore(LocalDateTime cutoffDate);
}
