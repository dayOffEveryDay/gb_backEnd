# API.md

本文檔依目前 `gb_backEnd` 程式碼實作整理，內容以 `controller`、`dto`、`service`、`security`、`websocket` 設定為準。

## 基本說明

- Base Path：`/api/v1`
- 伺服器連接埠：`8080`
- 圖片存取路徑：`/images/**`
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

其餘 REST API 皆需帶入：

```http
Authorization: Bearer <JWT_TOKEN>
```

## 通用錯誤格式

業務錯誤通常回傳 `400 Bad Request`：

```json
{
  "timestamp": "2026-04-01T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "錯誤訊息",
  "path": "/api/v1/xxx"
}
```

## 1. Auth API

### 1.1 LINE 登入

- Path：`/api/v1/auth/line`
- Method：`POST`
- Auth：不需要
- Content-Type：`application/json`

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

- Path：`/api/v1/auth/dev-login`
- Method：`GET`
- Auth：不需要

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

- Path：`/api/v1/stores`
- Method：`GET`
- Auth：不需要

### 2.2 取得分類清單

- Path：`/api/v1/categories`
- Method：`GET`
- Auth：不需要

## 3. Campaign API

### 3.1 查詢活動清單

- Path：`/api/v1/campaigns`
- Method：`GET`
- Auth：不需要

Query 參數：

- `storeId` `Integer`，optional
- `categoryId` `Integer`，optional
- `keyword` `String`，optional
- `page` `int`，optional，預設 `0`
- `size` `int`，optional，預設 `10`

回應：`Page<CampaignSummaryResponse>`

狀態值目前程式碼中會用到：

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

- Path：`/api/v1/campaigns`
- Method：`POST`
- Auth：需要 JWT
- Content-Type：`multipart/form-data`

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
- 實際活動 `totalQuantity` 會寫入 `openQuantity`
- 主揪自留數量會由 `productTotalQuantity - openQuantity` 計算

成功回應：

```json
{
  "success": true,
  "message": "活動建立成功"
}
```

### 3.3 主揪調整活動數量

- Path：`/api/v1/campaigns/{id}/host-revise`
- Method：`PUT`
- Auth：需要 JWT
- Content-Type：`application/json`

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

### 3.4 參加活動

- Path：`/api/v1/campaigns/{id}/join`
- Method：`POST`
- Auth：需要 JWT
- Content-Type：`application/json`

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
- 名額扣到 `0` 時活動狀態會轉為 `FULL`
- 轉為 `FULL` 時會通知主揪與所有 `JOINED` 團員

### 3.5 主揪開放滿單後修改

- Path：`/api/v1/campaigns/{id}/unlock`
- Method：`POST`
- Auth：需要 JWT

說明：

- 只有主揪可操作
- 只有 `FULL` 狀態可解鎖
- 解鎖後會把活動的 `allowRevision` 設為 `true`
- 解鎖後團員可在滿單狀態下修改數量或退出

成功回應：

```json
{
  "success": true,
  "message": "已開放修改權限，團員現在可以自行調整數量或退出了。"
}
```

### 3.6 修改參與數量

- Path：`/api/v1/campaigns/{id}/revise`
- Method：`POST`
- Auth：需要 JWT
- Content-Type：`application/json`

請求：

```json
{
  "quantity": 1
}
```

說明：

- 這裡的 `quantity` 是「要減少的數量」，不是修改後的最終數量
- 修改後個人參與數量必須仍然 `>= 1`
- 可修改條件：
  - 活動為 `OPEN`
  - 或活動雖為 `FULL`，但主揪已執行 `/unlock`
- 只允許目前狀態為 `JOINED` 的團員修改
- 一旦釋出名額，活動會轉回 `OPEN`
- 轉回 `OPEN` 時 `allowRevision` 會自動關閉

### 3.7 退出活動

- Path：`/api/v1/campaigns/{id}/withdraw`
- Method：`POST`
- Auth：需要 JWT

說明：

- 只允許目前狀態為 `JOINED` 的團員退出
- 可退出條件：
  - 活動為 `OPEN`
  - 或活動雖為 `FULL`，但主揪已執行 `/unlock`
- 超過 `expireTime` 後不可退出
- 退出後參與者狀態改為 `CANCELLED`
- 活動狀態會回到 `OPEN`
- `allowRevision` 會自動關閉
- 退出者的 `participantCancelCount` 會加 1
- 會通知主揪有團員退出

