package com.costco.gb.dto.request;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}