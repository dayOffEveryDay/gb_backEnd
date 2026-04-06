package com.costco.gb.repository;

import com.costco.gb.entity.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    // 檢查是否已追蹤
    boolean existsByFollowerIdAndHostId(Long followerId, Long hostId);

    // 找特定追蹤紀錄 (退追時用)
    Optional<UserFollow> findByFollowerIdAndHostId(Long followerId, Long hostId);

    // 撈取我的追蹤列表
    Page<UserFollow> findByFollowerId(Long followerId, Pageable pageable);

    // 🌟 關鍵：當發生封鎖時，用這個方法直接斬斷關聯！
    void deleteByFollowerIdAndHostId(Long followerId, Long hostId);
}