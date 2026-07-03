package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequestMeetupRange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRequestMeetupRangeRepository extends JpaRepository<PurchaseRequestMeetupRange, Long> {

    List<PurchaseRequestMeetupRange> findByPurchaseRequestIdOrderById(Long requestId);

    long countByPurchaseRequestId(Long requestId);

    void deleteByPurchaseRequestId(Long requestId);
}
