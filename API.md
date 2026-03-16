# API.md - 後端 API 文件

## 專案功能概覽

本專案提供一個輕量、即時的在地化（LBS）好市多合購媒合服務，主要功能包括：
*   **會員與社交系統**：Line OAuth2 登入、信用評分、黑名單、關注團購主。
*   **開團與認購機制**：即時湊單、未來預約分購、防超賣庫存、狀態機與排程流局。
*   **即時通訊與通知**：專屬聊天室、站內小鈴鐺推播。
*   **基礎資料查詢**：取得門市、商品分類等參考資料。

---

## API 端點詳情

### 1. 認證相關 (AuthController)

#### 1.1 Line 登入
*   **功能**：處理 Line OAuth2 登入，返回 JWT Token 和使用者資訊。
*   **端點**：`/api/v1/auth/line`
*   **方法**：`POST`
*   **請求體 (application/json)**：
    ```json
    {
        "code": "Line 授權碼",
        "redirectUri": "與 Line 授權時相同的重定向 URI"
    }
    ```
    *   `code` (String): Line 授權碼。
    *   `redirectUri` (String): 為了安全驗證，Line 要求換 Token 時必須帶上與當初要求授權時一模一樣的 `redirectUri`。
*   **回應 (application/json)**：
    ```json
    {
        "token": "JWT Token",
        "isNewUser": true,
        "user": {
            "id": 123,
            "displayName": "使用者名稱",
            "profileImageUrl": "使用者頭像 URL",
            "hasCostcoMembership": false
        }
    }
    ```
    *   `token` (String): JWT 認證 Token。
    *   `isNewUser` (boolean): 是否為新註冊的使用者。
    *   `user` (Object): 使用者基本資訊。
        *   `id` (Long): 使用者 ID。
        *   `displayName` (String): 使用者顯示名稱。
        *   `profileImageUrl` (String): 使用者頭像 URL。
        *   `hasCostcoMembership` (Boolean): 是否擁有好市多會員資格。

#### 1.2 開發者後門登入 (開發環境專用)
*   **功能**：開發者專用，直接發放 JWT Token，正式上線前應刪除。
*   **端點**：`/api/v1/auth/dev-login`
*   **方法**：`GET`
*   **請求參數**：
    *   `userId` (Long, 可選): 指定登入的使用者 ID，預設為 `1`。
*   **回應 (application/json)**：
    ```json
    {
        "message": "開發者後門登入成功",
        "userId": "1",
        "token": "JWT Token"
    }
    ```
    *   `message` (String): 成功訊息。
    *   `userId` (String): 登入的使用者 ID。
    *   `token` (String): JWT 認證 Token。

### 2. 合購活動相關 (CampaignController)

#### 2.1 取得合購單列表
*   **功能**：取得所有活躍中的合購單列表，可依門市篩選，並支援分頁。
*   **端點**：`/api/v1/campaigns`
*   **方法**：`GET`
*   **請求參數**：
    *   `storeId` (Integer, 可選): 門市 ID，用於篩選特定門市的合購單。
    *   `page` (int, 可選): 頁碼，預設為 `0`。
    *   `size` (int, 可選): 每頁顯示筆數，預設為 `10`。
