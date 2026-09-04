package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 商品詢問問題主表。 */
@Entity
@Table(name = "inquiries", indexes = {
        @Index(name = "idx_inquiry_user", columnList = "user_id"),
        @Index(name = "idx_inquiry_type_status", columnList = "type, status"),
        @Index(name = "idx_inquiry_store_status", columnList = "store_id, status"),
        @Index(name = "idx_inquiry_expire_time", columnList = "expire_time"),
        @Index(name = "idx_inquiry_created_at", columnList = "created_at"),
        @Index(name = "idx_inquiry_product_name", columnList = "product_name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_inquiries_user"))
    private User user;

    // 問題類型，例如 STOCK 或 EXPERIENCE。
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "product_name", length = 255)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", foreignKey = @ForeignKey(name = "fk_inquiries_store"))
    private Store store;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_urls", length = 1024)
    private String imageUrls;

    // 問題狀態，例如 OPEN 或 EXPIRED。
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
