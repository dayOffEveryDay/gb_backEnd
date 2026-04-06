package com.costco.gb.controller;

import com.costco.gb.dto.request.UpdateProfileRequest;
import com.costco.gb.dto.response.BlockedUserResponse;
import com.costco.gb.dto.response.UserProfileResponse;
import com.costco.gb.service.BlockService;
import com.costco.gb.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BlockService blockService;

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

    // 🌟 將特定使用者加入黑名單
    @PostMapping("/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable("id") Long targetUserId) {

        // ✨ 統一用 Spring Security Context 抓取當前登入者 ID
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        blockService.blockUser(currentUserId, targetUserId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已成功將該使用者加入黑名單。您將不會再看到對方發起的合購單。"
        ));
    }

    // 🌟 解除黑名單
    @DeleteMapping("/{id}/block")
    public ResponseEntity<?> unblockUser(@PathVariable("id") Long targetUserId) {

        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        blockService.unblockUser(currentUserId, targetUserId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已解除封鎖。"
        ));
    }

    // 🌟 查看特定使用者的個人檔案與開團紀錄
    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable("id") Long targetUserId) {

        // ✨ 安全解析當前登入者 ID (支援訪客模式查看，有登入就做黑名單檢查)
        Long currentUserId = null;
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof String &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            currentUserId = Long.parseLong((String) authentication.getPrincipal());
        }

        UserProfileResponse response = userService.getUserProfile(currentUserId, targetUserId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/blocks")
    public ResponseEntity<Page<BlockedUserResponse>> getMyBlocklist(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long currentUserId = Long.parseLong(userIdStr);

        Page<BlockedUserResponse> response = blockService.getMyBlocklist(currentUserId, page, size);

        return ResponseEntity.ok(response);
    }

}