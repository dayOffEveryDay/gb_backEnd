package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 託購需求主檔。
 * 記錄發起人、商品、酬金、配送限制與託購狀態，供報價與訂單流程銜接。
 */
@Entity
@Table(
        name = "purchase_requests",
        indexes = {
                @Index(name = "idx_purchase_requests_status_expire", columnList = "status, request_expire_time"),
                @Index(name = "idx_purchase_requests_requester_status", columnList = "requester_id, status"),
                @Index(name = "idx_purchase_requests_city_status", columnList = "requester_city, status"),
                @Index(name = "idx_purchase_requests_reward_status", columnList = "reward_type, status"),
                @Index(name = "idx_purchase_requests_delivery_status", columnList = "delivery_method, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 發起託購需求的會員；Repository 會透過 requester.id 查詢我的發起列表。
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Lob
    @Column(name = "item_description", columnDefinition = "text")
    private String itemDescription;

    // 託購商品圖片網址清單，依陣列順序作為前台顯示排序。
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "json")
    private List<String> imageUrls = new ArrayList<>();

    // 酬金模式：FIXED 表示固定酬金，QUOTE 表示開放承接者報價。
    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    // 固定酬金金額；報價模式可為 null。
    @Column(name = "fixed_reward_amount")
    private Integer fixedRewardAmount;

    @Column(name = "requester_city", nullable = false, length = 50)
    private String requesterCity;

    @Column(name = "requester_district", length = 50)
    private String requesterDistrict;

    // 交付方式：FACE_TO_FACE、HOME_DELIVERY 或 STORE_TO_STORE。
    @Column(name = "delivery_method", nullable = false, length = 30)
    private String deliveryMethod;

    // 公開交付摘要，列表與詳情頁可直接顯示，不包含敏感地址。
    @Column(name = "delivery_public_summary", nullable = false, length = 255)
    private String deliveryPublicSummary;

    // 私密交付資訊，只有成立訂單後的相關雙方可查看。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_private_detail", columnDefinition = "json")
    private Map<String, Object> deliveryPrivateDetail;

    // 託購需求截止時間；排程會依此將未成立需求轉為過期。
    @Column(name = "request_expire_time", nullable = false)
    private LocalDateTime requestExpireTime;

    // 訂單成立後的交付期限，小時為單位。
    @Column(name = "delivery_deadline_hours", nullable = false)
    private Integer deliveryDeadlineHours;

    @Builder.Default
    @Column(name = "min_credit_score")
    private Integer minCreditScore = 0;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "OPEN";

    @Builder.Default
    @Column(name = "reopen_count")
    private Integer reopenCount = 0;

    @Column(name = "last_reopen_reason", length = 100)
    private String lastReopenReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
}
