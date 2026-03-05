package com.costco.gb.dto.request;

import lombok.Data;

@Data
public class LineLoginRequest {
    private String code;
    // 為了安全驗證，Line 要求換 Token 時必須帶上與當初要求授權時一模一樣的 redirectUri
    private String redirectUri;
}