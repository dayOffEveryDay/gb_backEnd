package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 託購報價單。
 * 同一位報價者對同一委託僅能保留一筆報價紀錄。
 */
@Entity
@Table(
        name = "purchase_quotes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_quotes_request_quoter",
                columnNames = {"request_id", "quoter_id"}
        ),
        indexes = {
                @Index(name = "idx_purchase_quotes_request_status", columnList = "request_id, status"),
                @Index(name = "idx_purchase_quotes_quoter_status", columnList = "quoter_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseQuote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quoter_id", nullable = false)
    private User quoter;

    @Column(name = "quote_amount", nullable = false)
    private Integer quoteAmount;

    @Column(name = "quote_note", length = 500)
    private String quoteNote;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "selected_at")
    private LocalDateTime selectedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;
}
