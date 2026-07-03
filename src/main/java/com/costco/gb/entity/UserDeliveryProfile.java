package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 使用者常用交付資料。
 * 保存面交、宅配或店到店的常用地點，供建立託購委託時選用。
 */
@Entity
@Table(
        name = "user_delivery_profiles",
        indexes = {
                @Index(name = "idx_user_delivery_profiles_user_type", columnList = "user_id, profile_type"),
                @Index(name = "idx_user_delivery_profiles_city_type", columnList = "city, location_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeliveryProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 此交付資料所屬的使用者。
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 交付方式，例如 FACE_TO_FACE、HOME_DELIVERY 或 STORE_TO_STORE。
    @Column(name = "profile_type", nullable = false, length = 30)
    private String profileType;

    // 地點資料類型，例如 TRANSIT_RANGE、LANDMARK、ADDRESS 或 STORE。
    @Column(name = "location_type", length = 30)
    private String locationType;

    // 使用者自訂辨識名稱，例如「家裡」或「公司」。
    @Column(length = 100)
    private String label;

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

    // 可直接提供前端顯示的交付地點摘要。
    @Column(name = "display_text", nullable = false, length = 255)
    private String displayText;

    // 保存不同地點類型的完整結構化資料。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detail;

    @Builder.Default
    @Column(name = "is_default")
    private Boolean isDefault = false;
}
