package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_reviews_order_reviewer_reviewee",
                columnNames = {"order_id", "reviewer_id", "reviewee_id"}),
        indexes = @Index(name = "idx_purchase_reviews_reviewee_time", columnList = "reviewee_id, created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;
    @Column(nullable = false) private Integer rating;
    @Column(length = 255) private String comment;
    @Builder.Default @Column(name = "is_score_counted", nullable = false) private Boolean isScoreCounted = false;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
