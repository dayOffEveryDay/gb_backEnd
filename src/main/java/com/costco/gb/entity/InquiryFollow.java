package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 使用者關注商品詢問問題紀錄表。 */
@Entity
@Table(name = "inquiry_follows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inquiry_follow", columnNames = {"inquiry_id", "user_id"})
}, indexes = {
        @Index(name = "idx_inquiry_follow_user", columnList = "user_id"),
        @Index(name = "idx_inquiry_follow_inquiry", columnList = "inquiry_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InquiryFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inquiry_follows_inquiry"))
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inquiry_follows_user"))
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
