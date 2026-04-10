# Function.md

## Implementation Notes

### Host manual cancellation

- `cancelCampaignByHost(...)` currently updates campaign and participant status only.
- It currently does not deduct host credit score.
- It currently does not write a `CreditScoreLog`.

### Credit score logs

- `CreditScoreLog.scoreChange` records the exact delta for each score change.
- Score deduction for host no-show is `-10`.

### Review status query

- `ReviewController` now exposes `GET /api/v1/reviews/check`.
- `ReviewService.checkReviewStatus(...)` returns whether the current user already reviewed the target user for the given campaign.
- `ReviewStatusResponse` includes `isReviewed`, `rating`, and `comment`.

### Campaign completion workflow

- `Campaign.completedAt` is set when all joined participants confirm receipt.
- `confirmReceipt(...)` now triggers `notificationService.notifyReviewTime(campaign)` after the campaign becomes `COMPLETED`.
- `notifyReviewTime(...)` sends `CAMPAIGN_COMPLETED` notifications to the host and all confirmed participants.

### WebSocket room guard

- `ChatRoomInterceptor` is registered on the STOMP inbound channel after JWT authentication.
- Subscription is allowed only for the host or participants in allowed statuses.
- Completed campaign chat remains accessible for 3 days after `completedAt`.

本文件整理目前專案 `gb_backEnd` 主要 service / WebSocket / 新增功能邏輯，內容以 `src/main/java/com/costco/gb` 實際程式碼為準，並補上這次工作區尚未提交的聊天與通知功能。

---

## 1. AuthService

### 1.1 `lineLogin(LineLoginRequest request)`

- 功能: 用 LINE OAuth code 完成登入或註冊
- 輸入:
  - `code`
  - `redirectUri`
- 輸出: `AuthResponse`
- 核心流程:
  1. 呼叫 LINE token API 換取 `access_token`
  2. 以 `access_token` 取得 LINE 使用者資料
  3. 依 `lineUid` 查詢本地使用者
  4. 若已存在則更新 `displayName`、`profileImageUrl`
  5. 若不存在則建立 `User` 與預設 `UserPreference`
  6. 產生 JWT 回傳

### 1.2 `getLineAccessToken(String code, String redirectUri)`

- 功能: 呼叫 LINE token endpoint
- 可見性: `private`
- 失敗時: 丟出 `RuntimeException("Line login failed at token exchange.")`

### 1.3 `getLineUserProfile(String accessToken)`

- 功能: 呼叫 LINE profile endpoint
- 可見性: `private`
- 失敗時: 丟出 `RuntimeException("Line login failed at fetching profile.")`

---

## 2. CampaignService

### 2.1 `getActiveCampaigns(Integer storeId, Integer categoryId, String keyword, int page, int size)`

- 功能: 查詢首頁合購列表
- 輸出: `Page<CampaignSummaryResponse>`
- 流程:
  1. 依 `createdAt desc` 建立分頁
  2. 呼叫 `campaignRepository.findCampaignsWithFilters(...)`
  3. 透過 `mapToSummaryResponse` 轉 DTO

### 2.2 `createCampaign(Long hostId, CreateCampaignRequest request)`

- 功能: 建立合購
- 核心規則:
  - 團主必須存在且 `hasCostcoMembership = true`
  - `storeId`、`categoryId` 必須對應到有效資料
  - `images` 最多 3 張
  - 圖片存放目錄為 `uploads/campaigns/`
  - 檔名改為 UUID
  - 上傳時使用 Thumbnailator 壓縮到最大 `1080 x 1080`
  - 初始 `availableQuantity = totalQuantity`
  - 初始狀態為 `OPEN`
- 例外處理:
  - 任何流程失敗時會刪除本次已存下的圖片檔

### 2.3 `getMyHostedCampaigns(Long userId, Pageable pageable)`

- 功能: 查詢我開的團
- 輸出: `Page<CampaignSummaryResponse>`
- 排序: `createdAt desc`

### 2.4 `getMyJoinedCampaigns(Long userId, Pageable pageable)`

- 功能: 查詢我跟團的紀錄
- 輸出: `Page<CampaignSummaryResponse>`

### 2.5 `mapToSummaryResponse(Campaign campaign)`

- 功能: 將 `Campaign` 轉成 `CampaignSummaryResponse`
- 可見性: `private`
- 處理重點:
  - 將 `imageUrls` 的逗號字串拆成陣列
  - 自動組裝成 `${app.base-url}/images/{fileName}`
  - 回填 `host`、`storeName`、`categoryName`

### 2.6 `joinCampaign(Long userId, Long campaignId, JoinCampaignRequest request)`

