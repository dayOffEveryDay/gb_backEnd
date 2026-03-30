# API.md

本文檔依目前 `gb_backEnd` 程式碼實作整理，內容以 `controller`、`dto`、`security`、`websocket` 設定為準。

## 基本說明

- Base Path: `/api/v1`
- 伺服器連接埠：`8080`
- 圖片存取前綴：`/images/**`
- WebSocket STOMP 端點：`/ws`
- REST API 預設回傳 `application/json`

## 驗證規則

以下介面可匿名存取：

- `POST /api/v1/auth/line`
- `GET /api/v1/auth/dev-login`
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `/images/**`
- `/ws/**`

其餘 REST API 都需要帶入：

```http
Authorization: Bearer <JWT_TOKEN>
```

## 通用錯誤格式

業務錯誤通常回傳 `400 Bad Request`：

```json
{
  "timestamp": "2026-03-30T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "錯誤訊息",
  "path": "/api/v1/xxx"
}
```

驗證錯誤也會回傳 `400`：

```json
{
  "timestamp": "2026-03-30T12:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "參數驗證失敗: [數量至少為 1]",
  "path": "/api/v1/campaigns/1/join"
}
```

## 1. Auth API

### 1.1 LINE 登入

- Path: `/api/v1/auth/line`
- Method: `POST`
- Auth: 不需要
- Content-Type: `application/json`

請求：

```json
{
  "code": "line-oauth-code",
  "redirectUri": "http://localhost:5173/login/callback"
}
```

回應：`AuthResponse`

```json
{
  "token": "jwt-token",
  "newUser": true,
  "user": {
    "id": 1,
    "displayName": "A Hui",
    "profileImageUrl": "https://profile.line-scdn.net/xxx",
    "hasCostcoMembership": false
  }
}
```

### 1.2 開發用登入

- Path: `/api/v1/auth/dev-login`
- Method: `GET`
- Auth: 不需要

Query 參數：

- `userId` `Long`，optional，預設 `1`

回應：

```json
{
  "message": "開發用登入成功",
  "userId": "1",
  "token": "jwt-token"
}
```

## 2. Reference Data API

### 2.1 取得門市清單

- Path: `/api/v1/stores`
- Method: `GET`
- Auth: 不需要

回應：`StoreResponse[]`

```json
[
  {
    "id": 1,
    "name": "Costco 台中店",
    "address": "台中市南屯區...",
    "openTime": "10:00",
    "closeTime": "21:30"
  }
]
```

### 2.2 取得分類清單

- Path: `/api/v1/categories`
- Method: `GET`
- Auth: 不需要

回應：`CategoryResponse[]`

```json
[
  {
    "id": 1,
    "name": "生鮮",
    "icon": "🥬"
  }
]
```

## 3. Campaign API

### 3.1 查詢活動清單

- Path: `/api/v1/campaigns`
- Method: `GET`
- Auth: 不需要

Query 參數：

- `storeId` `Integer`，optional
- `categoryId` `Integer`，optional
- `keyword` `String`，optional
- `page` `int`，optional，預設 `0`
- `size` `int`，optional，預設 `10`