*   **回應 (application/json)**：
    ```json
    {
        "content": [
            {
                "id": 1,
                "itemName": "美味鮭魚排",
                "itemImageUrl": "https://example.com/salmon.jpg",
                "pricePerUnit": 300,
                "totalQuantity": 5,
                "availableQuantity": 2,
                "meetupLocation": "中和好市多熟食區",
                "meetupTime": "2026-03-17T18:00:00",
                "status": "OPEN",
                "storeName": "中和店",
                "categoryName": "生鮮",
                "host": {
                    "id": 101,
                    "displayName": "團主A",
                    "profileImageUrl": "https://example.com/host_a.jpg",
                    "creditScore": 95
                }
            }
        ],
        "pageable": { ... }, // Spring Data Page 相關資訊
        "totalPages": 10,
        "totalElements": 100,
        "size": 10,
        "number": 0,
        "numberOfElements": 10,
        "first": true,
        "last": false,
        "empty": false
    }
    ```
    *   `content` (Array of CampaignSummaryResponse): 合購單摘要列表。
        *   `id` (Long): 合購單 ID。
        *   `itemName` (String): 商品名稱。
        *   `itemImageUrl` (String): 商品圖片 URL。
        *   `pricePerUnit` (Integer): 每單位價格。
        *   `totalQuantity` (Integer): 總數量。
        *   `availableQuantity` (Integer): 可認購數量。
        *   `meetupLocation` (String): 面交地點。
        *   `meetupTime` (LocalDateTime): 面交時間。
        *   `status` (String): 合購單狀態 (例如 "OPEN", "FULL", "CLOSED", "CANCELLED")。
        *   `storeName` (String): 門市名稱。
        *   `categoryName` (String): 商品分類名稱。
        *   `host` (Object): 團主資訊。
            *   `id` (Long): 團主 ID。
            *   `displayName` (String): 團主顯示名稱。
            *   `profileImageUrl` (String): 團主頭像 URL。
            *   `creditScore` (Integer): 團主信用分數。

#### 2.2 發起合購單
*   **功能**：使用者發起一個新的合購活動。
*   **端點**：`/api/v1/campaigns`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **請求體 (application/json)**：
    ```json
    {
        "storeId": 1,
        "categoryId": 10,
        "scenarioType": "INSTANT",
        "itemName": "好市多自有品牌衛生紙",
        "itemImageUrl": "https://example.com/tissue.jpg",
        "pricePerUnit": 100,
        "totalQuantity": 12,
        "meetupLocation": "中和店出口處",
        "meetupTime": "2026-03-17T19:00:00",
        "expireTime": "2026-03-17T18:30:00"
    }
    ```
    *   `storeId` (Integer): 門市 ID。
    *   `categoryId` (Integer): 商品分類 ID。
    *   `scenarioType` (String): 合購情境類型 ("INSTANT" (即時) 或 "SCHEDULED" (預約))。
    *   `itemName` (String): 商品名稱。
    *   `itemImageUrl` (String, 可選): 商品圖片 URL。
    *   `pricePerUnit` (Integer): 每單位價格。
    *   `totalQuantity` (Integer): 總數量。
    *   `meetupLocation` (String): 面交地點。
    *   `meetupTime` (LocalDateTime): 面交時間。
    *   `expireTime` (LocalDateTime): 合購單過期時間。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "合購單發起成功！"
    }
    ```

#### 2.3 加入合購單
*   **功能**：使用者加入一個現有的合購活動。
*   **端點**：`/api/v1/campaigns/{id}/join`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **路徑參數**：
    *   `id` (Long): 合購單 ID。
*   **請求體 (application/json)**：
    ```json
    {
        "quantity": 3
    }
    ```
    *   `quantity` (Integer): 認購數量 (最少為 1)。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "成功加入合購！"
    }
    ```

#### 2.4 退出合購單
*   **功能**：使用者退出已加入的合購活動。
*   **端點**：`/api/v1/campaigns/{id}/withdraw`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **路徑參數**：
    *   `id` (Long): 合購單 ID。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "您已成功退出合購，相關紀錄已更新。"
    }
    ```

#### 2.5 團主宣告已面交
*   **功能**：團主標記合購單為已面交，等待團員確認收貨。
*   **端點**：`/api/v1/campaigns/{id}/deliver`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **路徑參數**：
    *   `id` (Long): 合購單 ID。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "已成功更改為待確認狀態，請等待團員點擊收貨。"
    }
    ```