- 功能: 參加合購或追加數量
- 核心規則:
  - 合購單必須存在
  - 狀態必須是 `OPEN`
  - 不可超過截止時間
  - 團主不能參加自己的團
  - `quantity > 0`
  - 先用 `campaignRepository.decrementQuantity` 做原子扣庫存
  - 若已參加過，累加原本 `Participant.quantity`
  - 若首次參加，建立 `Participant(status = JOINED)`
  - 若扣完後 `availableQuantity == 0`，狀態轉為 `FULL`
  - 狀態變成 `FULL` 時，呼叫 `notificationService.notifyCampaignFull(campaign)`

### 2.7 `reviseCampaign(Long userId, Long campaignId, ReviseCampaignRequest request)`

- 功能: 減少已認購數量
- 核心規則:
  - `request.quantity > 0`
  - 合購狀態需為 `OPEN` 或 `FULL`
  - 使用者必須已參團
  - 減少後數量不得小於 `1`
  - 會先更新 `Participant.quantity`
  - 再用 `campaignRepository.incrementQuantity` 歸還庫存
  - 若原本 `FULL` 且歸還後有剩餘數量，狀態改回 `OPEN`

### 2.8 `withdrawCampaign(Long userId, Long campaignId)`

- 功能: 退出合購
- 核心規則:
  - 使用者必須有 `Participant`
  - `Participant.status` 必須是 `JOINED`
  - 合購狀態需為 `OPEN` 或 `FULL`
  - 不可晚於 `expireTime`
  - 將 `Participant.status` 改為 `CANCELLED`
  - 透過 `campaignRepository.incrementQuantityAndReopen` 歸還庫存並強制回到 `OPEN`
  - 使用者 `participantCancelCount + 1`

### 2.9 `deliverCampaign(Long userId, Long campaignId)`

- 功能: 團主標記已交付
- 核心規則:
  - 只有團主可以操作
  - 合購狀態需為 `OPEN` 或 `FULL`
  - 至少要有一位 `JOINED` 團員
  - 狀態改為 `DELIVERED`

### 2.10 `confirmReceipt(Long userId, Long campaignId)`

- 功能: 團員確認收貨
- 核心規則:
  - 合購狀態必須是 `DELIVERED`
  - 使用者必須有 `Participant`
  - `Participant.status` 必須是 `JOINED`
  - 確認後改成 `CONFIRMED`
  - 若整張單已沒有 `JOINED` 的團員，合購狀態改為 `COMPLETED`

### 2.11 `processGhostedCampaigns()`

- 功能: 系統排程處理團主超時未處理的單
- 核心規則:
  - 搜尋 `meetupTime` 超過 24 小時仍未處理的團單
  - 將合購狀態改為 `HOST_NO_SHOW`
  - 團主信用分扣 `10`
  - 寫入 `CreditScoreLog(scoreChange = -10)`
  - 透過 `participantRepository.cancelAllByCampaignId` 將所有 `JOINED` 團員改成 `CANCELLED_BY_SYSTEM`

### 2.12 `cancelCampaignByHost(Long userId, Long campaignId)`

- 功能: 團主主動取消合購
- 核心規則:
  - 只有團主可以操作
  - 合購狀態需為 `OPEN` 或 `FULL`
  - 若已有 `JOINED` 團員:
    - `FULL` 扣 `5` 分
    - `OPEN` 扣 `2` 分
    - 寫入 `CreditScoreLog`
    - 透過 `participantRepository.cancelAllByHost` 將團員批次改成 `CANCELLED_BY_HOST`
  - 最後將合購狀態改成 `CANCELLED`

---

## 3. ChatService

這是本次新增、目前尚未提交的主要功能。

### 3.1 `saveMessage(Long senderId, Long campaignId, ChatMessageRequest request)`

- 功能: 儲存聊天室訊息並回傳廣播 DTO
- 輸入:
  - `senderId`
  - `campaignId`
  - `ChatMessageRequest.content`
- 輸出: `ChatMessageResponse`
- 核心規則:
  - 合購單與發送者必須存在
  - 發送者需是團主，或存在 `Participant`
  - 非團主且非團員時，丟出 `RuntimeException("您不是此合購單的成員，無法發言！")`
  - 目前訊息類型固定寫入 `messageType = "TEXT"`
- 回傳欄位:
  - `senderId`
  - `senderName`
  - `content`
  - `timestamp`

### 3.2 `getChatHistory(Long userId, Long campaignId)`

- 功能: 查詢聊天室歷史訊息
- 輸出: `List<ChatMessageResponse>`
- 核心規則:
  - 使用者需是團主或參與者
  - 若不是團員，會記錄 warning log
  - 透過 `chatMessageRepository.findByCampaignIdOrderByCreatedAtAsc(campaignId)` 依時間正序查詢

---

## 4. NotificationService

這次工作區也新增了滿單即時通知。

### 4.1 `notifyCampaignFull(Campaign campaign)`

- 功能: 合購滿單時通知團主與所有團員
- 流程:
  1. 先通知團主
  2. 再查詢 `participantRepository.findByCampaignId(...)`
  3. 對每位團員建立通知

