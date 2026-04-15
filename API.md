# API 文件

本文件整理 `gb_backEnd` 目前對外提供的 REST API 與 WebSocket/STOMP 使用方式。內容依據目前 controller 與 service 實作整理，若程式碼與文件有落差，請以程式碼為準。

## 基本資訊

- Base Path: `/api/v1`
- 預設 HTTP Port: `8080`
- 靜態圖片路徑: `/images/{fileName}`
- WebSocket STOMP Endpoint: `/ws`
- REST 預設 Content-Type: `application/json`
- 建立合購含圖片上傳時使用 Content-Type: `multipart/form-data`

## 認證規則

以下 API 不需要 JWT：

- `POST /api/v1/auth/line`
- `GET /api/v1/auth/dev-login`
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `/images/**`
- `/ws/**`

其餘 REST API 預設需要在 Header 帶入 JWT：

```http
Authorization: Bearer <JWT_TOKEN>
```

STOMP `CONNECT` 也需要帶入：

```http
Authorization: Bearer <JWT_TOKEN>
```

## 錯誤格式

全域錯誤由 `GlobalExceptionHandler` 統一處理，常見格式如下：

```json
{
  "timestamp": "2026-04-06T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "錯誤訊息",
  "path": "/api/v1/xxx"
}
```

## 常用狀態值

`campaign.status`：

- `OPEN`: 開放加入中
- `FULL`: 已額滿
- `DELIVERED`: 團主已宣告面交或交付，等待團員確認收貨
- `COMPLETED`: 所有團員已確認收貨，合購完成
- `CANCELLED`: 合購已取消
- `HOST_NO_SHOW`: 團主逾時未交付，被系統判定放鳥
- `FAILED`: 合購失敗或流局

`participant.status`：

- `JOINED`: 已加入
- `CANCELLED`: 已取消
- `KICKED`: 被團主踢除
- `CONFIRMED`: 已確認收貨
- `NO_SHOW`: 被標記未到場
- `DISPUTED`: 已提出爭議

`scenarioType`：

- `INSTANT`: 即時合購
- `SCHEDULED`: 預約合購

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

說明：

- 使用 LINE OAuth code 換取 LINE access token。
- 取得 LINE profile 後，依 `lineUid` 建立或查詢使用者。
- 回傳系統 JWT 給前端後續呼叫受保護 API 使用。

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

說明：

- 開發測試用登入 API。
- 會依指定 `userId` 產生 JWT。

## 2. Reference Data API

### 2.1 取得門市清單

- Path: `/api/v1/stores`
- Method: `GET`
- Auth: 不需要

Response: `List<StoreResponse>`

說明：

- 回傳目前系統內建或啟用的 Costco 門市資料。

### 2.2 取得分類清單

- Path: `/api/v1/categories`
- Method: `GET`
- Auth: 不需要

Response: `List<CategoryResponse>`

說明：

- 回傳商品分類資料，供建立合購或篩選合購列表使用。

## 3. Campaign API

### 3.1 查詢開團列表

- Path: `/api/v1/campaigns`
- Method: `GET`
- Auth: 不需要

Query Params:

- `storeId` `Integer`, optional，依門市篩選
- `categoryId` `Integer`, optional，依分類篩選
- `keyword` `String`, optional，依關鍵字搜尋
- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<CampaignSummaryResponse>`

主要欄位：

- `id`: 合購 id
- `itemName`: 商品名稱
- `imageUrls`: 圖片路徑清單
- `pricePerUnit`: 單位價格
- `totalQuantity`: 合購總數量
- `availableQuantity`: 剩餘可加入數量
- `meetupLocation`: 面交地點
- `meetupTime`: 面交時間
- `expireTime`: 合購截止時間
- `status`: 合購狀態
- `scenarioType`: 合購類型
- `storeName`: 門市名稱
- `categoryName`: 分類名稱
- `host.id`: 團主 id
- `host.displayName`: 團主顯示名稱
- `host.profileImageUrl`: 團主頭像
- `host.creditScore`: 團主目前信用分數

### 3.2 建立開團

- Path: `/api/v1/campaigns`
- Method: `POST`
- Auth: JWT required
- Content-Type: `multipart/form-data`

Request Form Fields:

- `storeId` `Integer`, required
- `categoryId` `Integer`, required
- `scenarioType` `String`, required，`INSTANT` 或 `SCHEDULED`
- `itemName` `String`, required
- `pricePerUnit` `Integer`, required
- `productTotalQuantity` `Integer`, required
- `openQuantity` `Integer`, required
- `meetupLocation` `String`, required
- `meetupTime` `LocalDateTime`, required
- `expireTime` `LocalDateTime`, required
- `images` `List<MultipartFile>`, optional，最多 `3` 張

規則：

- 開團者必須有 Costco 會員資格。
- `openQuantity` 不可大於 `productTotalQuantity`。
- 系統會計算 `hostReservedQuantity = productTotalQuantity - openQuantity`。
- 圖片會存到 `uploads/campaigns/`，對外讀取路徑為 `/images/{fileName}`。

### 3.3 團主修改開放數量

- Path: `/api/v1/campaigns/{id}/host-revise`
- Method: `PUT`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "newProductTotalQuantity": 36,
  "newOpenQuantity": 20
}
```

