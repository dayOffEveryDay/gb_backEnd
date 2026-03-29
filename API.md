# API.md

本文件整理目前 `gb_backEnd` 專案已實作、以及這次工作區新增但尚未提交的 API / WebSocket 介面。內容以 `controller`、`dto`、`service` 與目前程式碼行為為準。

## 共通說明

- Base Path: `/api/v1`
- 驗證方式: 除登入與基礎資料查詢外，其餘 REST API 需帶 `Authorization: Bearer <JWT_TOKEN>`
- 成功回傳格式:
  - 查詢型 API 直接回傳 DTO 或分頁物件
  - 動作型 API 多數回傳 `{ "success": true, "message": "..." }`
- 錯誤處理: 由全域例外處理器統一回傳錯誤內容

---

## 1. Auth API

### 1.1 LINE 登入

- 路徑: `/api/v1/auth/line`
- Method: `POST`
- Content-Type: `application/json`
- 說明: 以 LINE OAuth code 交換 access token，取得 LINE 使用者資料，建立或更新本地會員，最後回傳 JWT
- 請求參數:

```json
{
  "code": "line-oauth-code",
  "redirectUri": "https://example.com/callback"
}
```

- 返回內容: `AuthResponse`

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
- 返回內容:

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
- 返回內容: `Page<CampaignSummaryResponse>`

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
- 請求參數:
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
- 返回內容:

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
- Content-Type: `application/json`
- 請求參數:

```json
{
  "quantity": 2
}
```

- 返回內容:

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
- Content-Type: `application/json`
- 請求參數:

```json
{
  "quantity": 1
}
```

- 說明:
  - `quantity` 必須大於 `0`
  - 修改後參與數量不得小於 `1`
  - 若原本滿單且釋出名額，狀態會從 `FULL` 回到 `OPEN`
- 返回內容:

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
- 說明: 取消目前使用者在該合購中的參與，會釋出庫存並累加 `participantCancelCount`
- 返回內容:

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
- 返回內容:

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
- 說明: 參與者確認收貨後，自身狀態變成 `CONFIRMED`；若所有團員都確認，整張單轉為 `COMPLETED`
- 返回內容:

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
- 說明: 僅團主可取消 `OPEN` 或 `FULL` 狀態的合購；若已有團員，會依狀態扣團主信用分，並批次改寫團員狀態
- 返回內容:

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
- 返回內容: `Page<CreditLogResponse>`

```json
{
  "content": [
    {
      "id": 1,
      "scoreChange": -10,
      "reason": "超過 24 小時未處理，系統判定放鳥",
      "campaignId": 99,
      "createdAt": "2026-03-27T10:00:00"
    }
  ],
  "totalPages": 1,
  "totalElements": 1,
  "size": 10,
  "number": 0
}
```

### 2.10 查詢我開的合購

- 路徑: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Query:
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- 返回內容: `Page<CampaignSummaryResponse>`

### 2.11 查詢我參加的合購

- 路徑: `/api/v1/campaigns/my-joined`
- Method: `GET`
- Query:
  - `page` `int`, optional, 預設 `0`
  - `size` `int`, optional, 預設 `10`
- 返回內容: `Page<CampaignSummaryResponse>`

### 2.12 取得聊天室歷史訊息

- 路徑: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- Path Variable:
  - `campaignId` `Long`
- 說明:
  - 僅團主或該團參與者可查看
  - 依建立時間升冪回傳，舊訊息在前
- 返回內容: `List<ChatMessageResponse>`

```json
[
  {
    "senderId": 2,
    "senderName": "Howard",
    "content": "我大概 7 點到",
    "timestamp": "2026-03-29T18:30:15"
  },
  {
    "senderId": 10,
    "senderName": "Host A",
    "content": "好，熟食區門口見",
    "timestamp": "2026-03-29T18:31:08"
  }
]
```

---

## 3. Reference Data API

### 3.1 取得啟用中的賣場

- 路徑: `/api/v1/stores`
- Method: `GET`
- 說明: 回傳所有 `isActive = true` 的賣場
- 返回內容: `List<StoreResponse>`

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
- 返回內容: `List<CategoryResponse>`

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
- Content-Type: `application/json`
- 請求參數:

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
- 返回內容:

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
- Content-Type: `application/json`
- 請求參數:

```json
{
  "displayName": "新暱稱",
  "hasCostcoMembership": true
}
```

- 說明: 兩個欄位皆可單獨更新，未提供的欄位不變
- 返回內容:

```json
{
  "success": true,
  "message": "個人資料更新成功"
}
```

---

## 6. WebSocket / STOMP

這次工作區新增了聊天室與即時通知推播，通訊方式不是 REST，而是 STOMP over WebSocket。

### 6.1 建立 WebSocket 連線

- 端點: `/ws`
- 協定: WebSocket + SockJS fallback
- 連線 Header:
  - `Authorization: Bearer <JWT_TOKEN>`
- 說明:
  - 前端先連到 `/ws`
  - 後端 `application destination prefix` 為 `/app`
  - 群播 broker prefix 為 `/topic`
  - 個人通知 broker prefix 為 `/user/queue`

### 6.2 發送聊天室訊息

- Send Destination: `/app/chat/{campaignId}/sendMessage`
- 對應後端: `@MessageMapping("/chat/{campaignId}/sendMessage")`
- 請求參數: `ChatMessageRequest`

```json
{
  "content": "我五分鐘後到"
}
```

- 返回內容 / 廣播內容: `ChatMessageResponse`
- Broadcast Topic: `/topic/campaigns/{campaignId}`

```json
{
  "senderId": 2,
  "senderName": "Howard",
  "content": "我五分鐘後到",
  "timestamp": "2026-03-29T18:45:20"
}
```

### 6.3 訂閱聊天室

- Subscribe Topic: `/topic/campaigns/{campaignId}`
- 說明: 成功送出訊息後，後端會把 `ChatMessageResponse` 廣播到該團聊天室頻道

### 6.4 訂閱個人通知

- Subscribe Topic: `/user/queue/notifications`
- 說明:
  - 當合購因認購而滿單時，`NotificationService` 會寫入 `notifications` 資料表
  - 同時透過 `convertAndSendToUser` 推播給團主與所有團員
- 廣播內容範例:

```json
{
  "content": "您參與的合購單「鮭魚切片」已成團！請隨時留意聊天室訊息。",
  "type": "CAMPAIGN_FULL",
  "referenceId": 101
}
```

### 6.5 驗證流程

- `WebSocketAuthInterceptor` 會在 STOMP `CONNECT` 時解析 JWT
- `WebSocketConfig` 已將 interceptor 註冊到 inbound channel
- 驗證成功後會把 `userId` 放進 WebSocket session，供聊天室發言時使用

---

## 7. 狀態補充

### 7.1 Campaign 狀態

- `OPEN`: 可參加
- `FULL`: 已滿團
- `DELIVERED`: 團主已交付，等待參與者確認
- `COMPLETED`: 所有參與者已確認收貨
- `CANCELLED`: 團主取消
- `FAILED`: 已過期未成團
- `HOST_NO_SHOW`: 團主超時未處理

### 7.2 Participant 狀態

- `JOINED`: 已參加
- `CONFIRMED`: 已確認收貨
- `CANCELLED`: 主動退出
- `CANCELLED_BY_HOST`: 團主取消後由系統批次釋放
- `CANCELLED_BY_SYSTEM`: 團主超時未處理後由系統批次釋放
