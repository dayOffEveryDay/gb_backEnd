# API

本文件依據目前 `gb_backEnd` 程式碼整理，來源包含 `controller`、`dto`、`service`、`security` 與 `websocket` 設定。

## 基本資訊

- Base Path: `/api/v1`
- 預設 HTTP Port: `8080`
- 圖片路徑: `/images/**`
- WebSocket STOMP Endpoint: `/ws`
- REST API 預設回傳 `application/json`

## 認證規則

不需要 JWT 的 API：
- `POST /api/v1/auth/line`
- `GET /api/v1/auth/dev-login`
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `/images/**`
- `/ws/**`

其餘 REST API 需要帶：

```http
Authorization: Bearer <JWT_TOKEN>
```

## 錯誤格式

業務錯誤與驗證錯誤會由 `GlobalExceptionHandler` 統一處理。

```json
{
  "timestamp": "2026-04-06T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "錯誤訊息",
  "path": "/api/v1/xxx"
}
```

驗證失敗時 `error` 可能為 `Validation Failed`。未預期錯誤時會回 `500 Internal Server Error`。

## 1. Auth API

### 1.1 LINE Login

- Path: `/api/v1/auth/line`
- Method: `POST`
- Auth: 不需要
- Content-Type: `application/json`

Request Body:

```json
{
  "code": "line-oauth-code",
  "redirectUri": "http://localhost:5173/login/callback"
}
```

Response: `AuthResponse`

### 1.2 Dev Login

- Path: `/api/v1/auth/dev-login`
- Method: `GET`
- Auth: 不需要

Query Params:
- `userId` `Long`, optional, default `1`

Response:

```json
{
  "message": "dev login success",
  "userId": "1",
  "token": "jwt-token"
}
```

## 2. Reference Data API

### 2.1 取得門市清單

- Path: `/api/v1/stores`
- Method: `GET`
- Auth: 不需要

Response: `List<StoreResponse>`

### 2.2 取得分類清單

- Path: `/api/v1/categories`
- Method: `GET`
- Auth: 不需要

Response: `List<CategoryResponse>`

## 3. Campaign API

### 3.1 查詢開團列表

- Path: `/api/v1/campaigns`
- Method: `GET`
- Auth: 不需要

Query Params:
- `storeId` `Integer`, optional
- `categoryId` `Integer`, optional
- `keyword` `String`, optional
- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<CampaignSummaryResponse>`

主要欄位：
- `id`
- `itemName`
- `imageUrls`
- `pricePerUnit`
- `totalQuantity`
- `availableQuantity`
- `meetupLocation`
- `meetupTime`
- `expireTime`
- `status`
- `scenarioType`
- `storeName`
- `categoryName`
- `host.id`
- `host.displayName`
- `host.profileImageUrl`
- `host.creditScore`

`scenarioType` 目前使用值：
- `INSTANT`
- `SCHEDULED`

`campaign.status` 目前程式實際出現值：
- `OPEN`
- `FULL`
- `DELIVERED`
- `COMPLETED`
- `CANCELLED`
- `HOST_NO_SHOW`

`participant.status` 目前程式實際出現值：
- `JOINED`
- `CANCELLED`
- `KICKED`
- `CONFIRMED`
- `NO_SHOW`
- `DISPUTED`

### 3.2 建立開團

- Path: `/api/v1/campaigns`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `multipart/form-data`

Request Form Fields:
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
- `images` `List<MultipartFile>`, optional, 最多 `3` 張

程式行為：
- 只有 `hasCostcoMembership = true` 的使用者可以開團
- `openQuantity` 不可大於 `productTotalQuantity`
- 系統會計算 `hostReservedQuantity = productTotalQuantity - openQuantity`
- 實際存入 campaign 的 `totalQuantity` 為 `openQuantity`
- 圖片會存到 `uploads/campaigns/`，並以 UUID 檔名保存

### 3.3 團主修改開放數量

- Path: `/api/v1/campaigns/{id}/host-revise`
- Method: `PUT`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "newProductTotalQuantity": 36,
  "newOpenQuantity": 20
}
```

