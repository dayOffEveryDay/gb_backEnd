package com.costco.gb.service;

import com.costco.gb.dto.request.UpdateProfileRequest;
import com.costco.gb.dto.response.CampaignSummaryResponse;
//import com.costco.gb.dto.response.CreditLogResponse;
import com.costco.gb.dto.response.UserProfileResponse;
import com.costco.gb.entity.Campaign;
//import com.costco.gb.entity.CreditScoreLog;
import com.costco.gb.entity.User;
import com.costco.gb.mapper.CampaignMapper;
import com.costco.gb.repository.BlockRepository;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.CreditScoreLogRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
//    private final CreditScoreLogRepository creditScoreLogRepository;
    private final BlockRepository blockRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignMapper campaignMapper; // 🌟 注入專屬的轉換器

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

//    @Transactional(readOnly = true)
//    public Page<CreditLogResponse> getMyCreditLogs(Long userId, Pageable pageable) { // 👈 直接收 Pageable
//
//        // Repository 已經設定好依建立時間倒序，直接傳入 pageable 即可
//        Page<CreditScoreLog> logs = creditScoreLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
//
//        // 將 Entity 轉換成 DTO
//        return logs.map(log -> CreditLogResponse.builder()
//                .id(log.getId())
//                .scoreChange(log.getScoreChange())
//                .reason(log.getReason())
//                .campaignId(log.getCampaignId())
//                .createdAt(log.getCreatedAt())
//                .build());
//    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long currentUserId, Long targetUserId) {

        // 🛡️ 1. 黑名單防護：如果互拉黑名單，直接裝傻說找不到這個人！
        if (currentUserId != null && !currentUserId.equals(targetUserId)) {
            boolean isBlocked = blockRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId) ||
                    blockRepository.existsByBlockerIdAndBlockedId(targetUserId, currentUserId);
            if (isBlocked) {
                log.warn("User {} 試圖查看已封鎖/被封鎖的 User {} 檔案", currentUserId, targetUserId);
                throw new RuntimeException("查無此使用者"); // 避免激怒對方，統一口徑
            }
        }

        // 🔍 2. 撈取該使用者的基本資料
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("查無此使用者"));

        // 📦 3. 撈取他正在開的團，並轉換成大廳用的 DTO 格式
        List<Campaign> activeCampaigns = campaignRepository.findActiveCampaignsByHostId(targetUserId);

        // (假設你的 CampaignService 裡有一個 mapToSummaryResponse 可以把 Entity 轉成 DTO)
        List<CampaignSummaryResponse> campaignDtos = activeCampaigns.stream()
                .map(campaignMapper::toSummaryResponse)
                .toList();

        // 🚀 4. 組裝成最終的 Profile 回傳
        return UserProfileResponse.builder()
                .userId(targetUser.getId())
                .displayName(targetUser.getDisplayName())
                .avatarUrl(targetUser.getProfileImageUrl()) // 抓取大頭貼
                .creditScore(targetUser.getCreditScore())
                .totalHostedCount(targetUser.getTotalHostedCount())
                .totalJoinedCount(targetUser.getTotalJoinedCount())
                .joinDate(targetUser.getCreatedAt())
                .activeCampaigns(campaignDtos)
                .build();
    }

}