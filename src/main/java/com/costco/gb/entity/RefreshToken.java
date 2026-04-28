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

    // 一位使用者可以擁有多筆 Refresh Token，支援多裝置登入
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 前端用來換發 Access Token 的長效憑證
    @Column(nullable = false, unique = true)
    private String token;

    // token 到期時間，用於刷新前驗證是否仍有效
    @Column(nullable = false)
    private Instant expiryDate;
}