通知型別：

- `MEMBER_WITHDRAW`

### 3.8 主揪踢除團員

- Path：`/api/v1/campaigns/{id}/participants/{participantId}/kick`
- Method：`POST`
- Auth：需要 JWT

說明：

- 只有主揪可操作
- `participantId` 代表被踢除的使用者 ID
- 只允許踢除目前狀態為 `JOINED` 的團員
- 被踢除後：
  - 團員狀態改為 `CANCELLED`
  - 名額會釋出
  - 活動狀態會回到 `OPEN`
  - `allowRevision` 會自動關閉
- 被踢除者會收到通知

通知型別：

- `KICKED`

成功回應：

```json
{
  "success": true,
  "message": "已成功踢除該名團員，並將合購單退回招募狀態。"
}
```

### 3.9 主揪標記已交付

- Path：`/api/v1/campaigns/{id}/deliver`
- Method：`POST`
- Auth：需要 JWT

### 3.10 參與者確認收貨

- Path：`/api/v1/campaigns/{id}/confirm`
- Method：`POST`
- Auth：需要 JWT

### 3.11 主揪取消活動

- Path：`/api/v1/campaigns/{id}/cancel`
- Method：`POST`
- Auth：需要 JWT

說明：

- 只有主揪可操作
- 活動取消後狀態改為 `CANCELLED`
- 若已有參與者，會通知被取消的團員

通知型別：

- `CAMPAIGN_CANCELLED`

### 3.12 查詢我的信用分紀錄

- Path：`/api/v1/campaigns/me/credit-logs`
- Method：`GET`
- Auth：需要 JWT

### 3.13 查詢我主揪的活動

- Path：`/api/v1/campaigns/my-hosted`
- Method：`GET`
- Auth：需要 JWT

### 3.14 查詢我參加的活動

- Path：`/api/v1/campaigns/my-joined`
- Method：`GET`
- Auth：需要 JWT

### 3.15 查詢主揪活動儀表板

- Path：`/api/v1/campaigns/{id}/host-dashboard`
- Method：`GET`
- Auth：需要 JWT

回應：`HostDashboardResponse`

主要欄位：

- `campaignId`
- `itemName`
- `status`
- `totalPhysicalQuantity`
- `openQuantity`
- `hostReservedQuantity`
- `alreadySoldQuantity`
- `availableQuantity`
- `participants`

### 3.16 查詢我在該活動的參與狀態

- Path：`/api/v1/campaigns/{id}/participants/me`
- Method：`GET`
- Auth：需要 JWT

回應：`MyParticipationResponse`

## 4. User API

### 4.1 更新個人資料

- Path：`/api/v1/users/me`
- Method：`PUT`
- Auth：需要 JWT

## 5. Review API

### 5.1 新增評價

- Path：`/api/v1/reviews`
- Method：`POST`
- Auth：需要 JWT

## 6. Notification API

### 6.1 查詢未讀通知

- Path：`/api/v1/notifications/unread`
- Method：`GET`
- Auth：需要 JWT

### 6.2 標記通知已讀

- Path：`/api/v1/notifications/{id}/read`
- Method：`PUT`
- Auth：需要 JWT

## 7. Chat API

### 7.1 查詢聊天室歷史訊息

- Path：`/api/v1/campaigns/{campaignId}/chat-messages`
- Method：`GET`
- Auth：需要 JWT

## 8. WebSocket / STOMP

### 8.1 連線方式

- Endpoint：`/ws`
- 支援 SockJS
- Client 傳送前綴：`/app`
- 訂閱前綴：`/topic`
- 使用者私有佇列前綴：`/user`

連線時需在 STOMP `CONNECT` header 帶上：

```http
Authorization: Bearer <JWT_TOKEN>
```

後端會從 JWT 解析 `userId`，並同時：

- 寫入 WebSocket Session
- 綁定為 `Principal`

因此使用 `convertAndSendToUser(...)` 時，通知會送往：

- `/user/queue/notifications`

### 8.2 傳送聊天室訊息

- Client Send To：`/app/chat/{campaignId}/sendMessage`

### 8.3 使用者通知推播

- Subscribe：`/user/queue/notifications`

推播 payload 目前至少包含：

- `content`
- `type`
- `referenceId`

目前程式已使用的通知型別：

- `CAMPAIGN_FULL`
- `CAMPAIGN_CANCELLED`
- `MEMBER_WITHDRAW`
- `KICKED`
