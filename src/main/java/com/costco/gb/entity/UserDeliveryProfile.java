package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

/** 使用者常用交付資料，選用後會複製成託購單自己的快照。 */
@Entity
@Table(name = "user_delivery_profiles", indexes = {
        @Index(name = "idx_user_delivery_profiles_user_type", columnList = "user_id, profile_type"),
        @Index(name = "idx_user_delivery_profiles_area", columnList = "city, district"),
        @Index(name = "idx_user_delivery_profiles_provider_location", columnList = "provider, location_code")
})
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime")),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false, columnDefinition = "datetime"))
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserDeliveryProfile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_delivery_profiles_user"))
    private User user;

    // FACE_TO_FACE、STORE_TO_STORE、HOME_DELIVERY、IBOX。
    @Column(name = "profile_type", nullable = false, length = 30)
    private String profileType;

    @Column(length = 100)
    private String label;

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

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
