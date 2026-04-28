package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private boolean isNewUser;
    private String refreshToken;  // 🌟 新增這行：這是長命的 Refresh Token
    private UserDto user;

    @Data
    @Builder
    public static class UserDto {
        private Long id;
        private String displayName;
        private String profileImageUrl;
        private Boolean hasCostcoMembership;
    }
}