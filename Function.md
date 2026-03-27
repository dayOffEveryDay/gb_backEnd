# Function.md

本文件整理目前專案主要 service 方法與實際業務規則，內容以 `src/main/java/com/costco/gb/service` 為準。

---

## 1. AuthService

### 1.1 `lineLogin(LineLoginRequest request)`

- 用途: 使用 LINE OAuth 完成登入或註冊
- 輸入:
  - `code`
  - `redirectUri`
- 回傳: `AuthResponse`
- 流程:
  1. 呼叫 LINE token API 交換 `access_token`
  2. 用 `access_token` 取得 LINE 使用者資料
  3. 以 `lineUid` 查找本地會員
  4. 若會員存在，更新 `displayName`、`profileImageUrl`
  5. 若會員不存在，建立 `User`，並建立預設 `UserPreference`
  6. 產生 JWT 並回傳
- 補充:
  - 新會員預設 `hasCostcoMembership = false`
  - `UserPreference` 預設會建立空的偏好賣場與分類清單

### 1.2 `getLineAccessToken(String code, String redirectUri)`

- 用途: 對 LINE token endpoint 換取 access token
- 性質: `private`
- 失敗時: 丟出 `RuntimeException("Line login failed at token exchange.")`

### 1.3 `getLineUserProfile(String accessToken)`

- 用途: 透過 LINE user info endpoint 取得使用者資料
- 性質: `private`
- 失敗時: 丟出 `RuntimeException("Line login failed at fetching profile.")`

---

## 2. CampaignService

### 2.1 `getActiveCampaigns(Integer storeId, Integer categoryId, String keyword, int page, int size)`

- 用途: 查詢合購列表
- 回傳: `Page<CampaignSummaryResponse>`
- 規則:
  - 依 `createdAt desc` 排序
  - 支援賣場、分類、關鍵字條件
  - 透過 `mapToSummaryResponse` 組裝回傳 DTO

### 2.2 `createCampaign(Long hostId, CreateCampaignRequest request)`

- 用途: 建立合購
- 主要規則:
  - 團主必須存在
  - 團主必須有 Costco 會員資格，否則不可開團
  - `storeId`、`categoryId` 必須存在
  - 最多可上傳 3 張圖片
  - 圖片存放在 `uploads/campaigns/`
  - 圖片檔名改為 UUID
  - 上傳時先用 Thumbnailator 壓到最大 `1080x1080`
  - 建立時 `availableQuantity = totalQuantity`
  - 初始狀態固定為 `OPEN`
- 失敗補償:
  - 若儲存流程中途失敗，會刪除本次已存檔案後再丟出例外

### 2.3 `getMyHostedCampaigns(Long userId, Pageable pageable)`

- 用途: 取得使用者開過的合購
- 回傳: `Page<CampaignSummaryResponse>`
- 規則: 依建立時間新到舊

### 2.4 `getMyJoinedCampaigns(Long userId, Pageable pageable)`

- 用途: 取得使用者參加過的合購
- 回傳: `Page<CampaignSummaryResponse>`

### 2.5 `mapToSummaryResponse(Campaign campaign)`

- 用途: 將 `Campaign` 轉成 `CampaignSummaryResponse`
- 性質: `private`
- 規則:
  - 將 `imageUrls` 字串依逗號拆成清單
  - 每張圖補成 `${app.base-url}/images/{fileName}`
  - 一併回傳 host、store、category 的摘要資訊

### 2.6 `joinCampaign(Long userId, Long campaignId, JoinCampaignRequest request)`

- 用途: 參加合購
- 主要規則:
  - 合購必須存在
  - 合購狀態必須是 `OPEN`
  - 合購不可已過期
  - 團主不能參加自己的團
  - `quantity` 必須大於 0
  - 以 repository 原子扣減 `availableQuantity`
  - 若同使用者已在該團中，直接累加 `Participant.quantity`
  - 若首次加入，建立 `Participant(status = JOINED)`
  - 若剩餘數量變 0，合購狀態改成 `FULL`

### 2.7 `reviseCampaign(Long userId, Long campaignId, ReviseCampaignRequest request)`

- 用途: 下修目前參與數量
- 主要規則:
  - `request.quantity` 必須大於 0
  - 合購狀態必須是 `OPEN` 或 `FULL`
  - 呼叫者必須已參加該團
  - 修改後數量不得小於 1
  - 會把釋放的數量加回合購庫存
  - 若合購原本為 `FULL` 且釋放後有庫存，狀態改回 `OPEN`

