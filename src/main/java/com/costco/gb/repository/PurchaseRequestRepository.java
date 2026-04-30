package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @Query("SELECT pr FROM PurchaseRequest pr WHERE " +
            "(:keyword IS NULL OR LOWER(pr.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:rewardType IS NULL OR pr.rewardType = :rewardType) AND " +
            "(:deliveryMethod IS NULL OR pr.deliveryMethod = :deliveryMethod) AND " +
            "(:requestArea IS NULL OR pr.requestArea LIKE %:requestArea%) AND " +
            "(:status IS NULL OR pr.status = :status) AND " +
            "(:onlyAvailable = false OR (pr.status = 'OPEN' AND (pr.deadlineAt IS NULL OR pr.deadlineAt > :now)))")
    Page<PurchaseRequest> findWithFilters(
            @Param("keyword") String keyword,
            @Param("rewardType") String rewardType,
            @Param("deliveryMethod") String deliveryMethod,
            @Param("requestArea") String requestArea,
            @Param("status") String status,
            @Param("onlyAvailable") boolean onlyAvailable,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    Page<PurchaseRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId, Pageable pageable);

    Page<PurchaseRequest> findByAssignedRunnerIdOrderByCreatedAtDesc(Long assignedRunnerId, Pageable pageable);

    @Query("SELECT DISTINCT pr FROM PurchaseRequest pr JOIN PurchaseRequestQuote q ON q.purchaseRequest.id = pr.id " +
            "WHERE q.runner.id = :runnerId ORDER BY pr.createdAt DESC")
    Page<PurchaseRequest> findQuotedByRunnerId(@Param("runnerId") Long runnerId, Pageable pageable);
}
