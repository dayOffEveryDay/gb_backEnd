package com.costco.gb.service;

import com.costco.gb.dto.response.BlockedUserResponse;
import com.costco.gb.repository.UserFollowRepository;
import com.costco.gb.entity.Block;
import com.costco.gb.entity.User;
import com.costco.gb.repository.BlockRepository;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ParticipantRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final CampaignRepository campaignRepository;
    private final UserFollowRepository userFollowRepository;
    // 🌟 將某人加入黑名單
    @Transactional
    public void blockUser(Long currentUserId, Long targetUserId) {
        // 防呆 1：不能封鎖自己
        if (currentUserId.equals(targetUserId)) {
            throw new RuntimeException("您不能將自己加入黑名單！");
        }

        // 防呆 2：確認對方真的存在
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("找不到該名使用者！"));

        // 防呆 3：如果已經封鎖過了，就不重複做事
        if (blockRepository.existsByBlockerIdAndBlockedId(currentUserId, targetUserId)) {
            return;
        }

        User currentUser = userRepository.getReferenceById(currentUserId);

        Block newBlock = Block.builder()
                .blocker(currentUser)
                .blocked(targetUser)
                .build();

        blockRepository.save(newBlock);

        // 🌟 新增：強力防護網，直接斬斷雙方可能存在的追蹤關係！
        // 1. 如果對方原本有追蹤我，強制踢除粉絲
        userFollowRepository.deleteByFollowerIdAndHostId(targetUserId, currentUserId);

        // 2. 如果我原本有追蹤對方，自動取消追蹤
        userFollowRepository.deleteByFollowerIdAndHostId(currentUserId, targetUserId);

        log.info("User {} 已將 User {} 加入黑名單，並斬斷相關追蹤關係", currentUserId, targetUserId);
    }

    // 🌟 解除封鎖
    @Transactional
    public void unblockUser(Long currentUserId, Long targetUserId) {
        blockRepository.findByBlockerIdAndBlockedId(currentUserId, targetUserId)
                .ifPresent(block -> {
                    blockRepository.delete(block);
                    log.info("User {} 已解除對 User {} 的封鎖", currentUserId, targetUserId);
                });
    }
    // 🌟 輕量版：撈取我的黑名單
    @Transactional(readOnly = true)
    public Page<BlockedUserResponse> getMyBlocklist(Long currentUserId, int page, int size) {

        // 依據封鎖時間倒序排列 (越近封鎖的在越上面)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Block> blocks = blockRepository.findByBlockerId(currentUserId, pageable);

        return blocks.map(block -> {
            User blockedUser = block.getBlocked();

            return BlockedUserResponse.builder()
                    .userId(blockedUser.getId())
                    .displayName(blockedUser.getDisplayName())
                    .avatarUrl(blockedUser.getProfileImageUrl())
                    .blockedAt(block.getCreatedAt()) // 直接取 Block 紀錄的建立時間
                    .build();
        });
    }
}