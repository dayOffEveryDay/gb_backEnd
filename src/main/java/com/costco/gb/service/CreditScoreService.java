package com.costco.gb.service;

import com.costco.gb.dto.response.CreditScoreLogResponse;
import com.costco.gb.entity.CreditScoreLog;
import com.costco.gb.entity.User;
import com.costco.gb.repository.CreditScoreLogRepository;
import com.costco.gb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditScoreService {

    private final CreditScoreLogRepository creditScoreLogRepository;
    private final UserRepository userRepository;

    // 🌟 1. 給前端查詢用的「信用存摺」
    @Transactional(readOnly = true)
    public Page<CreditScoreLogResponse> getMyCreditScoreLogs(Long userId, int page, int size) {
        // 因為 Repository 方法已經自帶 OrderByCreatedAtDesc，這裡不需額外寫 Sort
        Pageable pageable = PageRequest.of(page, size);

        Page<CreditScoreLog> logs = creditScoreLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return logs.map(log -> CreditScoreLogResponse.builder()
                .id(log.getId())
                .scoreChange(log.getScoreChange())
                .reason(log.getReason())
                .campaignId(log.getCampaignId())
                .purchaseRequestId(log.getPurchaseRequestId())
                .sourceType(resolveSourceType(log))
                .createdAt(log.getCreatedAt())
                .build());
    }
    // 🌟 2. 系統內部專用：記錄分數異動的「核心記帳」方法
    @Transactional
    public void recordScoreChange(Long userId, Integer scoreChange, String reason, Long campaignId) {
        recordScoreChange(userId, scoreChange, reason, campaignId, null);
    }

    @Transactional
    public void recordPurchaseRequestScoreChange(Long userId, Integer scoreChange, String reason, Long purchaseRequestId) {
        recordScoreChange(userId, scoreChange, reason, null, purchaseRequestId);
    }

    @Transactional
    public void recordScoreChange(Long userId, Integer scoreChange, String reason, Long campaignId, Long purchaseRequestId) {
        if (scoreChange == null || scoreChange == 0) {
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到該會員"));

        // 假設新註冊會員預設是 100 分 (滿血開局)
        int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
        int newScore = currentScore + scoreChange;

        // 🛡️ 實作「天花板」與「地板」機制
        if (newScore > 100) {
            newScore = 100; // 封頂 100 分
        } else if (newScore < 0) {
            newScore = 0;   // 扣到底就是 0 分 (未來可針對 0 分帳號做停權攔截)
        }

        // 判斷是否真的有分數異動 (例如原本 100 分，又加 5 分，結果還是 100)
        // 如果沒有實際變動，可以選擇不寫入 Log，或者照樣寫入(當作表揚)
        // 這裡示範：就算已經 100 分，還是給他寫入加分明細讓他開心！

        user.setCreditScore(newScore);
        userRepository.save(user);

        CreditScoreLog logEntry = CreditScoreLog.builder()
                .user(user)
                .scoreChange(scoreChange)
                .reason(reason)
                .campaignId(campaignId)
                .purchaseRequestId(purchaseRequestId)
                .build();

        creditScoreLogRepository.save(logEntry);

        log.info("會員 {} 信用分異動: {}. 結算: {}", userId, scoreChange, newScore);
    }

    private String resolveSourceType(CreditScoreLog log) {
        if (log.getPurchaseRequestId() != null) {
            return "PURCHASE_REQUEST";
        }
        if (log.getCampaignId() != null) {
            return "CAMPAIGN";
        }
        return "SYSTEM";
    }

}
