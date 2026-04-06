package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Campaign extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "scenario_type", nullable = false, length = 50)
    private String scenarioType;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(length = 1000)
    private String imageUrls;// 存放格式："img1.jpg,img2.jpg,img3.jpg"

    private Integer pricePerUnit;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer availableQuantity;

    @Column(name = "host_reserved_quantity", nullable = false)
    private Integer hostReservedQuantity;

    private String meetupLocation;

    private LocalDateTime meetupTime;

    private LocalDateTime expireTime;

    @Builder.Default
    @Column(length = 50)
    private String status = "OPEN";

    // 歸責機制
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blame_user_id")
    private User blameUser;

    private String cancelReason;

    @Builder.Default
    @Column(name = "allow_revision", nullable = false)
    private boolean allowRevision = false;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude // ⚠️ 防雷必加：防止 Lombok 產生 toString() 時引發無限迴圈
    @EqualsAndHashCode.Exclude
    private List<Participant> participants = new ArrayList<>();
}
