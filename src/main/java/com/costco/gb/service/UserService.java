package com.costco.gb.service;

import com.costco.gb.dto.request.UpdateProfileRequest;
import com.costco.gb.dto.response.CreditLogResponse;
import com.costco.gb.entity.CreditScoreLog;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CreditScoreLogRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CreditScoreLogRepository creditScoreLogRepository;

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        // 1. 去資料庫把這個人撈出來
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到該使用者"));

        // 2. 如果前端有傳入新的值，才進行更新
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getHasCostcoMembership() != null) {
            user.setHasCostcoMembership(request.getHasCostcoMembership());
        }

        // 3. 儲存進資料庫 (其實有 @Transactional 的加持，Hibernate 的 Dirty Checking 也會自動幫你 UPDATE)
        userRepository.save(user);

        log.info("User {} updated profile. hasCostcoMembership: {}", userId, request.getHasCostcoMembership());
    }

    @Transactional(readOnly = true)
    public Page<CreditLogResponse> getMyCreditLogs(Long userId, Pageable pageable) { // 👈 直接收 Pageable

        // Repository 已經設定好依建立時間倒序，直接傳入 pageable 即可
        Page<CreditScoreLog> logs = creditScoreLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        // 將 Entity 轉換成 DTO
        return logs.map(log -> CreditLogResponse.builder()
                .id(log.getId())
                .scoreChange(log.getScoreChange())
                .reason(log.getReason())
                .campaignId(log.getCampaignId())
                .createdAt(log.getCreatedAt())
                .build());
    }
}