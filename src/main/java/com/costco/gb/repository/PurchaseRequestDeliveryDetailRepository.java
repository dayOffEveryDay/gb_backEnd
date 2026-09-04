package com.costco.gb.repository;

import com.costco.gb.entity.PurchaseRequestDeliveryDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseRequestDeliveryDetailRepository extends JpaRepository<PurchaseRequestDeliveryDetail, Long> {
    List<PurchaseRequestDeliveryDetail> findByPurchaseRequestIdOrderByIdAsc(Long requestId);

    void deleteByPurchaseRequestId(Long requestId);

    @Modifying
    @Query("UPDATE PurchaseRequestDeliveryDetail detail SET detail.sourceProfile = null "
            + "WHERE detail.sourceProfile.id = :profileId")
    int clearSourceProfile(@Param("profileId") Long profileId);
}
