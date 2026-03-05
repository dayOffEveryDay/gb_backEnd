package com.costco.gb.controller;

import com.costco.gb.dto.request.LineLoginRequest;
import com.costco.gb.dto.response.AuthResponse;
import com.costco.gb.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/line")
    public ResponseEntity<AuthResponse> lineLogin(@RequestBody LineLoginRequest request) {
        // 呼叫 Service 處理所有邏輯
        AuthResponse response = authService.lineLogin(request);
        return ResponseEntity.ok(response);
    }
}