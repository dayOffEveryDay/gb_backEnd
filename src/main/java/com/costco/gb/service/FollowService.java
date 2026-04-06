package com.costco.gb.service;

import com.costco.gb.dto.response.FollowingUserResponse;
import com.costco.gb.entity.User;
import com.costco.gb.entity.UserFollow;
import com.costco.gb.repository.BlockRepository;
import com.costco.gb.repository.UserFollowRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository; // 用來檢查封鎖狀態

    // 🌟 1. 追蹤
    @Transactional
    public void follow(Long followerId, Long hostId) {
        if (followerId.equals(hostId)) {
            throw new RuntimeException("無法追蹤自己");
        }

        // 🛡️ 防呆：檢查是否已經追蹤
        if (userFollowRepository.existsByFollowerIdAndHostId(followerId, hostId)) {
            throw new RuntimeException("您已經追蹤過此用戶了");
        }

        // 🛡️ 防護機制：檢查雙方是否有封鎖關係 (只要有任一方封鎖，就不准追蹤)
        if (blockRepository.existsByBlockerIdAndBlockedId(followerId, hostId) ||
                blockRepository.existsByBlockerIdAndBlockedId(hostId, followerId)) {
            throw new RuntimeException("無法追蹤此用戶 (存在封鎖關係)");
        }

        User follower = userRepository.findById(followerId).orElseThrow();
        User host = userRepository.findById(hostId).orElseThrow();

        UserFollow follow = UserFollow.builder()
                .follower(follower)
                .host(host)
                .build();

        userFollowRepository.save(follow);
    }

    // 🌟 2. 退追蹤
    @Transactional
    public void unfollow(Long followerId, Long hostId) {
        UserFollow follow = userFollowRepository.findByFollowerIdAndHostId(followerId, hostId)
                .orElseThrow(() -> new RuntimeException("您並未追蹤此用戶"));

        userFollowRepository.delete(follow);
    }

    // 🌟 3. 我的追蹤列表
    @Transactional(readOnly = true)
    public Page<FollowingUserResponse> getMyFollowingList(Long followerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<UserFollow> follows = userFollowRepository.findByFollowerId(followerId, pageable);

        return follows.map(follow -> {
            User host = follow.getHost();
            return FollowingUserResponse.builder()
                    .hostId(host.getId())
                    .displayName(host.getDisplayName())
                    .avatarUrl(host.getProfileImageUrl())
                    .followedAt(follow.getCreatedAt())
                    .build();
        });
    }
}