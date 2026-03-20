package com.costco.gb.scheduler;
import com.costco.gb.entity.Campaign;
import com.costco.gb.repository.CampaignRepository;
import com.costco.gb.repository.ParticipantRepository;
import com.costco.gb.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import net.coobird.thumbnailator.Thumbnails; // 記得 import
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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


    @Transactional
    @Scheduled(cron = "0 0 3 * * *") // 每天凌晨 3 點執行
    public void compressOldCampaignImages() {
        log.info("🗜️ 啟動瘦身排程：開始將 3 個月前的合購單圖片壓縮為 100x100 縮圖...");

        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusDays(90);
        List<Campaign> oldCampaigns = campaignRepository.findOldCampaignsForImageCompression(threeMonthsAgo);

        if (oldCampaigns.isEmpty()) {
            return;
        }

        for (Campaign campaign : oldCampaigns) {
            String imageUrls = campaign.getImageUrls();
            List<String> newThumbNames = new ArrayList<>();

            if (imageUrls != null && !imageUrls.isEmpty()) {
                String[] images = imageUrls.split(",");

                for (String imgName : images) {
                    File originalFile = new File("uploads/campaigns/" + imgName);

                    if (originalFile.exists()) {
                        try {
                            // 產生縮圖的新檔名 (例如 uuid.jpg 變成 uuid_thumb.jpg)
                            String extension = imgName.substring(imgName.lastIndexOf("."));
                            String nameWithoutExt = imgName.substring(0, imgName.lastIndexOf("."));
                            String thumbName = nameWithoutExt + "_thumb" + extension;

                            File thumbFile = new File("uploads/campaigns/" + thumbName);

                            // 🌟 核心魔法：執行壓縮
                            Thumbnails.of(originalFile)
                                    .size(100, 100)        // 限制最大長寬為 100x100 (會自動保持比例，不變形)
                                    .outputQuality(0.8)    // 畫質設定為 80%，極度省空間
                                    .toFile(thumbFile);

                            // 壓縮成功後，把肥大的原圖物理刪除
                            originalFile.delete();

                            // 紀錄新的檔名
                            newThumbNames.add(thumbName);

                        } catch (Exception e) {
                            log.error("壓縮圖片 {} 失敗: {}", imgName, e.getMessage());
                            newThumbNames.add(imgName); // 萬一壓縮失敗，保留原本的名字避免資料庫錯亂
                        }
                    } else {
                        newThumbNames.add(imgName); // 檔案本來就不存在的話，原樣保留
                    }
                }
            }
            // 將資料庫的 imageUrls 更新為縮圖的檔名
            campaign.setImageUrls(String.join(",", newThumbNames));
            log.info("已成功將合購單 {} 的圖片轉換為 100x100 縮圖並釋放空間", campaign.getId());
        }

        campaignRepository.saveAll(oldCampaigns);
    }
}