package com.costco.gb.controller;

import com.costco.gb.dto.request.LineLoginRequest;
import com.costco.gb.dto.response.AuthResponse;
import com.costco.gb.security.JwtService;
import com.costco.gb.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/line")
    public ResponseEntity<AuthResponse> lineLogin(@RequestBody LineLoginRequest request) {
        // 呼叫 Service 處理所有邏輯
        AuthResponse response = authService.lineLogin(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ⚠️ 警告：這是開發者專用的後門 API，正式上線前請務必刪除或用 @Profile("dev") 限制！
     */
//    @GetMapping("/dev-login")
//    public ResponseEntity<Map<String, String>> devLogin(@RequestParam(defaultValue = "1") Long userId) {
//        // 直接無腦印鈔票 (發放 Token)
//        String token = jwtService.generateToken(userId);
//
//        return ResponseEntity.ok(Map.of(
//                "message", "開發者後門登入成功",
//                "userId", String.valueOf(userId),
//                "token", token
//        ));
//    }
}