規則：

- 只有團主可以操作。
- 合購狀態必須是 `OPEN`。
- `newOpenQuantity` 不可大於 `newProductTotalQuantity`。
- `newOpenQuantity` 不可低於目前已加入數量。

### 3.4 參加開團

- Path: `/api/v1/campaigns/{id}/join`
- Method: `POST`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "quantity": 2
}
```

規則：

- `quantity >= 1`
- 不可加入自己開的團。
- 合購狀態必須是 `OPEN`。
- 不可超過剩餘可加入數量。
- 不可加入已過期合購。

### 3.5 團主開啟修改模式

- Path: `/api/v1/campaigns/{id}/unlock`
- Method: `POST`
- Auth: JWT required

規則：

- 只有團主可以操作。
- 合購狀態必須是 `FULL`。
- 開啟後團員可在允許條件下修改數量。

### 3.6 團員減少數量

- Path: `/api/v1/campaigns/{id}/revise`
- Method: `POST`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "quantity": 1
}
```

規則：

- 只能修改自己的 `JOINED` 參與紀錄。
- `quantity > 0`
- 合購狀態必須是 `OPEN`，或 `allowRevision = true`。
- 減少後數量不可低於 `1`。
- 若要完全退出，應使用退出 API。

### 3.7 退出開團

- Path: `/api/v1/campaigns/{id}/withdraw`
- Method: `POST`
- Auth: JWT required

規則：

- 只能退出自己的 `JOINED` 參與紀錄。
- 合購狀態必須是 `OPEN`，或 `allowRevision = true`。
- 不可退出已過期合購。

效果：

- participant 狀態改為 `CANCELLED`。
- 釋出原本加入的數量。
- campaign 狀態會回到 `OPEN`。
- 使用者 `participantCancelCount` 會加 `1`。
- 團主會收到 `MEMBER_WITHDRAW` 通知。

### 3.8 團主踢除團員

