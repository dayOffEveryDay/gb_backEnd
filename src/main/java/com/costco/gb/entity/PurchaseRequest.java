package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "image_urls", length = 1000)
    private String imageUrls;

    @Column(name = "reward_type", nullable = false, length = 20)
    private String rewardType;

    @Column(name = "fixed_reward_amount", precision = 10, scale = 2)
    private BigDecimal fixedRewardAmount;

    @Column(name = "delivery_method", nullable = false, length = 30)
    private String deliveryMethod;

    @Column(name = "request_area", length = 100)
    private String requestArea;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Builder.Default
    @Column(name = "delivery_time_type", nullable = false, length = 20)
    private String deliveryTimeType = "DISCUSS";

    @Column(name = "delivery_time_note")
    private String deliveryTimeNote;

    @Column(name = "min_credit_score")
    private Integer minCreditScore;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_runner_id")
    private User assignedRunner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_quote_id")
    private PurchaseRequestQuote acceptedQuote;

    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @OneToMany(mappedBy = "purchaseRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<PurchaseRequestQuote> quotes = new ArrayList<>();
}
