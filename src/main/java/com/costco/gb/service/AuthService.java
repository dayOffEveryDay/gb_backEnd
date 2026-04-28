package com.costco.gb.service;

import com.costco.gb.dto.request.LineLoginRequest;
import com.costco.gb.dto.response.AuthResponse;
import com.costco.gb.entity.RefreshToken;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserPreference;
import com.costco.gb.repository.UserPreferenceRepository;
import com.costco.gb.repository.UserRepository;
import com.costco.gb.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final JwtService jwtService;
    private final RestTemplate restTemplate = new RestTemplate(); // 呼叫 LINE API 使用的 HTTP 客戶端
    private final RefreshTokenService refreshTokenService;

    @Value("${line.api.client-id}")
    private String channelId;

    @Value("${line.api.client-secret}")
    private String channelSecret;

    @Value("${line.api.token-uri}")
    private String tokenUri;

    @Value("${line.api.user-info-uri}")
    private String userInfoUri;

    @Transactional
    public AuthResponse lineLogin(LineLoginRequest request) {
        // Step 1: 使用 LINE 授權碼換取 Access Token
        String lineAccessToken = getLineAccessToken(request.getCode(), request.getRedirectUri());

        // Step 2: 使用 Access Token 取得使用者 Profile
        Map<String, Object> lineProfile = getLineUserProfile(lineAccessToken);
        String lineUid = (String) lineProfile.get("userId");
        String displayName = (String) lineProfile.get("displayName");
        String pictureUrl = (String) lineProfile.get("pictureUrl");

        // Step 3: 依 LINE UID 查詢既有會員，沒有資料時建立新會員
        Optional<User> userOptional = userRepository.findByLineUid(lineUid);
        User user;
        boolean isNewUser = false;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // 每次登入時同步 LINE 最新顯示名稱與頭像
            user.setDisplayName(displayName);
            user.setProfileImageUrl(pictureUrl);
            user = userRepository.save(user);
        } else {
            // 第一次登入時建立會員資料
            isNewUser = true;
            user = User.builder()
                    .lineUid(lineUid)
                    .displayName(displayName)
                    .profileImageUrl(pictureUrl)
                    .hasCostcoMembership(false) // 預設尚未綁定 Costco 會員
                    .build();
            user = userRepository.save(user);

            // 同步建立使用者偏好設定，避免後續查詢偏好時發生空值
            UserPreference preference = UserPreference.builder()
                    .user(user)
                    .receiveNotifications(true)
                    .favoriteStoreIds(new ArrayList<>())
                    .preferredCategoryIds(new ArrayList<>())
                    .build();
            userPreferenceRepository.save(preference);
            log.info("New user registered with Line UID: {}", lineUid);
        }

        // Step 4: 產生短效 JWT Access Token
        String jwtToken = jwtService.generateToken(user.getId());
        // Step 4.2: 產生長效 Refresh Token 並保存到資料庫
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        // Step 5: 回傳登入憑證與使用者基本資料
        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken.getToken()) // 前端用於換發新的 Access Token
                .isNewUser(isNewUser)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .hasCostcoMembership(user.getHasCostcoMembership())
                        .build())
                .build();
    }

    // --- LINE API 呼叫輔助方法 ---

    private String getLineAccessToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // LINE token API 要求使用 Form URL Encoded 格式
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", code);
        map.add("redirect_uri", redirectUri);
        map.add("client_id", channelId);
        map.add("client_secret", channelSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUri, request, Map.class);
            return (String) response.getBody().get("access_token");
        } catch (Exception e) {
            log.error("Failed to get Line Access Token: {}", e.getMessage());
            throw new RuntimeException("Line login failed at token exchange.");
        }
    }

    private Map<String, Object> getLineUserProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> request = new HttpEntity<>("parameters", headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, request, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get Line User Profile: {}", e.getMessage());
            throw new RuntimeException("Line login failed at fetching profile.");
        }
    }
}
