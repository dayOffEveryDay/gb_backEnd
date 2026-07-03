package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 託購委託的面交範圍快照。
 * 即使來源常用交付資料日後被修改，本表仍保留建立委託當下的內容。
 */
@Entity
@Table(
        name = "purchase_request_meetup_ranges",
        indexes = {
                @Index(name = "idx_purchase_request_meetup_ranges_request", columnList = "request_id"),
                @Index(name = "idx_purchase_request_meetup_ranges_city_type", columnList = "city, location_type"),
                @Index(name = "idx_purchase_request_meetup_ranges_location", columnList = "location_name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestMeetupRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    // 使用者手動輸入時沒有來源常用交付資料，因此可為 null。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_profile_id")
    private UserDeliveryProfile sourceProfile;

    @Column(name = "location_type", nullable = false, length = 30)
    private String locationType;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String district;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(name = "range_start", length = 100)
    private String rangeStart;

    @Column(name = "range_end", length = 100)
    private String rangeEnd;

    @Column(name = "display_text", nullable = false, length = 255)
    private String displayText;

    // 保存建立委託時的完整地點資料快照。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detail;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
