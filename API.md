# API.md

本文件整理目前專案 `gb_backEnd` 已實作的 API，內容以 `controller`、`dto` 與實際 service 行為為準。

## 共通說明

- Base Path: `/api/v1`
- 驗證方式: 除登入與基礎資料查詢外，其餘 API 需帶 `Authorization: Bearer <JWT_TOKEN>`
- 成功回傳格式:
  - 查詢型 API 直接回傳 DTO 或分頁物件
  - 動作型 API 多數回傳 `{ "success": true, "message": "..." }`
- 錯誤處理: 由全域例外處理器統一回傳錯誤內容

---

## 1. Auth API

### 1.1 LINE 登入

- 路徑: `/api/v1/auth/line`
- Method: `POST`
- 說明: 以 LINE OAuth code 交換 access token，取得 LINE 使用者資料，建立或更新本地會員，最後回傳 JWT
- Request Body: `application/json`

```json
{
  "code": "line-oauth-code",
  "redirectUri": "https://example.com/callback"
}
```

- Response: `AuthResponse`

```json
{
  "token": "jwt-token",
  "isNewUser": true,
  "user": {
    "id": 1,
    "displayName": "A Hui",
    "profileImageUrl": "https://profile.line-scdn.net/xxx",
    "hasCostcoMembership": false
  }
}
```

### 1.2 開發用快速登入

- 路徑: `/api/v1/auth/dev-login`
- Method: `GET`
- Query:
  - `userId` `Long`, optional, 預設 `1`
- 說明: 直接為指定 `userId` 產生 JWT，方便本機或測試環境驗證流程
- Response:

```json
{
  "message": "開發者快速登入成功",
  "userId": "1",
  "token": "jwt-token"
}
```

---

## 2. Campaign API

### 2.1 取得合購列表

- 路徑: `/api/v1/campaigns`
- Method: `GET`
- Query:
  - `storeId` `Integer`, optional
  - `categoryId` `Integer`, optional
  - `keyword` `String`, optional
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- 說明: 取得符合條件的合購列表，依建立時間新到舊排序
- Response: `Page<CampaignSummaryResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "itemName": "鮭魚切片",
      "imageUrls": [
        "http://localhost:8080/images/uuid-a.jpg",
        "http://localhost:8080/images/uuid-b.jpg"
      ],
      "pricePerUnit": 300,
      "totalQuantity": 5,
      "availableQuantity": 2,
      "meetupLocation": "內湖店停車場",
      "meetupTime": "2026-03-27T18:00:00",
      "expireTime": "2026-03-27T16:00:00",
      "status": "OPEN",
      "scenarioType": "SCHEDULED",
      "storeName": "內湖店",
      "categoryName": "生鮮",
      "host": {
        "id": 10,
        "displayName": "Host A",
        "profileImageUrl": "https://example.com/a.jpg",
        "creditScore": 95
      }
    }
  ],
  "totalPages": 1,
  "totalElements": 1,
  "size": 10,
  "number": 0
}
```

### 2.2 建立合購

- 路徑: `/api/v1/campaigns`
- Method: `POST`
- Content-Type: `multipart/form-data`
- 說明: 建立新的合購單，可上傳最多 3 張圖片
- Form 欄位:
  - `storeId` `Integer`
  - `categoryId` `Integer`
  - `scenarioType` `String`
  - `itemName` `String`
  - `pricePerUnit` `Integer`
  - `totalQuantity` `Integer`
  - `meetupLocation` `String`
  - `meetupTime` `LocalDateTime`
  - `expireTime` `LocalDateTime`
  - `images` `List<MultipartFile>`, optional, 最多 3 張
- Response:

```json
{
  "success": true,
  "message": "合購建立成功"
}
```

### 2.3 參加合購

- 路徑: `/api/v1/campaigns/{id}/join`
- Method: `POST`
- Path Variable:
  - `id` `Long`: 合購 ID
- Request Body:

```json
{
  "quantity": 2
}
```

- 說明: 以目前登入者身份參加合購，`quantity` 至少為 `1`
- Response:

```json
{
  "success": true,
  "message": "成功參加合購"
}
```

### 2.4 修改參加數量

- 路徑: `/api/v1/campaigns/{id}/revise`
- Method: `POST`
- Path Variable:
  - `id` `Long`: 合購 ID
- Request Body:

```json
{
  "quantity": 1
}
```

- 說明: 將目前使用者在該合購中的參與數量往下調整指定數量
- 實際行為:
  - `quantity` 必須大於 `0`
  - 修改後參與數量不得小於 `1`
  - 若合購原本是 `FULL` 且釋出數量後有庫存，狀態會改回 `OPEN`
- Response:

```json
{
  "success": true,
  "message": "修改成功，已更新您的參與數量"
}
```

### 2.5 退出合購

- 路徑: `/api/v1/campaigns/{id}/withdraw`
- Method: `POST`
- Path Variable:
  - `id` `Long`
- 說明: 取消目前使用者在該合購中的參與
- Response:

```json
{
  "success": true,
  "message": "退出成功，已釋出您的參與數量"
}
```

### 2.6 團主標記已交付

- 路徑: `/api/v1/campaigns/{id}/deliver`
- Method: `POST`
- Path Variable:
  - `id` `Long`
- 說明: 僅團主可操作，且該合購必須為 `OPEN` 或 `FULL`
- Response:

```json
{
  "success": true,
  "message": "已標記為已交付，等待參與者確認"
}
```

### 2.7 參與者確認收貨

- 路徑: `/api/v1/campaigns/{id}/confirm`
- Method: `POST`
- Path Variable:
  - `id` `Long`
- 說明: 參與者確認收貨後，自己的參與狀態會改成 `CONFIRMED`；若所有參與者都確認，合購狀態會改成 `COMPLETED`
- Response:

```json
{
  "success": true,
  "message": "已確認收貨"
}
```

### 2.8 團主取消合購

- 路徑: `/api/v1/campaigns/{id}/cancel`
- Method: `POST`
- Path Variable:
  - `id` `Long`
- 說明: 僅團主可取消 `OPEN` 或 `FULL` 狀態的合購；若已有參與者，會依狀態扣團主信用分並釋放參與者
- Response:

```json
{
  "success": true,
  "message": "合購已取消，相關參與者已釋放"
}
```

### 2.9 查詢我的信用分紀錄

- 路徑: `/api/v1/campaigns/me/credit-logs`
- Method: `GET`
- Query:
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- Response: `Page<CreditLogResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "scoreChange": -10,
      "reason": "超過 24 小時未交付，視為團主失約",
      "campaignId": 99,
      "createdAt": "2026-03-27T10:00:00"
    }
  ]
}
```

### 2.10 查詢我開的合購

- 路徑: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Query:
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- Response: `Page<CampaignSummaryResponse>`

### 2.11 查詢我參加的合購

- 路徑: `/api/v1/campaigns/my-joined`
- Method: `GET`
- Query:
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- Response: `Page<CampaignSummaryResponse>`

---

## 3. Reference Data API

### 3.1 取得啟用中的賣場

- 路徑: `/api/v1/stores`
- Method: `GET`
- 說明: 回傳所有 `isActive = true` 的賣場
- Response: `List<StoreResponse>`

```json
[
  {
    "id": 1,
    "name": "內湖店",
    "address": "台北市內湖區舊宗路一段 268 號",
    "openTime": "10:00",
    "closeTime": "21:30"
  }
]
```

### 3.2 取得分類清單

- 路徑: `/api/v1/categories`
- Method: `GET`
- 說明: 依 `sortOrder` 升冪回傳分類
- Response: `List<CategoryResponse>`

```json
[
  {
    "id": 1,
    "name": "生鮮",
    "icon": "🥩"
  }
]
```

---

## 4. Review API

### 4.1 建立評價

- 路徑: `/api/v1/reviews`
- Method: `POST`
- Request Body:

```json
{
  "campaignId": 1,
  "revieweeId": 2,
  "rating": 5,
  "comment": "交付準時，商品狀況良好"
}
```

- 說明:
  - 不可評自己
  - `rating` 僅接受 `1` 到 `5`
  - 同一組 `campaignId + reviewerId + revieweeId` 不可重複評價
- Response:

```json
{
  "success": true,
  "message": "評價成功，感謝您的回饋"
}
```

---

## 5. User API

### 5.1 更新我的個人資料

- 路徑: `/api/v1/users/me`
- Method: `PUT`
- Request Body:

```json
{
  "displayName": "新暱稱",
  "hasCostcoMembership": true
}
```

- 說明: 兩個欄位皆可單獨更新，未提供的欄位不變
- Response:

```json
{
  "success": true,
  "message": "個人資料更新成功"
}
```

---

## 6. 主要狀態補充

### 6.1 Campaign 狀態

- `OPEN`: 可參加
- `FULL`: 已滿團
- `DELIVERED`: 團主已交付，等待參與者確認
- `COMPLETED`: 所有參與者已確認收貨
- `CANCELLED`: 團主取消或系統釋放
- `FAILED`: 已過期未成團
- `HOST_NO_SHOW`: 團主超時未交付

### 6.2 Participant 狀態

- `JOINED`: 已參加
- `CONFIRMED`: 已確認收貨
- `CANCELLED`: 已退出或被釋放