### 2.8 `withdrawCampaign(Long userId, Long campaignId)`

- 用途: 完整退出合購
- 主要規則:
  - 必須找到對應 `Participant`
  - `Participant.status` 必須是 `JOINED`
  - 合購狀態必須是 `OPEN` 或 `FULL`
  - 若合購已過期，不可退出
  - 將 `Participant.status` 改成 `CANCELLED`
  - 歸還參與數量，必要時把合購重開為 `OPEN`
  - 使用者 `participantCancelCount + 1`

### 2.9 `deliverCampaign(Long userId, Long campaignId)`

- 用途: 團主標記已交付
- 主要規則:
  - 只有團主可以操作
  - 合購狀態必須是 `OPEN` 或 `FULL`
  - 至少要有一位 `JOINED` 參與者
  - 成功後合購狀態改成 `DELIVERED`

### 2.10 `confirmReceipt(Long userId, Long campaignId)`

- 用途: 參與者確認收貨
- 主要規則:
  - 合購狀態必須是 `DELIVERED`
  - 呼叫者必須是該團參與者
  - 參與狀態必須是 `JOINED`
  - 確認後 `Participant.status = CONFIRMED`
  - 若該團已沒有任何 `JOINED` 參與者，合購狀態改成 `COMPLETED`

### 2.11 `processGhostedCampaigns()`

- 用途: 處理團主失約未交付的合購
- 規則:
  - 找出超過 24 小時仍未交付的合購
  - 將合購狀態改成 `HOST_NO_SHOW`
  - 團主信用分扣 10 分，最低不低於 0
  - 新增 `CreditScoreLog(scoreChange = -10)`
  - 取消該團全部參與者

### 2.12 `cancelCampaignByHost(Long userId, Long campaignId)`

- 用途: 團主取消合購
- 主要規則:
  - 只有團主可以操作
  - 合購狀態必須是 `OPEN` 或 `FULL`
  - 若已有 `JOINED` 參與者:
    - `FULL` 狀態扣 5 分
    - `OPEN` 狀態扣 2 分
    - 建立對應 `CreditScoreLog`
    - 取消全部參與者
  - 最後將合購狀態設為 `CANCELLED`

---

## 3. ReferenceDataService

### 3.1 `getActiveStores()`

- 用途: 取得啟用中的賣場
- 回傳: `List<StoreResponse>`
- 規則: 只回傳 `isActive = true`

### 3.2 `getAllCategories()`

- 用途: 取得所有分類
- 回傳: `List<CategoryResponse>`
- 規則: 依 `sortOrder asc` 排序

---

## 4. ReviewService

### 4.1 `createReview(Long reviewerId, CreateReviewRequest request)`

- 用途: 建立評價並依規則調整信用分
- 主要規則:
  - 不可評自己
  - `rating` 只接受 `1 ~ 5`
  - 同一合購、同一評價人、同一被評人不可重複評價
  - 建立 `Review` 時會記錄 `isScoreCounted`
- 信用分規則:
  - `5` 星: `+1`
  - `1` 星: `-3`
  - `2~4` 星: 不調整信用分
  - 正向加分有 7 天限制:
    - 若 reviewer 在 7 天內已對同 reviewee 有一筆已計分的正向評價，新的 `5` 星不再加分
  - 分數範圍會被限制在 `0 ~ 100`
  - 若有分數異動，會新增 `CreditScoreLog`

---

## 5. UserService

### 5.1 `updateProfile(Long userId, UpdateProfileRequest request)`

- 用途: 更新個人資料
- 規則:
  - 使用者必須存在
  - `displayName`、`hasCostcoMembership` 皆支援部分更新
  - 只更新 request 中有提供的欄位

### 5.2 `getMyCreditLogs(Long userId, Pageable pageable)`

- 用途: 查詢個人信用分異動紀錄
- 回傳: `Page<CreditLogResponse>`
- 規則: 依 `createdAt desc` 排序

---

## 6. 信用分異動整理

### 6.1 會加減信用分的情境

- 評價 5 星: `+1`
- 評價 1 星: `-3`
- 團主取消 `OPEN` 合購且已有參與者: `-2`
- 團主取消 `FULL` 合購且已有參與者: `-5`
- 團主超過 24 小時未交付: `-10`

### 6.2 目前不直接改信用分但會記錄行為的情境

- 參與者退出合購: 增加 `participantCancelCount`