回應：`Page<CampaignSummaryResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "itemName": "牛小排",
      "imageUrls": [
        "http://localhost:8080/images/a.jpg"
      ],
      "pricePerUnit": 300,
      "totalQuantity": 20,
      "availableQuantity": 8,
      "meetupLocation": "台中店停車場",
      "meetupTime": "2026-03-30T18:00:00",
      "expireTime": "2026-03-30T16:00:00",
      "status": "OPEN",
      "scenarioType": "SCHEDULED",
      "storeName": "Costco 台中店",
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

目前程式碼中會用到的活動狀態：

- `OPEN`
- `FULL`
- `DELIVERED`
- `COMPLETED`
- `CANCELLED`
- `HOST_NO_SHOW`

`scenarioType` 目前請求端使用值：

- `INSTANT`
- `SCHEDULED`

### 3.2 建立活動

- Path: `/api/v1/campaigns`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `multipart/form-data`

表單欄位：

- `storeId` `Integer`
- `categoryId` `Integer`
- `scenarioType` `String`
- `itemName` `String`
- `pricePerUnit` `Integer`
- `productTotalQuantity` `Integer`
- `openQuantity` `Integer`
- `meetupLocation` `String`
- `meetupTime` `LocalDateTime`
- `expireTime` `LocalDateTime`
- `images` `List<MultipartFile>`，optional，最多 3 張

說明：

- 只有 `hasCostcoMembership = true` 的使用者可以開團
- `openQuantity` 不能大於 `productTotalQuantity`
- 實際活動的 `totalQuantity` 會寫入 `openQuantity`
- 主揪自留數量會由 `productTotalQuantity - openQuantity` 計算

成功回應：

```json
{
  "success": true,
  "message": "活動建立成功"
}
```

### 3.3 主揪調整活動數量

- Path: `/api/v1/campaigns/{id}/host-revise`
- Method: `PUT`
- Auth: 需要 JWT
- Content-Type: `application/json`

請求：

```json
{
  "newProductTotalQuantity": 36,
  "newOpenQuantity": 20
}
```

說明：

- 只有主揪可操作
- 只有 `OPEN` 狀態可調整
- `newOpenQuantity` 不能大於 `newProductTotalQuantity`
- `newOpenQuantity` 不能小於目前已售出數量
- 更新後：
  - `totalQuantity = newOpenQuantity`
  - `hostReservedQuantity = newProductTotalQuantity - newOpenQuantity`
  - `availableQuantity = newOpenQuantity - alreadySoldQuantity`
- 若更新後 `availableQuantity = 0`，活動狀態會改為 `FULL`

成功回應：

```json
{
  "success": true,
  "message": "活動數量調整成功"
}
```

### 3.4 參加活動

- Path: `/api/v1/campaigns/{id}/join`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

請求：

```json
{
  "quantity": 2
}
```

說明：

- `quantity` 必須 `>= 1`
- 不能參加自己開的團
- 只有 `OPEN` 狀態可參加
- 過期後不可參加

成功回應：

```json
{
  "success": true,
  "message": "成功參加活動"
}
```

### 3.5 修改參與數量

- Path: `/api/v1/campaigns/{id}/revise`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

請求：

```json
{
  "quantity": 1
}
```

說明：

- 這裡的 `quantity` 是「要減少的數量」，不是修改後的最終數量
- 修改後個人參與數量必須仍然 `>= 1`
- 活動狀態必須為 `OPEN` 或 `FULL`
- 若活動原本為 `FULL`，修改後有名額會自動改回 `OPEN`

成功回應：

```json
{
  "success": true,
  "message": "修改成功，已更新參與數量"
}
```

### 3.6 退出活動

- Path: `/api/v1/campaigns/{id}/withdraw`
- Method: `POST`
- Auth: 需要 JWT

說明：

- 僅參與者可退出
- 僅 `OPEN` 或 `FULL` 狀態可退出
- 超過 `expireTime` 後不可退出
- 退出後參與者狀態改為 `CANCELLED`
- 使用者的 `participantCancelCount` 會加 1
- 活動名額會歸還，必要時自動重開

成功回應：

```json
{
  "success": true,
  "message": "退出成功，活動名額已釋出"
}
```

### 3.7 主揪標記已交付

- Path: `/api/v1/campaigns/{id}/deliver`
- Method: `POST`
- Auth: 需要 JWT

說明：

- 只有主揪可操作
- 活動狀態必須為 `OPEN` 或 `FULL`
- 至少要有一位 `JOINED` 參與者
- 成功後活動狀態改為 `DELIVERED`

成功回應：

```json
{
  "success": true,
  "message": "已標記為已交付，等待參與者確認收貨"
}
```

### 3.8 參與者確認收貨

- Path: `/api/v1/campaigns/{id}/confirm`
- Method: `POST`
- Auth: 需要 JWT

說明：

- 活動狀態必須為 `DELIVERED`
- 僅 `JOINED` 參與者可確認
- 參與者確認後個人狀態改為 `CONFIRMED`
- 當所有參與者都確認後，活動狀態改為 `COMPLETED`

成功回應：

```json
{
  "success": true,
  "message": "確認收貨成功"
}
```

### 3.9 主揪取消活動

- Path: `/api/v1/campaigns/{id}/cancel`
- Method: `POST`
- Auth: 需要 JWT

說明：

- 只有主揪可操作
- 活動狀態必須為 `OPEN` 或 `FULL`
- 成功後活動狀態改為 `CANCELLED`
- 若已有參與者：
  - `FULL` 狀態取消會扣主揪信用分 `5`
  - `OPEN` 狀態取消會扣主揪信用分 `2`
  - 所有參與者會被批次取消

成功回應：

```json
{
  "success": true,
  "message": "活動已取消，參與者已收到釋出或取消結果"
}
```

### 3.10 查詢我的信用分紀錄

- Path: `/api/v1/campaigns/me/credit-logs`
- Method: `GET`
- Auth: 需要 JWT

分頁參數：

- `page` `int`，optional，預設 `0`
- `size` `int`，optional，預設 `10`
- `sort` `String`，optional

回應：`Page<CreditLogResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "scoreChange": -2,
      "reason": "主揪取消活動(OPEN)",
      "campaignId": 12,
      "createdAt": "2026-03-30T12:00:00"
    }
  ]
}
```

### 3.11 查詢我主揪的活動

- Path: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Auth: 需要 JWT

分頁參數：

- `page` `int`，optional，預設 `0`
- `size` `int`，optional，預設 `10`
- `sort` `String`，optional

回應：`Page<CampaignSummaryResponse>`

### 3.12 查詢我參加的活動

- Path: `/api/v1/campaigns/my-joined`
- Method: `GET`
- Auth: 需要 JWT

分頁參數：

- `page` `int`，optional，預設 `0`
- `size` `int`，optional，預設 `10`
- `sort` `String`，optional

回應：`Page<CampaignSummaryResponse>`

### 3.13 查詢主揪活動儀表板

- Path: `/api/v1/campaigns/{id}/host-dashboard`
- Method: `GET`
- Auth: 需要 JWT

說明：

- 只有主揪可查看
- 會回傳活動銷售摘要與所有參與者清單

回應：`HostDashboardResponse`

```json
{
  "campaignId": 1,
  "itemName": "牛小排",
  "status": "OPEN",
  "totalPhysicalQuantity": 36,
  "openQuantity": 20,
  "hostReservedQuantity": 16,
  "alreadySoldQuantity": 12,
  "availableQuantity": 8,
  "participants": [
    {
      "userId": 101,
      "displayName": "User A",
      "quantity": 2,
      "status": "JOINED"
    }
  ]
}
```

### 3.14 查詢我在該活動的參與狀態

- Path: `/api/v1/campaigns/{id}/participants/me`
- Method: `GET`
- Auth: 需要 JWT

回應：`MyParticipationResponse`

```json
{
  "host": false,
  "joined": true,
  "quantity": 2
}
```

說明：

- 若自己是主揪，則 `host = true`
- 若未參加，則 `joined = false`，`quantity = 0`

## 4. User API

### 4.1 更新個人資料

- Path: `/api/v1/users/me`
- Method: `PUT`
- Auth: 需要 JWT
- Content-Type: `application/json`

請求：

```json
{
  "displayName": "A Hui",
  "hasCostcoMembership": true
}
```

欄位可部分更新，傳入 `null` 的欄位不會被修改。

成功回應：

```json
{
  "success": true,
  "message": "個人資料更新成功"
}
```

## 5. Review API

### 5.1 新增評價

- Path: `/api/v1/reviews`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

請求：

```json
{
  "campaignId": 1,
  "revieweeId": 2,
  "rating": 5,
  "comment": "交易順利"
}
```

說明：

- 不可評價自己
- `rating` 只能是 `1` 到 `5`
- 同一 `campaignId + reviewerId + revieweeId` 只能評價一次
- 信用分規則：
  - `5` 星：`+1`
  - `1` 星：`-3`
  - 其他星等：`0`
- 正向評價有 7 天去重限制：同一評價人對同一對象，7 天內再次給 `5` 星不會再加分

成功回應：

```json
{
  "success": true,
  "message": "評價成功"
}
```

## 6. Notification API

### 6.1 查詢未讀通知

- Path: `/api/v1/notifications/unread`
- Method: `GET`
- Auth: 需要 JWT

回應：`NotificationResponse[]`

```json
[
  {
    "id": 1,
    "type": "CAMPAIGN_FULL",
    "referenceId": 99,
    "content": "你的活動已滿團",
    "createdAt": "2026-03-30T12:00:00"
  }
]
```

### 6.2 標記通知已讀

- Path: `/api/v1/notifications/{id}/read`
- Method: `PUT`
- Auth: 需要 JWT

成功回應：

```json
{
  "success": true,
  "message": "已標記為已讀"
}
```

## 7. Chat API

### 7.1 查詢聊天室歷史訊息

- Path: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- Auth: 需要 JWT

說明：

- 只有主揪或參與者可以查看

回應：`ChatMessageResponse[]`

```json
[
  {
    "senderId": 1,
    "senderName": "A Hui",
    "content": "我大概六點到",
    "timestamp": "2026-03-30T18:05:00"
  }
]
```

## 8. WebSocket / STOMP

### 8.1 連線方式

- Endpoint: `/ws`
- 支援 SockJS
- Client 傳送前綴：`/app`
- 訂閱前綴：`/topic`
- 使用者私有佇列前綴：`/user`

連線時需在 STOMP `CONNECT` header 帶上：

```http
Authorization: Bearer <JWT_TOKEN>
```

後端會從 JWT 解析 `userId` 並寫入 WebSocket Session。

### 8.2 傳送聊天室訊息

- Client Send To: `/app/chat/{campaignId}/sendMessage`

請求：

```json
{
  "content": "大家幾點到？"
}
```

說明：

- 只有主揪或參與者可以發送

廣播目的地：

- Subscribe: `/topic/campaigns/{campaignId}`

廣播內容：`ChatMessageResponse`

```json
{
  "senderId": 1,
  "senderName": "A Hui",
  "content": "大家幾點到？",
  "timestamp": "2026-03-30T18:10:00"
}
```

### 8.3 使用者通知推播

後端會使用使用者專屬佇列推播通知：

- Subscribe: `/user/queue/notifications`

推播內容範例：

```json
{
  "content": "你的活動已滿團",
  "type": "CAMPAIGN_FULL",
  "referenceId": 99
}
```

## 9. 目前未實作或不應寫入文件的介面

以下內容在目前 controller 中沒有對應 REST endpoint，不應再視為正式 API：

- `GET /api/v1/campaigns/{id}` 單筆活動詳情
- 其他未在本文檔列出的 CRUD 介面
