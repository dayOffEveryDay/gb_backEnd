package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findFirstByPurchaseRequestIdOrderByCreatedAtDesc(Long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    Page<PurchaseOrder> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    Page<PurchaseOrder> findByRequesterIdAndStatusOrderByCreatedAtDesc(
            Long requesterId,
            String status,
            Pageable pageable
    );

    Page<PurchaseOrder> findByRunnerIdOrderByCreatedAtDesc(Long runnerId, Pageable pageable);

    Page<PurchaseOrder> findByRunnerIdAndStatusOrderByCreatedAtDesc(
            Long runnerId,
            String status,
            Pageable pageable
    );

    List<PurchaseOrder> findByStatusAndAbnormalReportedAtBeforeAndAbnormalRespondedAtIsNull(
            String status,
            LocalDateTime timeoutBefore
    );

    List<PurchaseOrder> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    List<PurchaseOrder> findByStatusIn(List<String> statuses);
}
