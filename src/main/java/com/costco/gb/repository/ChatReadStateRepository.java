package com.costco.gb.repository;

import com.costco.gb.entity.ChatReadState;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
    // Atomic insert/update also handles the first read racing across devices.
    // Assign the timestamp before the cursor so the comparison uses the old cursor.
    @Modifying
    @Query(value = """
            INSERT INTO chat_read_states (campaign_id, user_id, last_read_message_id, last_read_at)
            VALUES (:campaignId, :userId, :messageId, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
              last_read_at = IF(COALESCE(last_read_message_id, 0) < :messageId,
                                CURRENT_TIMESTAMP, last_read_at),
              last_read_message_id = GREATEST(COALESCE(last_read_message_id, 0), :messageId)
            """, nativeQuery = true)
    void advance(@Param("campaignId") Long campaignId, @Param("userId") Long userId,
                 @Param("messageId") Long messageId);
}
