package com.costco.gb.service;

import com.costco.gb.logging.BusinessLog;
import com.costco.gb.dto.request.UserDeliveryProfileRequest;
import com.costco.gb.dto.response.UserDeliveryProfileResponse;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserDeliveryProfile;
import com.costco.gb.repository.PurchaseRequestDeliveryDetailRepository;
import com.costco.gb.repository.UserDeliveryProfileRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeliveryProfileService {

    private static final Set<String> PROFILE_TYPES = Set.of(
            "FACE_TO_FACE", "HOME_DELIVERY", "STORE_TO_STORE", "IBOX"
    );
    private final UserDeliveryProfileRepository profileRepository;
    private final PurchaseRequestDeliveryDetailRepository deliveryDetailRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserDeliveryProfileResponse> getMyProfiles(Long userId, String profileType) {
        String normalizedType = normalizeOptional(profileType);
        if (normalizedType != null) {
            validateProfileType(normalizedType);
        }

        List<UserDeliveryProfile> profiles = normalizedType == null
                ? profileRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                : profileRepository.findByUserIdAndProfileTypeOrderByIsDefaultDescCreatedAtDesc(userId, normalizedType);
        return profiles.stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserDeliveryProfileResponse create(Long userId, UserDeliveryProfileRequest request) {
        validateRequest(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到目前登入使用者"));

        UserDeliveryProfile profile = new UserDeliveryProfile();
        profile.setUser(user);
        applyRequest(profile, request);
        UserDeliveryProfile saved = profileRepository.save(profile);
        BusinessLog.info(log, "event=USER_DELIVERY_PROFILE_CREATED actorId={} profileId={} profileType={}", userId, saved.getId(), saved.getProfileType());
        return toResponse(saved);
    }

    @Transactional
    public UserDeliveryProfileResponse update(Long userId, Long profileId, UserDeliveryProfileRequest request) {
        validateRequest(request);
        UserDeliveryProfile profile = getOwnedProfile(userId, profileId);
        applyRequest(profile, request);
        UserDeliveryProfile saved = profileRepository.save(profile);
        BusinessLog.info(log, "event=USER_DELIVERY_PROFILE_UPDATED actorId={} profileId={} profileType={}", userId, profileId, saved.getProfileType());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        UserDeliveryProfile profile = getOwnedProfile(userId, profileId);
        // 保留託購快照內容，只清除來源追蹤關聯，行為與資料庫 ON DELETE SET NULL 一致。
        deliveryDetailRepository.clearSourceProfile(profileId);
        profileRepository.delete(profile);
        BusinessLog.info(log, "event=USER_DELIVERY_PROFILE_DELETED actorId={} profileId={} profileType={}", userId, profileId, profile.getProfileType());
    }

    private UserDeliveryProfile getOwnedProfile(Long userId, Long profileId) {
        UserDeliveryProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("找不到常用交付資料"));
        if (!profile.getUser().getId().equals(userId)) {
            throw new RuntimeException("無權操作此常用交付資料");
        }
        return profile;
    }

    private void validateRequest(UserDeliveryProfileRequest request) {
        String profileType = normalizeRequired(request.getProfileType(), "交付資料類型不可空白");
        validateProfileType(profileType);
    }

    private void validateProfileType(String profileType) {
        if (!PROFILE_TYPES.contains(profileType)) {
            throw new RuntimeException("不支援的交付資料類型: " + profileType);
        }
    }

    private void applyRequest(UserDeliveryProfile profile, UserDeliveryProfileRequest request) {
        profile.setProfileType(normalizeRequired(request.getProfileType(), "交付資料類型不可空白"));
        profile.setLabel(request.getLabel());
        profile.setCity(request.getCity());
        profile.setDistrict(request.getDistrict());
        profile.setLocationName(request.getLocationName());
        profile.setLocationCode(request.getLocationCode());
        profile.setProvider(request.getProvider());
        profile.setAddress(request.getAddress());
        profile.setRecipientName(request.getRecipientName());
        profile.setRecipientPhone(request.getRecipientPhone());
        profile.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
    }

    private UserDeliveryProfileResponse toResponse(UserDeliveryProfile profile) {
        return UserDeliveryProfileResponse.builder()
                .id(profile.getId())
                .profileType(profile.getProfileType())
                .label(profile.getLabel())
                .city(profile.getCity())
                .district(profile.getDistrict())
                .locationName(profile.getLocationName())
                .locationCode(profile.getLocationCode())
                .provider(profile.getProvider())
                .address(profile.getAddress())
                .recipientName(profile.getRecipientName())
                .recipientPhone(profile.getRecipientPhone())
                .isDefault(profile.getIsDefault())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }
}
