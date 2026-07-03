package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PurchaseChatRoomRepository extends JpaRepository<PurchaseChatRoom, Long> {
    Optional<PurchaseChatRoom> findByPurchaseOrderId(Long orderId);
}
