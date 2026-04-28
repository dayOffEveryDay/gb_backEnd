package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🌟 一個 User 可以有多個 Refresh Token (例如同時登入手機和電腦)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 這是給前端拿來換 Access Token 的那串亂碼
    @Column(nullable = false, unique = true)
    private String token;

    // 到期時間
    @Column(nullable = false)
    private Instant expiryDate;
}