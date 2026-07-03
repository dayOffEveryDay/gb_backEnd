package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 託購成立後的交易訂單。
 * 保存雙方確認、處理進度、物流資料、取消歸責與異常處理狀態。
 */
@Entity
@Table(
        name = "purchase_orders",
        indexes = {
                @Index(name = "idx_purchase_orders_request", columnList = "request_id"),
                @Index(name = "idx_purchase_orders_requester_status", columnList = "requester_id, status"),
                @Index(name = "idx_purchase_orders_runner_status", columnList = "runner_id, status"),
                @Index(name = "idx_purchase_orders_status_created", columnList = "status, created_at"),
                @Index(name = "idx_purchase_orders_abnormal", columnList = "status, abnormal_reported_at"),
                @Index(name = "idx_purchase_orders_delivery_time", columnList = "status, shipped_at, delivered_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "runner_id", nullable = false)
    private User runner;

    // 固定酬金直接接單時沒有報價，因此可為 null。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_quote_id")
    private PurchaseQuote selectedQuote;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String status = "WAITING_CONFIRMATION";

    @Column(name = "requester_confirmed_at")
    private LocalDateTime requesterConfirmedAt;

    @Column(name = "runner_confirmed_at")
    private LocalDateTime runnerConfirmedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "logistics_provider", length = 50)
    private String logisticsProvider;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;

    // 訂單取消或異常事件的責任方；無責取消時為 null。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blame_user_id")
    private User blameUser;

    @Builder.Default
    @Column(name = "credit_penalty")
    private Integer creditPenalty = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abnormal_reporter_id")
    private User abnormalReporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "abnormal_reported_user_id")
    private User abnormalReportedUser;

    @Column(name = "abnormal_type", length = 100)
    private String abnormalType;

    @Column(name = "abnormal_reported_at")
    private LocalDateTime abnormalReportedAt;

    @Column(name = "abnormal_responded_at")
    private LocalDateTime abnormalRespondedAt;
}
