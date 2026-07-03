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
 * 託購委託單。
 * 保存公開商品需求、酬金方式、交付摘要與委託期限。
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

    // 發布託購需求的委託人。
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Lob
    @Column(name = "item_description", columnDefinition = "text")
    private String itemDescription;

    // 商品圖片網址清單，以 JSON 陣列保存。
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "json")
    private List<String> imageUrls = new ArrayList<>();

    // 酬金模式：FIXED 或 QUOTE。
    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    // 固定酬金模式使用；報價模式可為 null。
    @Column(name = "fixed_reward_amount")
    private Integer fixedRewardAmount;

    @Column(name = "requester_city", nullable = false, length = 50)
    private String requesterCity;

    @Column(name = "requester_district", length = 50)
    private String requesterDistrict;

    // 交付方式：FACE_TO_FACE、HOME_DELIVERY 或 STORE_TO_STORE。
    @Column(name = "delivery_method", nullable = false, length = 30)
    private String deliveryMethod;

    // 成立前可公開顯示的交付地點摘要。
    @Column(name = "delivery_public_summary", nullable = false, length = 255)
    private String deliveryPublicSummary;

    // 成立後才可提供給交易雙方的地址、電話或店到店資料。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_private_detail", columnDefinition = "json")
    private Map<String, Object> deliveryPrivateDetail;

    // 未成立委託超過此時間後應轉為過期。
    @Column(name = "request_expire_time", nullable = false)
    private LocalDateTime requestExpireTime;

    // 訂單成立後允許完成交貨的時數。
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