### 4.2 `sendNotification(User user, String type, Long refId, String content)`

- 功能: 寫入通知資料並推播給特定使用者
- 可見性: `private`
- 實作內容:
  - 建立 `Notification`
  - 存入 `notificationRepository`
  - 用 `SimpMessagingTemplate.convertAndSendToUser(...)`
  - 推送到 `/user/{userId}/queue/notifications`
  - 目前廣播 payload 為:
    - `content`
    - `type`
    - `referenceId`

---

## 5. WebSocket 與 Controller

### 5.1 `ChatController.sendMessage(...)`

- 路徑類型: STOMP MessageMapping
- Destination: `/app/chat/{campaignId}/sendMessage`
- Broadcast: `/topic/campaigns/{campaignId}`
- 流程:
  1. 從 WebSocket session 讀出 `userId`
  2. 若無 `userId`，丟出未授權例外
  3. 呼叫 `chatService.saveMessage(...)`
  4. 回傳 `ChatMessageResponse` 給訂閱中的客戶端

### 5.2 `ChatRestController.getChatHistory(...)`

- REST Path: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- 流程:
  1. 從 request attribute 取 `userId`
  2. 呼叫 `chatService.getChatHistory(...)`
  3. 回傳 `List<ChatMessageResponse>`

### 5.3 `WebSocketConfig`

- 功能:
  - 啟用 `@EnableWebSocketMessageBroker`
  - 註冊 STOMP endpoint `/ws`
  - 開啟 SockJS fallback
  - 啟用 broker prefix `/topic`、`/queue`
  - 設定 application prefix `/app`
  - 設定 user destination prefix `/user`

### 5.4 `WebSocketAuthInterceptor`

- 功能: 在 STOMP `CONNECT` 時驗證 JWT
- 流程:
  1. 從 native header 讀取 `Authorization`
  2. 檢查 `Bearer ` 前綴
  3. 用 `jwtService.extractUserId(token)` 取回 `userId`
  4. 存入 `accessor.getSessionAttributes().put("userId", userId)`

### 5.5 `WebSocketConfig.configureClientInboundChannel(...)`

- 功能: 把 `WebSocketAuthInterceptor` 接到 STOMP inbound channel
- 效果:
  - 前端在 `CONNECT` 階段送來的 `Authorization` 會先經過 JWT 驗證
  - 驗證成功後，聊天室訊息處理就能直接從 session 取 `userId`

---

## 6. Repository 變更重點

### 6.1 `ParticipantRepository`

- 新增 / 使用中的重點方法:
  - `findByCampaignId(Long campaignId)`: 滿單通知用
  - `countByCampaignIdAndStatus(Long campaignId, String status)`: 面交與完成判斷用
  - `cancelAllByCampaignId(Long campaignId)`: 將 `JOINED` 批次改成 `CANCELLED_BY_SYSTEM`
  - `cancelAllByHost(Long campaignId)`: 將 `JOINED` 批次改成 `CANCELLED_BY_HOST`
  - `deleteByCampaignId(Long campaignId)`: 後續清理用途

### 6.2 `NotificationRepository`

- 新增 / 使用中的重點方法:
  - `findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId)`
  - `deleteByExpireTimeBefore(LocalDateTime cutoffDate)`

### 6.3 `ChatMessageRepository`

- 新增重點方法:
  - `findByCampaignIdOrderByCreatedAtAsc(Long campaignId)`: 撈聊天室歷史訊息
  - `deleteByCreatedAtBefore(LocalDateTime cutoffDate)`: 後續排程清理 3 個月前訊息

---

## 7. ReferenceDataService

### 7.1 `getActiveStores()`

- 功能: 取得啟用中的門市
- 輸出: `List<StoreResponse>`

### 7.2 `getAllCategories()`

- 功能: 依 `sortOrder asc` 取得分類
- 輸出: `List<CategoryResponse>`

---

## 8. ReviewService

### 8.1 `createReview(Long reviewerId, CreateReviewRequest request)`

- 功能: 建立評價，並根據規則調整信用分
- 核心規則:
  - 不可評自己
  - `rating` 僅接受 `1 ~ 5`
  - 同一筆交易不可重複評價
  - `5` 星通常 `+1`
  - `1` 星通常 `-3`
  - `2 ~ 4` 星不直接加減分
  - 具 7 天防刷單限制
  - 所有信用異動都會寫入 `CreditScoreLog`

---

## 9. UserService

### 9.1 `updateProfile(Long userId, UpdateProfileRequest request)`

- 功能: 更新個人資料
- 核心規則:
  - 先查使用者是否存在
  - `displayName`、`hasCostcoMembership` 有傳才更新

### 9.2 `getMyCreditLogs(Long userId, Pageable pageable)`

- 功能: 查詢我的信用分異動紀錄
- 輸出: `Page<CreditLogResponse>`
- 排序: `createdAt desc`
