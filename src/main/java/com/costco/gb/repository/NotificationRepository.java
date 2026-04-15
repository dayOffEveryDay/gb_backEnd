package com.costco.gb.repository;

import com.costco.gb.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 取得使用者個人的通知清單 (分頁，最新的在前面)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // 取得未讀通知數量 (做前端小鈴鐺紅點數字)
    int countByUserIdAndIsReadFalse(Long userId);

    // 系統排程用：刪除過期超過 7 天的通知
    void deleteByExpireTimeBefore(LocalDateTime cutoffDate);

    // 🌟 使用 Pageable 進行分頁，已讀訊息
    Page<Notification> findByUserIdAndIsReadTrueOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);
}
