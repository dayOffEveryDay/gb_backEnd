package com.costco.gb.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private boolean isNewUser;
    // 用於換發 Access Token 的長效憑證
    private String refreshToken;
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
