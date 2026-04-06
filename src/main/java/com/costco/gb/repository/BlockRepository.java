package com.costco.gb.repository;


import com.costco.gb.entity.Block;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    // 檢查 A 是否封鎖了 B (用在首頁過濾合購單，防呆)
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 撈出那筆封鎖紀錄 (解除封鎖時會用到)
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 找出所有我封鎖的人，並支援分頁
    Page<Block> findByBlockerId(Long blockerId, Pageable pageable);
}
