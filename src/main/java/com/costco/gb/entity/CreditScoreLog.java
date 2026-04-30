package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_score_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 誰的分數被異動了？
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 異動了多少分？ (例如：+5, -2, -10)
    @Column(nullable = false)
    private Integer scoreChange;

    // 異動原因 (例如："發起合購並成功面交", "主動取消已成團合購")
    @Column(nullable = false, length = 100)
    private String reason;

    // 關聯的合購單 ID (可選，如果是因為某張單被扣分，就可以點擊連過去看)
    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;

    // 異動時間
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
