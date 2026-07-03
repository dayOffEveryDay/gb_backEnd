package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.id = :id")
    Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT pr FROM PurchaseRequest pr WHERE " +
            "((:status IS NULL AND pr.status IN ('OPEN', 'QUOTING')) OR pr.status = :status) AND " +
            "(:status IS NOT NULL OR pr.requestExpireTime > :now) AND " +
            "(:city IS NULL OR pr.requesterCity = :city) AND " +
            "(:district IS NULL OR pr.requesterDistrict = :district) AND " +
            "(:deliveryMethod IS NULL OR pr.deliveryMethod = :deliveryMethod) AND " +
            "(:rewardType IS NULL OR pr.rewardType = :rewardType) AND " +
            "(:keyword IS NULL OR LOWER(pr.itemName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(COALESCE(pr.itemDescription, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))" )
    Page<PurchaseRequest> findWithFilters(
            @Param("status") String status,
            @Param("city") String city,
            @Param("district") String district,
            @Param("deliveryMethod") String deliveryMethod,
            @Param("rewardType") String rewardType,
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("SELECT pr FROM PurchaseRequest pr WHERE pr.requester.id = :userId " +
            "AND (:status IS NULL OR pr.status = :status)")
    Page<PurchaseRequest> findCreatedByUser(
            @Param("userId") Long userId,
            @Param("status") String status,
            Pageable pageable
    );

    List<PurchaseRequest> findByStatusInAndRequestExpireTimeBefore(List<String> statuses, LocalDateTime now);
}
