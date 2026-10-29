package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PurchaseChatMessageRepository extends JpaRepository<PurchaseChatMessage, Long> {
    boolean existsByIdAndRoomId(Long id, Long roomId);
    List<PurchaseChatMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);
    Optional<PurchaseChatMessage> findFirstByRoomIdOrderByIdDesc(Long roomId);
    long countByRoomIdAndIdGreaterThanAndSenderIdNot(Long roomId, Long lastReadMessageId, Long senderId);
}
