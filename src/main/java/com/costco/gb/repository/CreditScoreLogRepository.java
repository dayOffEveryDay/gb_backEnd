package com.costco.gb.repository;

import com.costco.gb.entity.CreditScoreLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditScoreLogRepository extends JpaRepository<CreditScoreLog, Long> {
    // 讓會員查詢自己的信用存摺，越新的在越上面
    Page<CreditScoreLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}