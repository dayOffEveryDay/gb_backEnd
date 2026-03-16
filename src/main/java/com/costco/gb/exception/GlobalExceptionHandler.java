package com.costco.gb.exception;

import com.costco.gb.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice // 👈 宣告這是一個全域攔截器
public class GlobalExceptionHandler {

    /**
     * 1. 攔截我們自己寫的商業邏輯錯誤 (RuntimeException)
     * 例如：手腳太慢啦、沒有好市多會員卡、無法評價自己 等等
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {

        // 印出 Warning 等級的 Log，方便我們後端自己除錯
        log.warn("商業邏輯阻擋 [{}]: {}", request.getRequestURI(), ex.getMessage());

        // 包裝成漂漂亮亮的 JSON
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value()) // 400 Bad Request
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage()) // 👈 把我們寫的中文錯誤訊息塞進這裡
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 2. 攔截系統崩潰或其他未預期的錯誤 (Exception)
     * 例如：資料庫連線中斷、NullPointerException 空指標例外
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {

        // 這種真的是系統壞了，印出 Error 等級的 Log 並且把 StackTrace 印出來
        log.error("系統發生未預期錯誤 [{}]: ", request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value()) // 500 Internal Server Error
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("伺服器發生異常，請稍後再試或聯繫客服。") // 👈 絕對不要把真實的系統報錯丟給使用者看
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    /**
     * 3. 攔截 @Valid 參數驗證失敗的錯誤
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {

        // 收集所有欄位的錯誤訊息 (例如 {"quantity": "認購數量至少必須為 1"})
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        // 我們把這些欄位錯誤，整合成一個字串放在 message 裡讓前端好顯示
        String errorMessage = "參數格式錯誤: " + errors.values().toString();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(errorMessage)
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}