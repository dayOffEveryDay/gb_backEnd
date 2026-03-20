package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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
}