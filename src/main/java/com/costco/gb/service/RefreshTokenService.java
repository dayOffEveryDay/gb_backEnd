package com.costco.gb.service;

import com.costco.gb.entity.RefreshToken;
import com.costco.gb.entity.User;
import com.costco.gb.repository.RefreshTokenRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // 🌟 Refresh Token 壽命設定為 30 天 (以毫秒為單位)
    private final Long REFRESH_TOKEN_EXPIRATION = 2592000000L;

    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString()) // 產生安全的隨機字串
                .expiryDate(Instant.now().plusMillis(REFRESH_TOKEN_EXPIRATION))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        // 如果現在的時間已經超過了到期時間
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token); // 過期就從資料庫清掉
            throw new RuntimeException("Refresh token 已經過期，請重新登入");
        }
        return token;
    }

    // 🌟 新增這個方法：讓 Controller 可以透過 Service 去資料庫撈 Token
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }
    @Transactional
    public void deleteByUserId(Long userId) {
        userRepository.findById(userId).ifPresent(refreshTokenRepository::deleteByUser);
    }
}