package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseChatRoomMemberRepository extends JpaRepository<PurchaseChatRoomMember, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE purchase_chat_room_members SET last_read_message_id = :messageId,
              last_read_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE room_id = :roomId AND user_id = :userId
              AND (last_read_message_id IS NULL OR last_read_message_id < :messageId)
            """, nativeQuery = true)
    int advanceRead(@Param("roomId") Long roomId, @Param("userId") Long userId,
                    @Param("messageId") Long messageId);
    Optional<PurchaseChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    List<PurchaseChatRoomMember> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
