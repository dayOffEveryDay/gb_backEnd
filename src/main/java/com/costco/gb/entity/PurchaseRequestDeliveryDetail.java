package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/** 託購單交付快照；來源資料只用於追蹤，顯示與交付均使用本表欄位。 */
@Entity
@Table(name = "purchase_request_delivery_details", indexes = {
        @Index(name = "idx_pr_delivery_details_request", columnList = "request_id"),
        @Index(name = "idx_pr_delivery_details_area", columnList = "city, district"),
        @Index(name = "idx_pr_delivery_details_provider_location", columnList = "provider, location_code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseRequestDeliveryDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_request_delivery_details_request"))
    private PurchaseRequest purchaseRequest;

    // SQL 外鍵 ON DELETE SET NULL；刪除常用資料不刪除或修改快照內容。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_profile_id",
            foreignKey = @ForeignKey(name = "fk_purchase_request_delivery_details_profile"))
    private UserDeliveryProfile sourceProfile;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "district", length = 50)
    private String district;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "location_code", length = 100)
    private String locationCode;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;
}