- Path: `/api/v1/campaigns/{id}/participants/{participantId}/kick`
- Method: `POST`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "reason": "超過約定時間未回覆"
}
```

規則：

- 只有團主可以操作。
- 合購狀態必須是 `OPEN` 或 `FULL`。
- 不可在 `DELIVERED` 或 `CONFIRMED` 後踢除。
- 目標使用者必須是 `JOINED` 團員。

效果：

- participant 狀態改為 `KICKED`。
- participant `hostNote` 會記錄踢除原因。
- 合購釋出該團員數量並改回 `OPEN`。
- `allowRevision` 設為 `false`。
- 發送 `KICKED` 通知。

### 3.9 團主宣告已面交

- Path: `/api/v1/campaigns/{id}/deliver`
- Method: `POST`
- Auth: JWT required

規則：

- 只有團主可以操作。
- 合購狀態必須是 `OPEN` 或 `FULL`。
- 至少需要一位 `JOINED` 團員。

效果：

- campaign 狀態改為 `DELIVERED`。
- 系統會透過 WebSocket 廣播 `CAMPAIGN_STATUS_CHANGED` 到 `/topic/campaigns/{campaignId}`。
- 廣播 payload 會包含 `type`、`campaignId`、`status`、`message`。

WebSocket 廣播範例：

```json
{
  "type": "CAMPAIGN_STATUS_CHANGED",
  "campaignId": 123,
  "status": "DELIVERED",
  "message": "主揪已發起面交"
}
```

### 3.10 團員確認收貨

- Path: `/api/v1/campaigns/{id}/confirm`
- Method: `POST`
- Auth: JWT required

規則：

- campaign 狀態必須是 `DELIVERED`。
- 目前使用者必須有該合購的 participant。
- participant 狀態必須是 `JOINED`。

效果：

- participant 狀態改為 `CONFIRMED`。
- 如果已無剩餘 `JOINED` 團員，campaign 狀態改為 `COMPLETED`。
- campaign 完成時寫入 `completedAt`。
- campaign 完成時發送 `CAMPAIGN_COMPLETED` 通知給團主與所有 `CONFIRMED` 團員。

### 3.11 團主取消開團

- Path: `/api/v1/campaigns/{id}/cancel`
- Method: `POST`
- Auth: JWT required

規則：

- 只有團主可以操作。
- 不允許取消狀態為 `CANCELLED`、`COMPLETED`、`DELIVERED` 的合購。

效果：

- campaign 狀態改為 `CANCELLED`。
- 如果已有 active `JOINED` 團員，這些 participant 會改為 `CANCELLED`。
- 如果已有 active `JOINED` 團員，會呼叫 `CreditScoreService.recordScoreChange(...)` 寫入信用分紀錄。
- 目前程式實際傳入 `scoreChange = 10`，原因為 `取消已有團員的合購單：「{itemName}」`。
- 如果沒有 active `JOINED` 團員，不會寫入信用分紀錄。

注意：

- 目前 `scoreChange = 10` 代表程式實作是加分紀錄；如果產品規格是取消要扣分，程式應改為 `-10`。

### 3.12 團主標記團員未到場

- Path: `/api/v1/campaigns/{campaignId}/participants/{userId}/no-show`
- Method: `PUT`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "note": "超過 20 分鐘未到場"
}
```

規則：

- 只有團主可以操作。
- `{userId}` 是使用者 id，不是 participant id。
- 目標 participant 狀態必須是 `JOINED` 或 `COMPLETED`。
- `note` 會記錄到 participant `hostNote`。

### 3.13 團員提出爭議

- Path: `/api/v1/campaigns/{campaignId}/dispute`
- Method: `POST`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "reason": "我有到場，但團主誤標未到場"
}
```

規則：

- 目前使用者必須有該 campaign 的 participant。
- participant 狀態必須是 `JOINED` 或 `NO_SHOW`。
- `reason` optional，未提供時 service 會補預設原因。

效果：

- participant `disputeReason` 記錄爭議原因。
- 發送 `DISPUTE_RAISED` 通知給團主。

### 3.14 查詢我的信用紀錄（舊路徑，已停用）

- Path: `/api/v1/campaigns/me/credit-logs`
- Method: `GET`
- Status: 目前 controller 已註解停用

說明：

- 舊 DTO `CreditLogResponse` 已標記 `@Deprecated`。
- 前端請改用新版 `/api/v1/credit-scores/me/logs`。

### 3.15 查詢我開的團

- Path: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Auth: JWT required

Query Params:

- Spring `Pageable` 支援的 `page`、`size`、`sort`

Response: `Page<CampaignSummaryResponse>`

### 3.16 查詢我參加的團

- Path: `/api/v1/campaigns/my-joined`
- Method: `GET`
- Auth: JWT required

Query Params:

- Spring `Pageable` 支援的 `page`、`size`、`sort`

Response: `Page<CampaignSummaryResponse>`

目前會納入的 participant 狀態：

- `JOINED`
- `COMPLETED`
- `DISPUTED`
- `NO_SHOW`
- `CONFIRMED`

### 3.17 團主查看團務儀表板

- Path: `/api/v1/campaigns/{id}/host-dashboard`
- Method: `GET`
- Auth: JWT required

Response: `HostDashboardResponse`

### 3.18 查詢我在某團的參與資訊

- Path: `/api/v1/campaigns/{id}/participants/me`
- Method: `GET`
- Auth: JWT required

Response: `MyParticipationResponse`

## 4. User API

### 4.1 更新個人資料

- Path: `/api/v1/users/me`
- Method: `PUT`
- Auth: JWT required
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
- Auth: JWT required

### 4.3 解除封鎖使用者

- Path: `/api/v1/users/{id}/block`
- Method: `DELETE`
- Auth: JWT required

### 4.4 查詢我的封鎖清單

- Path: `/api/v1/users/me/blocks`
- Method: `GET`
- Auth: JWT required

Query Params:

- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<BlockedUserResponse>`

