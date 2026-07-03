package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderEventRepository extends JpaRepository<PurchaseOrderEvent, Long> {

    List<PurchaseOrderEvent> findByPurchaseOrderIdOrderByCreatedAtAsc(Long orderId);
}
