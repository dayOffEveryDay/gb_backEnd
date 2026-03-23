# Function.md - 後端功能詳解

本文件詳細說明 `gb_backend` 專案的各項核心功能，包含其商業邏輯、輸入/輸出以及相關的處理流程。

---

## 1. 認證與使用者管理 (AuthService)

### 1.1 Line 登入 / 註冊
*   **商業邏輯**：處理使用者透過 Line 帳號進行登入或首次註冊的流程。此服務負責與 Line 平台進行 OAuth2 互動，獲取使用者基本資料，並在系統中建立或更新使用者資訊，最終核發內部 JWT Token。
*   **輸入**：`LineLoginRequest` (包含 Line 授權碼 `code` 和 `redirectUri`)
*   **輸出**：`AuthResponse` (包含 JWT `token`、`isNewUser` 旗標以及使用者基本資料 `user`)
*   **處理流程**：
    1.  呼叫 Line API 換取 `access_token`。
    2.  使用 `access_token` 取得 Line 使用者 Profile (UID, 暱稱, 頭像)。
    3.  根據 Line UID 檢查資料庫是否存在該使用者：
        *   **存在**：更新使用者的 Line 暱稱和頭像，保持最新狀態。
        *   **不存在**：創建新使用者記錄，並為其初始化預設的 `UserPreference` (例如接收通知、空的偏好門市/分類列表)。
    4.  為該使用者核發系統內部 JWT Token。
    5.  組裝 `AuthResponse` 返回給前端。
*   **錯誤處理**：若 Line API 呼叫失敗 (例如 token 交換失敗或獲取 Profile 失敗)，會拋出 `RuntimeException`，由 `GlobalExceptionHandler` 捕獲並返回統一錯誤格式。

### 1.2 開發者後門登入 (僅限開發環境)
*   **商業邏輯**：提供開發者快速登入系統的機制，無需經過 Line OAuth2 流程，直接為指定 `userId` 核發 JWT Token。此功能應在正式環境中禁用或移除。
*   **輸入**：`userId` (Long, 可選，預設為 1)
*   **輸出**：`Map<String, String>` (包含 `message`, `userId`, `token`)
*   **處理流程**：
    1.  直接根據提供的 `userId` (或預設值) 生成一個 JWT Token。
    2.  返回成功訊息及 Token。

---

## 2. 合購活動管理 (CampaignService)

### 2.1 取得活躍合購單列表
*   **商業邏輯**：提供篩選、分頁和排序功能，查詢目前狀態為活躍中 (OPEN 或 FULL) 的合購單。
*   **輸入**：`storeId` (Integer, 可選)、`categoryId` (Integer, 可選)、`keyword` (String, 可選)、`page` (int, 預設 0)、`size` (int, 預設 10)
*   **輸出**：`Page<CampaignSummaryResponse>` (分頁的合購單摘要列表)
*   **處理流程**：
    1.  根據 `page` 和 `size` 設定分頁資訊，並以 `createdAt` 倒序排序 (最新創建的在前)。
    2.  呼叫 `CampaignRepository` 執行帶有門市、分類和關鍵字篩選條件的動態查詢。
    3.  將查詢結果的 `Campaign` 實體轉換為 `CampaignSummaryResponse` DTO，其中圖片 URL 會被處理成前端可直接訪問的完整路徑。

### 2.2 發起合購單
*   **商業邏輯**：處理使用者創建新的合購活動的請求，包含身分驗證、關聯資料檢查和商品圖片上傳處理。
*   **輸入**：`hostId` (Long, 團主使用者 ID)、`CreateCampaignRequest` (包含門市、分類、情境類型、商品資訊、面交時間地點、過期時間和最多 3 張商品圖片)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **驗證團主身分與權限**：檢查 `hostId` 是否有效，並確認團主擁有好市多會員資格。
    2.  **驗證關聯實體**：確保指定的 `storeId` 和 `categoryId` 在資料庫中存在。
    3.  **圖片上傳處理**：
        *   為每張上傳的圖片生成唯一的 UUID 檔名，並保存副檔名。
        *   將圖片壓縮至 1080p 最大長寬並存儲到伺服器本地的 `uploads/campaigns/` 目錄。
        *   將所有圖片檔名以逗號分隔的字串形式存入 `Campaign` 實體的 `imageUrls` 欄位。
    4.  **創建 `Campaign` 實體**：根據請求資料構建 `Campaign` 物件，初始狀態為 `OPEN` (招募中)，`availableQuantity` 等於 `totalQuantity`。
    5.  **寫入資料庫**：保存 `Campaign` 實體。
    6.  **失敗退場機制**：如果在資料庫寫入失敗，會自動刪除已上傳到硬碟的圖片檔案，防止產生垃圾。

