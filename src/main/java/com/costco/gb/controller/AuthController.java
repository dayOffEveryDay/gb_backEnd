package com.costco.gb.controller;

import com.costco.gb.dto.request.LineLoginRequest;
import com.costco.gb.dto.request.TokenRefreshRequest;
import com.costco.gb.dto.response.AuthResponse;
import com.costco.gb.entity.RefreshToken;
import com.costco.gb.security.JwtService;
import com.costco.gb.service.AuthService;
import com.costco.gb.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/line")
    public ResponseEntity<AuthResponse> lineLogin(@RequestBody LineLoginRequest request) {
        // LINE 登入成功後會同時回傳 Access Token 與 Refresh Token
        AuthResponse response = authService.lineLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest request) {
        // 前端帶 Refresh Token 來換發新的短效 Access Token
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String newAccessToken = jwtService.generateToken(user.getId());
                    return ResponseEntity.ok(Map.of(
                            "token", newAccessToken,
                            "refreshToken", requestRefreshToken
                    ));
                })
                .orElseThrow(() -> new RuntimeException("Invalid refresh token."));
    }

    @GetMapping("/dev-login")
    public ResponseEntity<Map<String, String>> devLogin(@RequestParam(defaultValue = "1") Long userId) {
        // 開發環境用登入入口，方便直接取得測試用 token
        String token = jwtService.generateToken(userId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userId);

        return ResponseEntity.ok(Map.of(
                "message", "Developer login successful",
                "userId", String.valueOf(userId),
                "token", token,
                "refreshToken", refreshToken.getToken()
        ));
    }
}
