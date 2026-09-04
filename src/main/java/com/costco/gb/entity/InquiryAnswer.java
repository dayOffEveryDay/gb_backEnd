package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 商品詢問回答表。 */
@Entity
@Table(name = "inquiry_answers", indexes = {
        @Index(name = "idx_answer_inquiry", columnList = "inquiry_id"),
        @Index(name = "idx_answer_user", columnList = "user_id"),
        @Index(name = "idx_answer_store", columnList = "store_id"),
        @Index(name = "idx_answer_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InquiryAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inquiry_answers_inquiry"))
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inquiry_answers_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", foreignKey = @ForeignKey(name = "fk_inquiry_answers_store"))
    private Store store;

    // 庫存問題的回報狀態；心得問題可為 null。
    @Column(name = "stock_status", length = 50)
    private String stockStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