欄位：

- `userId`: 被封鎖使用者 id
- `displayName`: 顯示名稱
- `avatarUrl`: 頭像
- `blockedAt`: 封鎖時間

### 4.5 取得使用者個人頁

- Path: `/api/v1/users/{id}/profile`
- Method: `GET`
- Auth: JWT required

Response: `UserProfileResponse`

欄位：

- `userId`: 使用者 id
- `displayName`: 顯示名稱
- `avatarUrl`: 頭像
- `creditScore`: 目前信用分
- `totalHostedCount`: 開團總數
- `totalJoinedCount`: 參團總數
- `joinDate`: 加入時間
- `activeCampaigns`: 使用者目前進行中的合購清單

## 5. Follow API

### 5.1 追蹤團主

- Path: `/api/v1/follows/{hostId}`
- Method: `POST`
- Auth: JWT required

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
- Auth: JWT required

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
- Auth: JWT required

Query Params:

- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<FollowingUserResponse>`

欄位：

- `hostId`: 被追蹤團主 id
- `displayName`: 團主顯示名稱
- `avatarUrl`: 團主頭像
- `followedAt`: 追蹤時間

## 6. Review API

### 6.1 建立評價

- Path: `/api/v1/reviews`
- Method: `POST`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "campaignId": 1,
  "revieweeId": 2,
  "rating": 5,
  "comment": "交易順利"
}
```

規則：

- 不可評價自己。
- `rating` 必須介於 `1` 到 `5`。
- 同一個 campaign、reviewer、reviewee 不可重複評價。
- `rating = 5` 目前加 `+1`。
- `rating = 1` 目前扣 `-3`。
- `rating = 2 ~ 4` 不調整信用分。
- 7 天內同 reviewer 對同 reviewee 的有效加分會被防刷限制。

### 6.2 查詢評價狀態

- Path: `/api/v1/reviews/check`
- Method: `GET`
- Auth: JWT required

Query Params:

- `campaignId` `Long`, required
- `revieweeId` `Long`, required

Response: `ReviewStatusResponse`

欄位：

- `isReviewed`: 是否已評價
- `rating`: 若已評價，回傳評分；未評價時為 `null`
- `comment`: 若已評價，回傳評語；未評價時為 `null`

### 6.3 查詢我收到的評價

- Path: `/api/v1/reviews/me/received`
- Method: `GET`
- Auth: JWT required

Query Params:

- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`
- `sort` Spring Pageable 支援的排序參數，optional

Response: `Page<ReviewResponse>`

用途：

- 查詢「目前登入使用者」作為被評價者時，收到的所有評價紀錄。
- 適合用在個人頁、信用頁、評價列表頁。
- 後端會從 JWT 取得目前登入者 id，不需要前端傳 `revieweeId`。
- 結果依 `createdAt desc` 排序，也就是最新收到的評價在最前面。

Response 欄位：

- `id`: 評價紀錄 id
- `campaignId`: 關聯合購 id
- `campaignName`: 關聯合購商品名稱
- `reviewerId`: 評價者 id
- `reviewerName`: 評價者顯示名稱
- `rating`: 評分，範圍 `1 ~ 5`
- `comment`: 評語內容
- `isScoreCounted`: 這筆評價是否已納入信用分計算
- `createdAt`: 評價建立時間

Response 範例：

```json
{
  "content": [
    {
      "id": 10,
      "campaignId": 123,
      "campaignName": "Costco 牛肉分購",
      "reviewerId": 5,
      "reviewerName": "A Hui",
      "rating": 5,
      "comment": "交易順利，準時面交",
      "isScoreCounted": true,
      "createdAt": "2026-04-14T20:30:00"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true,
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "empty": false
}
```

## 7. Notification API

### 7.1 查詢未讀通知

- Path: `/api/v1/notifications/unread`
- Method: `GET`
- Auth: JWT required

Response: `List<NotificationResponse>`

說明：

- 回傳目前使用者尚未讀取的通知。
- 依建立時間新到舊排序。

### 7.2 標記通知已讀

- Path: `/api/v1/notifications/{id}/read`
- Method: `PUT`
- Auth: JWT required

Response:

```json
{
  "success": true,
  "message": "已標記為已讀"
}
```

說明：

- 只能標記自己的通知。
- 標記後該通知會從未讀清單移除。

### 7.3 查詢已讀通知

- Path: `/api/v1/notifications/read`
- Method: `GET`
- Auth: JWT required

Query Params:

- Spring `Pageable` 支援的 `page`、`size`、`sort`
- controller 預設 `size = 20`

Response: `Page<NotificationResponse>`

說明：

- 回傳目前使用者已讀通知。
- 依建立時間新到舊排序。
- 適合前端通知中心查詢歷史通知紀錄。

目前使用中的通知類型：

- `CAMPAIGN_FULL`: 合購滿單
- `CAMPAIGN_CANCELLED`: 合購取消
- `CAMPAIGN_COMPLETED`: 合購完成，可開始互評
- `MEMBER_WITHDRAW`: 團員退出
- `KICKED`: 團員被踢除
- `NO_SHOW_WARNING`: 團員被標記未到場警告
- `DISPUTE_RAISED`: 團員提出爭議

## 8. Credit Score API

### 8.1 查詢我的信用分異動紀錄

- Path: `/api/v1/credit-scores/me/logs`
- Method: `GET`
- Auth: JWT required

Query Params:

- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<CreditScoreLogResponse>`

欄位：

- `id`: 信用分紀錄 id
- `scoreChange`: 本次分數異動值，例如 `+1`、`-3`、`-10`
- `reason`: 異動原因
- `campaignId`: 關聯合購 id，前端可用於跳轉
- `createdAt`: 紀錄建立時間

說明：

- 依 `createdAt desc` 排序。
- 讀取資料表 `credit_score_logs`。
- `CreditLogResponse` 已標記 deprecated，前端請改用 `CreditScoreLogResponse`。

## 9. Chat REST API

### 9.1 取得聊天室歷史訊息

- Path: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- Auth: JWT required

Response: `List<ChatMessageResponse>`

說明：

- 查詢指定合購聊天室歷史訊息。
- WebSocket 即時訊息仍由 STOMP channel 傳送。

## 10. WebSocket / STOMP

### 10.1 連線資訊

- Endpoint: `/ws`
- SockJS: supported
- Application Destination Prefix: `/app`
- Broker Prefix: `/topic`, `/queue`
- User Destination Prefix: `/user`

STOMP `CONNECT` header:

```http
Authorization: Bearer <JWT_TOKEN>
```

### 10.2 發送聊天室訊息

- Client Send To: `/app/chat/{campaignId}/sendMessage`
- Server Broadcast To: `/topic/campaigns/{campaignId}`

Payload:

```json
{
  "content": "hello"
}
```

聊天室訂閱規則：

- 訂閱 `/topic/campaigns/{campaignId}` 時，必須是該 campaign 的團主，或是允許狀態中的參與者。
- 允許參與者狀態包含 `JOINED`、`CONFIRMED`、`DISPUTED`、`NO_SHOW`。
- `CANCELLED` campaign 不允許訂閱。
- `COMPLETED` campaign 在 `completedAt` 後保留 3 天聊天室權限，超過後不允許訂閱。

### 10.3 接收個人通知

- Subscribe: `/user/queue/notifications`

Payload:

```json
{
  "content": "notification content",
  "type": "CAMPAIGN_FULL",
  "referenceId": 123
}
```

### 10.4 接收合購狀態變更廣播

- Subscribe: `/topic/campaigns/{campaignId}`

Payload:

```json
{
  "type": "CAMPAIGN_STATUS_CHANGED",
  "campaignId": 123,
  "status": "DELIVERED",
  "message": "主揪已發起面交"
}
```

說明：

- 目前團主呼叫 `POST /api/v1/campaigns/{id}/deliver` 時，會廣播此事件。
- 前端可用此事件即時更新聊天室內的合購狀態提示。
