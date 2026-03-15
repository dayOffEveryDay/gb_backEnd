package com.costco.gb.scheduler;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService; // 注入 Service

    /*
     * TODO: [架構優化]
     * 目前使用 @Scheduled 每分鐘輪詢資料庫檢查過期合購單。
     * 未來若系統流量與並發量提升，為減輕資料庫掃描負擔 (Full Table Scan / Index Scan)，
     * 應評估改用「Redis 鍵值過期事件 (Keyspace Notifications)」
     * 或「Message Queue 延遲佇列 (Delay Queue, 如 RabbitMQ / AWS SQS)」
     * 來實現事件驅動 (Event-Driven) 的狀態變更。
     */
    @Scheduled(cron = "0 * * * * *") // 每分鐘的第 0 秒執行一次
    @Transactional
    public void closeExpiredCampaigns() {
        LocalDateTime now = LocalDateTime.now();

        // 直接呼叫 Repository 的批次更新方法
        int updatedCount = campaignRepository.updateExpiredCampaignsStatus(now);

        if (updatedCount > 0) {
            log.info("清道夫任務執行完畢：已將 {} 筆過期的合購單狀態更新為 FAILED", updatedCount);
        }
    }
    // 新增：每小時的第 30 分鐘去巡邏一次有沒有幽靈單
    @Scheduled(cron = "0 30 * * * *")
    public void huntGhostedCampaigns() {
        log.info("👻 啟動幽靈獵人排程：開始掃描團主放鳥的合購單...");
        campaignService.processGhostedCampaigns();
    }
}