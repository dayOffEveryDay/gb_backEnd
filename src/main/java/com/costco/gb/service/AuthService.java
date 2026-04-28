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
    private final RestTemplate restTemplate = new RestTemplate(); // 直接實例化即可使用
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
        // Step 1: 向 Line 換取 Access Token
        String lineAccessToken = getLineAccessToken(request.getCode(), request.getRedirectUri());

        // Step 2: 拿 Access Token 取得使用者 Profile (Line UID, 暱稱, 頭像)
        Map<String, Object> lineProfile = getLineUserProfile(lineAccessToken);
        String lineUid = (String) lineProfile.get("userId");
        String displayName = (String) lineProfile.get("displayName");
        String pictureUrl = (String) lineProfile.get("pictureUrl");

        // Step 3: 檢查資料庫是否有此使用者，沒有則註冊 (Upsert 邏輯)
        Optional<User> userOptional = userRepository.findByLineUid(lineUid);
        User user;
        boolean isNewUser = false;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            // 可以選擇在此時更新使用者的 Line 暱稱和頭像，保持最新狀態
            user.setDisplayName(displayName);
            user.setProfileImageUrl(pictureUrl);
            user = userRepository.save(user);
        } else {
            // 新使用者註冊
            isNewUser = true;
            user = User.builder()
                    .lineUid(lineUid)
                    .displayName(displayName)
                    .profileImageUrl(pictureUrl)
                    .hasCostcoMembership(false) // 預設無好市多會員
                    .build();
            user = userRepository.save(user);

            // 建立預設的 UserPreference (這一步很重要，否則未來查詢偏好會 NPE)
            UserPreference preference = UserPreference.builder()
                    .user(user)
                    .receiveNotifications(true)
                    .favoriteStoreIds(new ArrayList<>())
                    .preferredCategoryIds(new ArrayList<>())
                    .build();
            userPreferenceRepository.save(preference);
            log.info("New user registered with Line UID: {}", lineUid);
        }

        // Step 4: 核發我們系統專屬的 JWT Token
        String jwtToken = jwtService.generateToken(user.getId());
        // Step 4.2: 產生長命的 Refresh Token (存入資料庫)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        // Step 5: 組裝回傳格式給前端
        return AuthResponse.builder()
                .token(jwtToken)
                .refreshToken(refreshToken.getToken()) // 將長命的 Refresh Token 塞進去
                .isNewUser(isNewUser)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .hasCostcoMembership(user.getHasCostcoMembership())
                        .build())
                .build();
    }

    // --- 以下為呼叫 Line API 的私有輔助方法 ---

    private String getLineAccessToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Line API 規定要用 Form URL Encoded 格式
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