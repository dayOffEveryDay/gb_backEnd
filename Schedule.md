# Schedule.md

## 最新排程備註

- `huntGhostedCampaigns` 只處理排程判定的 `HOST_NO_SHOW` 團主放鳥情境。
- 在 `HOST_NO_SHOW` 流程中，團主信用分會扣 `10` 分，並寫入一筆 `CreditScoreLog(scoreChange = -10)`。
- 團主「主動取消合購」不是排程處理範圍；目前由 `CampaignService.cancelCampaignByHost(...)` 處理。
- 團主主動取消且已有 active `JOINED` 團員時，目前程式會呼叫 `CreditScoreService.recordScoreChange(...)` 寫入信用分紀錄。

本文件整理目前專案已實作的排程工作，內容以 `CampaignScheduler` 與其對應 service/repository 行為為準。

---

## 1. `closeExpiredCampaigns`

- 位置: `com.costco.gb.scheduler.CampaignScheduler`
- Cron: `0 * * * * *`
- 執行頻率: 每分鐘第 0 秒
- 用途: 將已過期且仍可募集的合購自動關閉
- 實際流程:
  1. 取得目前時間 `LocalDateTime.now()`
  2. 呼叫 `campaignRepository.updateExpiredCampaignsStatus(now)`
  3. 將符合條件的合購狀態更新為 `FAILED`
- 影響:
  - 已過 `expireTime` 的 `OPEN` 合購會被關閉
  - 目前這支排程只做狀態更新，不會處理通知

---

## 2. `huntGhostedCampaigns`

- 位置: `com.costco.gb.scheduler.CampaignScheduler`
- Cron: `0 30 * * * *`
- 執行頻率: 每小時第 30 分鐘
- 用途: 抓出團主超時未交付的合購
- 實際流程:
  1. 排程呼叫 `campaignService.processGhostedCampaigns()`
  2. service 內部找出超過 24 小時仍未交付的合購
  3. 將合購狀態改成 `HOST_NO_SHOW`
  4. 團主信用分扣 10 分
  5. 寫入一筆 `CreditScoreLog`
  6. 取消該合購下的全部參與者
- 影響:
  - 這是目前主要的失約處理機制
  - 會同時影響合購狀態、團主信用分、參與者狀態

---

## 3. `compressOldCampaignImages`

- 位置: `com.costco.gb.scheduler.CampaignScheduler`
- Cron: `0 0 3 * * *`
- 執行頻率: 每天凌晨 3 點
- 用途: 壓縮舊合購圖片，降低檔案體積
- 實際流程:
  1. 取出 90 天前的舊合購資料
  2. 掃描 `campaign.imageUrls` 內每張圖片
  3. 以 Thumbnailator 產生 `100x100` 縮圖
  4. 縮圖品質設定為 `0.8`
  5. 新檔名格式為 `原檔名_thumb.副檔名`
  6. 成功後刪除原圖
  7. 將 `campaign.imageUrls` 改成縮圖檔名後存回資料庫
- 影響:
  - 圖片檔案實體會被替換成縮圖
  - 若單張圖片處理失敗，該張圖會保留原檔名，不中斷整批流程

---

## 4. 排程總覽

| 方法 | Cron | 頻率 | 主要作用 |
| --- | --- | --- | --- |
| `closeExpiredCampaigns` | `0 * * * * *` | 每分鐘 | 關閉過期合購，狀態改 `FAILED` |
| `huntGhostedCampaigns` | `0 30 * * * *` | 每小時 30 分 | 處理團主超時未交付，改 `HOST_NO_SHOW` 並扣分 |
| `compressOldCampaignImages` | `0 0 3 * * *` | 每天 03:00 | 壓縮 90 天前的合購圖片 |

---

## 5. 目前排程特性與限制

- 排程皆為本機 Spring `@Scheduled`
- 沒有分散式鎖，若未來多台服務同時啟動，可能重複執行
- `closeExpiredCampaigns` 與 `huntGhostedCampaigns` 目前都偏向資料庫輪詢模式
- 通知、事件佇列、Redis 協調目前仍是 TODO，尚未實作