### 2.3 加入合購單
*   **商業邏輯**：處理使用者加入一個現有合購單的請求，確保交易的原子性並處理重複加入的情況。
*   **輸入**：`userId` (Long, 參與者使用者 ID)、`campaignId` (Long, 合購單 ID)、`JoinCampaignRequest` (包含認購數量 `quantity`)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **基本檢查**：確認合購單存在、狀態為 `OPEN` 且未過期，並且參與者不是團主本人，認購數量大於 0。
    2.  **原子性扣庫存**：使用資料庫的原子性操作減少 `Campaign` 的 `availableQuantity`，防止高併發下的超賣問題。如果數量不足，則拋出錯誤。
    3.  **處理參與者明細**：
        *   **已參加過**：更新現有 `Participant` 的認購數量 (追加)。
        *   **首次參加**：創建新的 `Participant` 記錄，狀態為 `JOINED`。
    4.  **檢查滿單狀態**：若 `availableQuantity` 歸零，則自動將 `Campaign` 狀態更新為 `FULL`。

### 2.4 退出合購單
*   **商業邏輯**：允許參與者在特定條件下退出已加入的合購單，並更新相關庫存和記錄懲罰次數。
*   **輸入**：`userId` (Long, 參與者使用者 ID)、`campaignId` (Long, 合購單 ID)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **檢查參與者狀態**：確認使用者確實已加入該合購單且未曾退出。
    2.  **檢查合購單狀態**：合購單必須仍在招募中 (`OPEN` 或 `FULL`) 且未過期。
    3.  **更新參與者狀態**：將 `Participant` 狀態變更為 `CANCELLED`。
    4.  **原子性加回庫存**：將退出的數量加回 `Campaign` 的 `availableQuantity`，並將狀態強制切回 `OPEN` (如果之前是 `FULL`)。
    5.  **信用記點**：增加該使用者的 `participantCancelCount` (參與者反悔次數)。

### 2.5 團主宣告已面交
*   **商業邏輯**：團主將合購單標記為已面交，等待所有團員確認收貨。
*   **輸入**：`userId` (Long, 團主使用者 ID)、`campaignId` (Long, 合購單 ID)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **權限檢查**：確保只有團主可以執行此操作。
    2.  **狀態檢查**：合購單狀態必須為 `OPEN` 或 `FULL`。
    3.  **防呆**：如果沒有任何團員參與，團主應直接取消合購單而非宣告面交。
    4.  **更新合購單狀態**：將 `Campaign` 狀態變更為 `DELIVERED`。

### 2.6 團員確認收貨
*   **商業邏輯**：參與者確認已收到合購商品。若所有參與者都確認收貨，則合購單自動結案。
*   **輸入**：`userId` (Long, 參與者使用者 ID)、`campaignId` (Long, 合購單 ID)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **狀態檢查**：合購單狀態必須為 `DELIVERED` (團主已宣告面交)。
    2.  **檢查參與者狀態**：確認使用者確實已加入該合購單且狀態為 `JOINED`。
    3.  **更新參與者狀態**：將 `Participant` 狀態變更為 `CONFIRMED`。
    4.  **結案判斷**：檢查該合購單是否所有參與者都已 `CONFIRMED`。若皆已確認，則將 `Campaign` 狀態變更為 `COMPLETED`。

### 2.7 團主主動取消合購單
*   **商業邏輯**：團主在某些條件下可以取消自己發起的合購單，並根據是否有團員參與實施不同的懲罰機制，同時記錄信用分數變動。
*   **輸入**：`userId` (Long, 團主使用者 ID)、`campaignId` (Long, 合購單 ID)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  **身分核對**：確保只有團主可以執行此操作。
    2.  **狀態核對**：合購單必須仍在招募中 (`OPEN` 或 `FULL`)，已面交或結案的不能取消。
    3.  **結算懲罰**：
        *   **有團員參與**：根據合購單狀態 (FULL 或 OPEN) 扣除團主不同的信用分數 (例如 FULL 扣 5 分，OPEN 扣 2 分)。同時，將所有受影響團員的 `Participant` 狀態變更為 `CANCELLED` (釋放團員)。**並寫入 `CreditScoreLog`。**
        *   **無團員參與**：不扣除信用分數，和平取消。
    4.  **最終狀態**：將 `Campaign` 狀態變更為 `CANCELLED`。

