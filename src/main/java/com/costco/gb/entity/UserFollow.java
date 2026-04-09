package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_follows", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"follower_id", "host_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserFollow {

    @Id // 🌟 對應你 SQL 的 `id` bigint NOT NULL AUTO_INCREMENT
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower; // 🌟 對應你 SQL 的 `follower_id`

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host; // 🌟 對應你 SQL 的 `host_id`

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt; // 🌟 對應你 SQL 的 `created_at`

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 🌟 對應你 SQL 的 `updated_at`
}