限制：
- 只有團主可操作
- 只允許 `OPEN`
- `newOpenQuantity` 不可大於 `newProductTotalQuantity`
- `newOpenQuantity` 不可小於目前已售數量

### 3.4 參加開團

- Path: `/api/v1/campaigns/{id}/join`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "quantity": 2
}
```

限制：
- `quantity >= 1`
- 不可加入自己開的團
- 只允許 `OPEN`
- 超過 `expireTime` 不可加入
- 庫存不足會失敗

### 3.5 團主開啟修改模式

- Path: `/api/v1/campaigns/{id}/unlock`
- Method: `POST`
- Auth: 需要 JWT

限制：
- 只有團主可操作
- 只允許 `FULL`

### 3.6 團員減少數量

- Path: `/api/v1/campaigns/{id}/revise`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "quantity": 1
}
```

限制：
- `quantity > 0`
- 需要有 `JOINED` 的參團紀錄
- 只允許 campaign 狀態為 `OPEN`，或 `allowRevision = true`
- 修改後個人數量不可小於 `1`

### 3.7 退出開團

- Path: `/api/v1/campaigns/{id}/withdraw`
- Method: `POST`
- Auth: 需要 JWT

限制：
- 需要有 `JOINED` 的參團紀錄
- 只允許 campaign 狀態為 `OPEN`，或 `allowRevision = true`
- 超過 `expireTime` 不可退出

### 3.8 kickParticipant: 團主踢除團員

- Path: `/api/v1/campaigns/{id}/participants/{participantId}/kick`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "reason": "超過集合時間仍未回應"
}
```

備註：
- Controller method name 是 `kickParticipant`
- Controller path 參數名稱叫 `participantId`，但實際傳入的是目標使用者 `userId`
- `requestBody` 可省略；若有帶 body，controller 目前只讀取 `reason`
- 若沒帶 `reason`，程式會使用預設字串
- service 會把 `reason` 寫入 participant 的 `hostNote`

限制：
- 只有團主可操作
- 只允許 campaign 狀態為 `OPEN` 或 `FULL`
- 若 campaign 已進入 `DELIVERED` 或 `CONFIRMED`，不可再踢人
- 目標使用者必須有 `JOINED` 狀態的參團資料

程式行為：
- participant 狀態改為 `KICKED`
- participant 的 `hostNote` 會保存團主填寫的原因
- 釋出該團員數量回 `availableQuantity`
- campaign 狀態改回 `OPEN`
- `allowRevision` 重設為 `false`
- 發送 `KICKED` 通知給被踢的使用者

### 3.9 團主宣告已面交

- Path: `/api/v1/campaigns/{id}/deliver`
- Method: `POST`
- Auth: 需要 JWT

限制：
- 只有團主可操作
- 只允許 `OPEN` 或 `FULL`
- 至少要有一位 `JOINED` 成員

### 3.10 團員確認收貨

- Path: `/api/v1/campaigns/{id}/confirm`
- Method: `POST`
- Auth: 需要 JWT

限制：
- campaign 必須是 `DELIVERED`
- 呼叫者必須有該團的 participant 記錄
- participant 狀態必須是 `JOINED`

### 3.11 團主取消開團

- Path: `/api/v1/campaigns/{id}/cancel`
- Method: `POST`
- Auth: 需要 JWT

限制：
- 只有團主可操作
- 不允許已經是 `CANCELLED`、`COMPLETED`、`DELIVERED`

### 3.12 markNoShow: 團主標記團員未到場

- Path: `/api/v1/campaigns/{campaignId}/participants/{userId}/no-show`
- Method: `PUT`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "note": "現場等 20 分鐘仍未出現"
}
```

備註：
- `{userId}` 是團員的使用者 ID，不是 participant table 的 primary key
- Controller method name 是 `markNoShow`
- `requestBody` 可省略；若有帶 body，controller 目前只讀取 `note`
- `note` 會傳給 service，並寫入 participant 的 `hostNote`

限制：
- 只有團主可操作
- 目標使用者必須是該團 participant
- 目標 participant 狀態必須是 `JOINED` 或 `COMPLETED`