### 2.8 處理幽靈單 (系統排程專用)
*   **商業邏輯**：自動識別並處理團主長時間未確認面交的「幽靈單」，以維護平台交易的公平性與流暢度。此功能由排程器觸發，並記錄信用分數變動。
*   **輸入**：無 (由系統內部觸發)
*   **輸出**：無
*   **處理流程**：
    1.  查詢所有在規定時間 (例如 24 小時) 內未被宣告面交 (`DELIVERED`) 的活躍中 (`OPEN` 或 `FULL`) 合購單。
    2.  對於每個幽靈單：
        *   將 `Campaign` 狀態標記為 `HOST_NO_SHOW` (團主放鳥)。
        *   嚴懲團主：扣除其信用分數 (例如 10 分)。**並寫入 `CreditScoreLog`。**
        *   釋放所有受影響的參與者，將其 `Participant` 狀態變更為 `CANCELLED`。

---

## 3. 參考資料查詢 (ReferenceDataService)

### 3.1 取得所有營業中的門市
*   **商業邏輯**：查詢並返回所有營業中的好市多門市資訊，供前端顯示。
*   **輸入**：無
*   **輸出**：`List<StoreResponse>`
*   **處理流程**：
    1.  從 `StoreRepository` 查詢所有門市。
    2.  將 `Store` 實體轉換為 `StoreResponse` DTO 列表。

### 3.2 取得所有商品分類
*   **商業邏輯**：查詢並返回所有商品分類資訊，供前端顯示。
*   **輸入**：無
*   **輸出**：`List<CategoryResponse>`
*   **處理流程**：
    1.  從 `CategoryRepository` 查詢所有商品分類。
    2.  將 `Category` 實體轉換為 `CategoryResponse` DTO 列表。

---

## 4. 評價管理 (ReviewService)

### 4.1 創建評價
*   **商業邏輯**：處理使用者對已完成的合購單中的另一方進行評價的請求。
*   **輸入**：`reviewerId` (Long, 評價者使用者 ID)、`CreateReviewRequest` (包含 `campaignId`、`revieweeId`、`rating` 和 `comment`)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  驗證 `campaignId`、`reviewerId` 和 `revieweeId` 的有效性，確保評價關係正確。
    2.  創建 `Review` 實體並保存。
    3.  更新 `reviewee` (被評價者) 的信用分數和評價相關統計數據。

---

## 5. 使用者資料管理 (UserService)

### 5.1 更新個人資料
*   **商業邏輯**：允許使用者更新自己的顯示名稱和好市多會員資格。
*   **輸入**：`userId` (Long, 當前使用者 ID)、`UpdateProfileRequest` (包含 `displayName` 和 `hasCostcoMembership`)
*   **輸出**：無直接返回，成功時靜默完成，失敗時拋出錯誤。
*   **處理流程**：
    1.  根據 `userId` 查詢使用者。
    2.  更新 `User` 實體的 `displayName` 和 `hasCostcoMembership` 欄位。
    3.  保存更新後的使用者實體。

### 5.2 查詢個人信用分數紀錄
*   **商業邏輯**：提供使用者查詢個人信用分數變動的歷史紀錄，包含分數變化、原因、相關合購單 ID 和時間。
*   **輸入**：`userId` (Long, 當前使用者 ID)、`pageable` (Spring Data `Pageable` 物件，包含分頁和排序資訊)
*   **輸出**：`Page<CreditLogResponse>` (分頁的信用分數紀錄列表)
*   **處理流程**：
    1.  從 `CreditScoreLogRepository` 根據 `userId` 查詢該使用者的所有信用分數紀錄，並自動依照建立時間倒序排序。
    2.  將查詢結果的 `CreditScoreLog` 實體轉換為 `CreditLogResponse` DTO 列表。
