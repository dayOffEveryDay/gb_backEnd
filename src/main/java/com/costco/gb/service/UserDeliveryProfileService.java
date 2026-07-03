package com.costco.gb.service;

import com.costco.gb.dto.request.UserDeliveryProfileRequest;
import com.costco.gb.dto.response.UserDeliveryProfileResponse;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserDeliveryProfile;
import com.costco.gb.repository.UserDeliveryProfileRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDeliveryProfileService {

    private static final Set<String> PROFILE_TYPES = Set.of(
            "FACE_TO_FACE", "HOME_DELIVERY", "STORE_TO_STORE"
    );
    private static final Set<String> LOCATION_TYPES = Set.of(
            "TRANSIT_RANGE", "LANDMARK", "ROAD", "DISTRICT", "ADDRESS", "STORE", "CUSTOM"
    );

    private final UserDeliveryProfileRepository profileRepository;
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
        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public UserDeliveryProfileResponse update(Long userId, Long profileId, UserDeliveryProfileRequest request) {
        validateRequest(request);
        UserDeliveryProfile profile = getOwnedProfile(userId, profileId);
        applyRequest(profile, request);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public void delete(Long userId, Long profileId) {
        UserDeliveryProfile profile = getOwnedProfile(userId, profileId);
        profileRepository.delete(profile);
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
        String locationType = normalizeRequired(request.getLocationType(), "地點類型不可空白");
        validateProfileType(profileType);
        if (!LOCATION_TYPES.contains(locationType)) {
            throw new RuntimeException("不支援的地點類型: " + locationType);
        }
        if (request.getDisplayText() == null || request.getDisplayText().isBlank()) {
            throw new RuntimeException("顯示文字不可空白");
        }
    }

    private void validateProfileType(String profileType) {
        if (!PROFILE_TYPES.contains(profileType)) {
            throw new RuntimeException("不支援的交付資料類型: " + profileType);
        }
    }

    private void applyRequest(UserDeliveryProfile profile, UserDeliveryProfileRequest request) {
        profile.setProfileType(normalizeRequired(request.getProfileType(), "交付資料類型不可空白"));
        profile.setLocationType(normalizeRequired(request.getLocationType(), "地點類型不可空白"));
        profile.setLabel(request.getLabel());
        profile.setCity(request.getCity());
        profile.setDistrict(request.getDistrict());
        profile.setLocationName(request.getLocationName());
        profile.setRangeStart(request.getRangeStart());
        profile.setRangeEnd(request.getRangeEnd());
        profile.setDisplayText(request.getDisplayText().trim());
        profile.setDetail(request.getDetail());
        profile.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
    }

    private UserDeliveryProfileResponse toResponse(UserDeliveryProfile profile) {
        return UserDeliveryProfileResponse.builder()
                .id(profile.getId())
                .profileType(profile.getProfileType())
                .locationType(profile.getLocationType())
                .label(profile.getLabel())
                .city(profile.getCity())
                .district(profile.getDistrict())
                .locationName(profile.getLocationName())
                .rangeStart(profile.getRangeStart())
                .rangeEnd(profile.getRangeEnd())
                .displayText(profile.getDisplayText())
                .detail(profile.getDetail())
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
