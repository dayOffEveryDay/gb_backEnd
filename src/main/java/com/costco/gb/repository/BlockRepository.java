package com.costco.gb.repository;


import com.costco.gb.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    // 檢查 A 是否封鎖了 B (用在首頁過濾合購單，防呆)
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
