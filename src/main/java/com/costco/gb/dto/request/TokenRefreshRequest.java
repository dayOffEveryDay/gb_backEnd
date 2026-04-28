package com.costco.gb.dto.request;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    // 前端保存的 Refresh Token，用來換發新的 Access Token
    private String refreshToken;
}
