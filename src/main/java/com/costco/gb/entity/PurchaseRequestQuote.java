package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_request_quotes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_request_runner",
                columnNames = {"purchase_request_id", "runner_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestQuote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "runner_id", nullable = false)
    private User runner;

    @Column(name = "quote_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal quoteAmount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "PENDING";
}