### 3.13 raiseDispute: 團員提出爭議

- Path: `/api/v1/campaigns/{campaignId}/dispute`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "reason": "我有到場，團主標記錯誤"
}
```

備註：
- Controller method name 是 `raiseDispute`
- `reason` 目前 controller 直接用 `Map<String, String>` 接，不是獨立 DTO
- Controller 標成 `required = false`，但目前實作仍直接讀 `requestBody.getOrDefault(...)`
- 實務上請視為 request body 必填，否則可能發生錯誤
- service 會把 `reason` 寫入 participant 的 `disputeReason`

限制：
- 呼叫者必須是該團 participant
- participant 狀態必須是 `JOINED` 或 `NO_SHOW`

### 3.14 查詢我的信用紀錄

- Path: `/api/v1/campaigns/me/credit-logs`
- Method: `GET`
- Auth: 需要 JWT

Response: `Page<CreditLogResponse>`

### 3.15 查詢我開的團

- Path: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Auth: 需要 JWT

Response: `Page<CampaignSummaryResponse>`

查詢參數：
- `page`, `size`, `sort` 由 Spring `Pageable` 支援

返回的 `status` 為 campaign 狀態，可能值：
- `OPEN`
- `FULL`
- `DELIVERED`
- `COMPLETED`
- `CANCELLED`
- `HOST_NO_SHOW`
- `FAILED`

### 3.16 查詢我參加的團

- Path: `/api/v1/campaigns/my-joined`
- Method: `GET`
- Auth: 需要 JWT

Response: `Page<CampaignSummaryResponse>`

查詢參數：
- `page`, `size`, `sort` 由 Spring `Pageable` 支援

返回的 `status` 為 campaign 狀態，可能值：
- `OPEN`
- `FULL`
- `DELIVERED`
- `COMPLETED`
- `CANCELLED`
- `HOST_NO_SHOW`
- `FAILED`

補充：
- `/my-joined` 會先依 participant 狀態過濾資料，目前納入查詢的 participant 狀態為：
- `JOINED`
- `COMPLETED`
- `DISPUTED`
- `NO_SHOW`
- `CONFIRMED`

### 3.17 團主查看團務儀表板

- Path: `/api/v1/campaigns/{id}/host-dashboard`
- Method: `GET`
- Auth: 需要 JWT

Response: `HostDashboardResponse`

### 3.18 查詢我在某團的參與資訊

- Path: `/api/v1/campaigns/{id}/participants/me`
- Method: `GET`
- Auth: 需要 JWT

Response: `MyParticipationResponse`

## 4. User API

### 4.1 更新個人資料

- Path: `/api/v1/users/me`
- Method: `PUT`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "displayName": "A Hui",
  "hasCostcoMembership": true
}
```

### 4.2 封鎖使用者

- Path: `/api/v1/users/{id}/block`
- Method: `POST`
- Auth: 需要 JWT

### 4.3 解除封鎖使用者

- Path: `/api/v1/users/{id}/block`
- Method: `DELETE`
- Auth: 需要 JWT

### 4.4 查詢我的封鎖清單

- Path: `/api/v1/users/me/blocks`
- Method: `GET`
- Auth: 需要 JWT

