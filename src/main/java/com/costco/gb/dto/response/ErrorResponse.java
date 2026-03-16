package com.costco.gb.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp;  // 發生時間
    private int status;               // HTTP 狀態碼 (如 400, 404, 500)
    private String error;             // 錯誤主旨 (如 Bad Request)
    private String message;           // 給使用者看的白話文訊息
    private String path;              // 發生錯誤的 API 路徑
}