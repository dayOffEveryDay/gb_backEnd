package com.costco.gb.controller;

// ... imports ...

import com.costco.gb.dto.response.FollowingUserResponse;
import com.costco.gb.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // 追蹤
    @PostMapping("/{hostId}")
    public ResponseEntity<?> followHost(@PathVariable Long hostId) {
        Long currentUserId = Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        followService.follow(currentUserId, hostId);
        return ResponseEntity.ok(Map.of("success", true, "message", "已成功追蹤"));
    }

    // 退追蹤
    @DeleteMapping("/{hostId}")
    public ResponseEntity<?> unfollowHost(@PathVariable Long hostId) {
        Long currentUserId = Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        followService.unfollow(currentUserId, hostId);
        return ResponseEntity.ok(Map.of("success", true, "message", "已取消追蹤"));
    }

    // 我的追蹤列表
    @GetMapping("/me")
    public ResponseEntity<Page<FollowingUserResponse>> getMyFollowingList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long currentUserId = Long.parseLong((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        return ResponseEntity.ok(followService.getMyFollowingList(currentUserId, page, size));
    }
}