#### 2.6 團員確認收貨
*   **功能**：團員確認已收到合購商品。
*   **端點**：`/api/v1/campaigns/{id}/confirm`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **路徑參數**：
    *   `id` (Long): 合購單 ID。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "您已確認收貨！感謝您的參與。"
    }
    ```

#### 2.7 團主主動取消合購單
*   **功能**：團主取消自己發起的合購單。
*   **端點**：`/api/v1/campaigns/{id}/cancel`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **路徑參數**：
    *   `id` (Long): 合購單 ID。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "合購單已成功取消。若有團員已加入，您的信用分數將會受到相應的扣除。"
    }
    ```

### 3. 參考數據相關 (ReferenceDataController)

#### 3.1 取得所有營業中的門市
*   **功能**：取得所有營業中的好市多門市列表。
*   **端點**：`/api/v1/stores`
*   **方法**：`GET`
*   **回應 (application/json)**：
    ```json
    [
        {
            "id": 1,
            "name": "中和店",
            "address": "新北市中和區中山路二段347號"
        },
        {
            "id": 2,
            "name": "內湖店",
            "address": "台北市內湖區舊宗路一段268號"
        }
    ]
    ```
    *   `id` (Integer): 門市 ID。
    *   `name` (String): 門市名稱。
    *   `address` (String): 門市地址。

#### 3.2 取得所有商品分類
*   **功能**：取得所有商品分類列表。
*   **端點**：`/api/v1/categories`
*   **方法**：`GET`
*   **回應 (application/json)**：
    ```json
    [
        {
            "id": 1,
            "name": "生鮮",
            "icon": "🥩"
        },
        {
            "id": 2,
            "name": "日用品",
            "icon": "🧻"
        }
    ]
    ```
    *   `id` (Integer): 分類 ID。
    *   `name` (String): 分類名稱。
    *   `icon` (String): 分類圖示 (Emoji)。

### 4. 評價相關 (ReviewController)

#### 4.1 創建評價
*   **功能**：使用者對已完成的合購單中的另一方進行評價。
*   **端點**：`/api/v1/reviews`
*   **方法**：`POST`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **請求體 (application/json)**：
    ```json
    {
        "campaignId": 123,
        "revieweeId": 456,
        "rating": 5,
        "comment": "團主很準時，商品包裝也很好，推！"
    }
    ```
    *   `campaignId` (Long): 發生交易的合購單 ID。
    *   `revieweeId` (Long): 被評價的人的 ID。
    *   `rating` (Integer): 評分 (1 ~ 5)。
    *   `comment` (String, 可選): 評價留言。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "評價成功！感謝您的回饋。"
    }
    ```

### 5. 使用者相關 (UserController)

#### 5.1 更新個人資料
*   **功能**：更新當前使用者的個人資料。
*   **端點**：`/api/v1/users/me`
*   **方法**：`PUT`
*   **請求頭**：`Authorization: Bearer <JWT_TOKEN>`
*   **請求體 (application/json)**：
    ```json
    {
        "displayName": "新的顯示名稱",
        "hasCostcoMembership": true
    }
    ```
    *   `displayName` (String, 可選): 新的顯示名稱。
    *   `hasCostcoMembership` (Boolean, 可選): 是否擁有好市多會員資格。
*   **回應 (application/json)**：
    ```json
    {
        "success": true,
        "message": "個人資料更新成功"
    }
    ```

---

## 錯誤回應格式 (ErrorResponse)

所有 API 發生錯誤時的通用回應格式。
```json
{
    "timestamp": "2026-03-17T10:30:00",
    "status": 400,
    "error": "Bad Request",
    "message": "請求參數無效",
    "path": "/api/v1/campaigns"
}
```
*   `timestamp` (LocalDateTime): 發生錯誤的時間。
*   `status` (int): HTTP 狀態碼 (例如 400, 404, 500)。
*   `error` (String): 錯誤主旨 (例如 "Bad Request", "Not Found", "Internal Server Error")。
*   `message` (String): 給使用者看的白話文錯誤訊息。
*   `path` (String): 發生錯誤的 API 路徑。