Query Params:
- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<BlockedUserResponse>`

欄位：
- `userId`
- `displayName`
- `avatarUrl`
- `blockedAt`

### 4.5 取得使用者個人頁

- Path: `/api/v1/users/{id}/profile`
- Method: `GET`
- Auth: 需要 JWT

備註：
- Controller 內部支援 `currentUserId = null` 的情況，但目前 `SecurityConfig` 沒有將此路徑設為 `permitAll`，所以實際上仍需要 JWT
- 若雙方存在 block 關係，service 會拒絕查詢

Response: `UserProfileResponse`

主要欄位：
- `userId`
- `displayName`
- `avatarUrl`
- `creditScore`
- `totalHostedCount`
- `totalJoinedCount`
- `joinDate`
- `activeCampaigns`

這支 API 會取得：
- 使用者基本資料：`userId`、`displayName`、`avatarUrl`
- 使用者統計資料：`creditScore`、`totalHostedCount`、`totalJoinedCount`
- 使用者註冊時間：`joinDate`
- 該使用者目前仍在進行中的開團清單：`activeCampaigns`

`activeCampaigns` 內容為 `List<CampaignSummaryResponse>`，每筆會包含：
- `id`
- `itemName`
- `imageUrls`
- `pricePerUnit`
- `totalQuantity`
- `availableQuantity`
- `meetupLocation`
- `meetupTime`
- `expireTime`
- `status`
- `scenarioType`
- `storeName`
- `categoryName`
- `host`

補充：
- `activeCampaigns` 是以該使用者作為團主的進行中開團，repository 目前查的是 `status IN ('OPEN', 'FULL')`

## 5. Follow API

### 5.1 追蹤團主

- Path: `/api/v1/follows/{hostId}`
- Method: `POST`
- Auth: 需要 JWT

如何使用：
- `hostId` 帶要追蹤的團主 user id
- Header 帶 `Authorization: Bearer <JWT_TOKEN>`
- body 不需要

限制：
- 不能追蹤自己
- 已經追蹤過會報錯
- 若雙方任一方封鎖對方，不能追蹤

Response:

```json
{
  "success": true,
  "message": "follow success"
}
```

### 5.2 取消追蹤團主

- Path: `/api/v1/follows/{hostId}`
- Method: `DELETE`
- Auth: 需要 JWT

如何使用：
- `hostId` 帶要取消追蹤的團主 user id
- Header 帶 `Authorization: Bearer <JWT_TOKEN>`
- body 不需要

Response:

```json
{
  "success": true,
  "message": "unfollow success"
}
```

### 5.3 查詢我的追蹤清單

- Path: `/api/v1/follows/me`
- Method: `GET`
- Auth: 需要 JWT

如何使用：
- Header 帶 `Authorization: Bearer <JWT_TOKEN>`
- 可用 `page`、`size` 分頁

Query Params:
- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<FollowingUserResponse>`

欄位：
- `hostId`
- `displayName`
- `avatarUrl`
- `followedAt`

## 6. Review API

### 6.1 建立評價

- Path: `/api/v1/reviews`
- Method: `POST`
- Auth: 需要 JWT
- Content-Type: `application/json`

Request Body:

```json
{
  "campaignId": 1,
  "revieweeId": 2,
  "rating": 5,
  "comment": "很準時"
}
```

## 7. Notification API

### 7.1 查詢未讀通知

- Path: `/api/v1/notifications/unread`
- Method: `GET`
- Auth: 需要 JWT

Response: `List<NotificationResponse>`

### 7.2 標記通知已讀

- Path: `/api/v1/notifications/{id}/read`
- Method: `PUT`
- Auth: 需要 JWT

目前程式內實際使用到的通知類型：
- `CAMPAIGN_FULL`
- `CAMPAIGN_CANCELLED`
- `MEMBER_WITHDRAW`
- `KICKED`
- `NO_SHOW_WARNING`
- `DISPUTE_RAISED`

## 8. Chat API

### 8.1 取得聊天室歷史訊息

- Path: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- Auth: 需要 JWT

Response: `List<ChatMessageResponse>`

## 9. WebSocket / STOMP

### 9.1 連線資訊

- Endpoint: `/ws`
- 支援 SockJS
- Application Destination Prefix: `/app`
- Broker Prefix: `/topic`, `/queue`
- User Destination Prefix: `/user`

STOMP `CONNECT` header 需要帶：

```http
Authorization: Bearer <JWT_TOKEN>
```

### 9.2 發送聊天室訊息

- Client Send To: `/app/chat/{campaignId}/sendMessage`

Payload:

```json
{
  "content": "hello"
}
```

Server 會儲存訊息後，廣播到：
- `/topic/campaigns/{campaignId}`

### 9.3 接收個人通知

- Subscribe: `/user/queue/notifications`

Payload:

```json
{
  "content": "通知內容",
  "type": "CAMPAIGN_FULL",
  "referenceId": 123
}
```
