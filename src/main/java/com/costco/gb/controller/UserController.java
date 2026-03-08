package com.costco.gb.controller;

import com.costco.gb.dto.request.UpdateProfileRequest;
import com.costco.gb.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {

        // ✨ 神奇的地方在這裡：直接從我們寫好的 JwtAuthenticationFilter 中拿出解析好的 userId
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = Long.parseLong(userIdStr);

        // 呼叫 Service 執行更新
        userService.updateProfile(userId, request);

        // 回傳成功訊息
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "個人資料更新成功"
        ));
    }
}