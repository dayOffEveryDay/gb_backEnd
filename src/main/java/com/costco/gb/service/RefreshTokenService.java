package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.entity.RefreshToken;
import com.costco.gb.entity.User;
import com.costco.gb.repository.RefreshTokenRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // Refresh Token 有效期限：30 天，單位為毫秒
    private final Long REFRESH_TOKEN_EXPIRATION = 2592000000L;

    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString()) // 使用 UUID 產生不可預測的 token 字串
                .expiryDate(Instant.now().plusMillis(REFRESH_TOKEN_EXPIRATION))
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        BusinessLog.debug(log, "event=REFRESH_TOKEN_CREATED userId={} tokenRecordId={} expiresAt={}", userId, saved.getId(), saved.getExpiryDate());
        return saved;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        // Refresh Token 過期時立即刪除，避免之後再次被使用
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            log.warn("event=TOKEN_REFRESH_REJECTED userId={} reasonCode=EXPIRED", token.getUser().getId());
            throw new RuntimeException("Refresh token 已過期，請重新登入");
        }
        return token;
    }

    // 依 token 字串查詢資料庫中的 Refresh Token
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            BusinessLog.info(log, "event=REFRESH_TOKENS_REVOKED userId={}", userId);
        });
    }
}
