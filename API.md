# API 文件

本文件整理 `gb_backEnd` 目前對外提供的 REST API 與 WebSocket/STOMP 使用方式。內容依據目前 controller 與 service 實作整理，若程式碼與文件有落差，請以程式碼為準。

## 基本資訊

- Base Path: `/api/v1`
- 預設 HTTP Port: `8080`
- 靜態圖片路徑: `/images/{fileName}`
- 聊天室上傳圖片回傳路徑: `/uploads/campaigns/{campaignId}/{fileName}` 或 `/uploads/general/{fileName}`
- WebSocket STOMP Endpoint: `/ws`
- REST 預設 Content-Type: `application/json`
- 建立合購含圖片上傳時使用 Content-Type: `multipart/form-data`

## 認證規則

以下 API 不需要 JWT：

- `POST /api/v1/auth/line`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/dev-login`
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `GET /api/v1/purchase-requests`
- `GET /api/v1/purchase-requests/{id}`
- `/images/**`
- `/uploads/**`
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

Response Example:

```json
{
  "token": "jwt-token",
  "isNewUser": false,
  "refreshToken": "refresh-token",
  "user": {
    "id": 1,
    "displayName": "Howard",
    "profileImageUrl": "https://profile.line-scdn.net/...",
    "hasCostcoMembership": false
  }
}
```

說明：

- 使用 LINE OAuth code 換取 LINE access token。
- 取得 LINE profile 後，依 `lineUid` 建立或查詢使用者。
- 回傳系統 JWT 與 `refreshToken`，供前端後續呼叫受保護 API 與換發 access token。

### 1.2 Refresh Token

- Path: `/api/v1/auth/refresh`
- Method: `POST`
- Auth: 不需要
- Content-Type: `application/json`

Request Body:

```json
{
  "refreshToken": "refresh-token"
}
```

Response:

```json
{
  "token": "new-jwt-token",
  "refreshToken": "refresh-token"
}
```

說明：

- 使用資料庫中有效且未過期的 `refreshToken` 換發新的 access token。
- 目前 refresh 成功後會沿用原本的 `refreshToken`，不會重新旋轉。

### 1.3 Dev Login

- Path: `/api/v1/auth/dev-login`
- Method: `GET`
- Auth: 不需要

Query Params:

- `userId` `Long`, optional, default `1`

Response:

```json
{
  "message": "Developer login successful",
  "userId": "1",
  "token": "jwt-token",
  "refreshToken": "refresh-token"
}
```

說明：

- 開發測試用登入 API。
- 會依指定 `userId` 產生 JWT 與 `refreshToken`。

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

### 3.3 更新合購單圖片順序

- Path: `/api/v1/campaigns/{campaignId}/images/order`
- Method: `PUT`
- Auth: JWT required
- Content-Type: `application/json`

Request Body:

```json
{
  "imageUrls": [
    "image-b.jpg",
    "image-a.jpg",
    "image-c.jpg"
  ]
}
```

Response:

```json
{
  "success": true,
  "message": "圖片順序已成功更新"
}
```

規則：

- 只有該合購單團主可以更新圖片順序。
- `imageUrls` 必須放入排序後的完整合購圖片檔名陣列，例如 `["image-b.jpg", "image-a.jpg"]`。
- 此 API 是調整合購單圖片順序，不是聊天室圖片上傳路徑；不要傳 `/uploads/campaigns/{campaignId}/...`。
- 新傳入的圖片數量必須和合購單目前圖片數量一致，只能調整順序，不能透過此 API 新增或刪除圖片。
- 後端會依照 `imageUrls` 陣列順序重新儲存圖片排序。

### 3.4 團主修改開放數量

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

### 3.5 參加開團

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

### 3.6 團主開啟修改模式

- Path: `/api/v1/campaigns/{id}/unlock`
- Method: `POST`
- Auth: JWT required

規則：

- 只有團主可以操作。
- 合購狀態必須是 `FULL`。
- 開啟後團員可在允許條件下修改數量。

### 3.7 團員減少數量

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

### 3.8 退出開團

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

### 3.9 團主踢除團員

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

### 3.10 團主宣告已面交

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

### 3.11 團員確認收貨

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

### 3.12 團主取消開團

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

### 3.13 團主標記團員未到場

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

### 3.14 團員提出爭議

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

### 3.15 查詢我的信用紀錄（舊路徑，已停用）

- Path: `/api/v1/campaigns/me/credit-logs`
- Method: `GET`
- Status: 目前 controller 已註解停用

說明：

- 舊 DTO `CreditLogResponse` 已標記 `@Deprecated`。
- 前端請改用新版 `/api/v1/credit-scores/me/logs`。

### 3.16 查詢我開的團

- Path: `/api/v1/campaigns/my-hosted`
- Method: `GET`
- Auth: JWT required

Query Params:

- Spring `Pageable` 支援的 `page`、`size`、`sort`

Response: `Page<CampaignSummaryResponse>`

### 3.17 查詢我參加的團

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

### 3.18 團主查看團務儀表板

- Path: `/api/v1/campaigns/{id}/host-dashboard`
- Method: `GET`
- Auth: JWT required

Response: `HostDashboardResponse`

### 3.19 查詢我在某團的參與資訊

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

託購通知 type:

- `PURCHASE_QUOTED`: 跑腿者對託購單報價，通知委託人
- `PURCHASE_QUOTE_UPDATED`: 跑腿者修改報價，通知委託人
- `PURCHASE_QUOTE_CANCELLED`: 跑腿者取消報價，通知委託人
- `PURCHASE_ACCEPTED`: 固定酬金託購單被承接，通知委託人
- `PURCHASE_QUOTE_ACCEPTED`: 委託人接受報價，通知被選中的跑腿者
- `PURCHASE_QUOTE_REJECTED`: 委託人接受其他報價，通知未被選中的報價者
- `PURCHASE_DELIVERED`: 跑腿者標記已交付，通知委託人
- `PURCHASE_COMPLETED`: 委託人確認完成，通知跑腿者
- `PURCHASE_CANCELLED`: 委託人取消託購，通知待確認報價者

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

Credit score log source fields:

- `campaignId`: 合購來源 id，非合購來源時為 null
- `purchaseRequestId`: 託購來源 id，非託購來源時為 null
- `sourceType`: `CAMPAIGN` / `PURCHASE_REQUEST` / `SYSTEM`

## 9. Chat REST API

### 9.1 取得聊天室歷史訊息

- Path: `/api/v1/campaigns/{campaignId}/chat-messages`
- Method: `GET`
- Auth: JWT required

Response: `List<ChatMessageResponse>`

說明：

- 查詢指定合購聊天室歷史訊息。
- WebSocket 即時訊息仍由 STOMP channel 傳送。

### 9.2 上傳聊天室圖片

- Path: `/api/v1/files/upload`
- Method: `POST`
- Auth: JWT required
- Content-Type: `multipart/form-data`

Request Form Fields:

- `files` `List<MultipartFile>`, required，要上傳的圖片檔，可一次上傳多張。
- `campaignId` `Long`, optional，聊天室所屬的合購 id。

Response:

```json
{
  "success": true,
  "urls": [
    "/uploads/campaigns/123/550e8400-e29b-41d4-a716-446655440000.jpg",
    "/uploads/campaigns/123/7c9e6679-7425-40de-944b-e07fc1f90ae7.png"
  ]
}
```

說明：

- 此 API 提供聊天室先批次上傳圖片使用。
- 有帶 `campaignId` 時，檔案會存到 `uploads/campaigns/{campaignId}/`。
- 未帶 `campaignId` 時，檔案會存到 `uploads/general/`。
- 檔名會改成 UUID，副檔名沿用原始檔案副檔名。
- 空檔案會被略過，不會出現在回傳的 `urls` 內。
- `Content-Type` 為 `image/*` 的檔案會壓縮為最大寬高 `1280x1280`、輸出品質 `0.75`。
- 非圖片檔案目前會直接原檔儲存。
- 前端取得 `urls` 後，可將圖片網址放進聊天室訊息內容，再透過 STOMP 發送到 `/app/chat/{campaignId}/sendMessage`。

## 11. Purchase Request API

託購功能使用 `/api/v1/purchase-requests`，圖片沿用合購圖片目錄，回傳圖片網址格式為 `/images/{fileName}`。

`purchase_request.status`:

- `OPEN`: 開放中，可承接或報價
- `ASSIGNED`: 已成立，已有跑腿者
- `DELIVERED`: 跑腿者已標記交付
- `COMPLETED`: 委託人已確認完成
- `CANCELLED`: 委託人已取消

`purchase_request.rewardType`:

- `FIXED`: 固定酬金，跑腿者可直接承接
- `QUOTE`: 跑腿者報價，委託人接受報價後成立

`purchase_request_quote.status`:

- `PENDING`: 等待委託人確認
- `ACCEPTED`: 已接受
- `REJECTED`: 已拒絕
- `CANCELLED`: 報價者取消

### 11.1 查詢目前託購單

- Path: `/api/v1/purchase-requests`
- Method: `GET`
- Auth: 不需登入

Query Params:

- `keyword` `String`, optional，商品名稱關鍵字
- `rewardType` `String`, optional，`FIXED` 或 `QUOTE`
- `deliveryMethod` `String`, optional，`FACE_TO_FACE` / `STORE_TO_STORE` / `HOME_DELIVERY`
- `requestArea` `String`, optional，委託地區
- `status` `String`, optional，不傳時預設只查 `OPEN` 且未過期資料
- `page` `int`, optional, default `0`
- `size` `int`, optional, default `10`

Response: `Page<PurchaseRequestResponse>`

```json
{
  "content": [
    {
      "id": 12,
      "productName": "柯克蘭衛生紙",
      "imageUrls": ["http://localhost:8080/images/xxx.jpg"],
      "rewardType": "FIXED",
      "fixedRewardAmount": 100,
      "quoteCount": 0,
      "deliveryMethod": "FACE_TO_FACE",
      "requestArea": "台北中山區",
      "deadlineAt": "2026-05-30T23:59:59",
      "minCreditScore": 80,
      "status": "OPEN",
      "requester": {
        "id": 3,
        "displayName": "王曉明",
        "profileImageUrl": "...",
        "creditScore": 92
      },
      "canQuote": false,
      "canAcceptDirectly": true,
      "canEdit": false,
      "actBlockedReason": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### 11.2 查詢託購單詳情

- Path: `/api/v1/purchase-requests/{requestId}`
- Method: `GET`
- Auth: 不需登入

Response: `PurchaseRequestResponse`

### 11.3 建立託購單

- Path: `/api/v1/purchase-requests`
- Method: `POST`
- Auth: JWT required
- Content-Type: `multipart/form-data`

Request Form Fields:

- `productName` `String`, required
- `rewardType` `String`, required，`FIXED` 或 `QUOTE`
- `fixedRewardAmount` `BigDecimal`, `rewardType=FIXED` 時 required
- `deliveryMethod` `String`, required，`FACE_TO_FACE` / `STORE_TO_STORE` / `HOME_DELIVERY`
- `requestArea` `String`, optional
- `deadlineAt` `LocalDateTime`, optional，空值代表無期限
- `deliveryTimeType` `String`, optional，`SPECIFIED` 或 `DISCUSS`
- `deliveryTimeNote` `String`, optional
- `minCreditScore` `Integer`, optional，空值代表不限信用分
- `description` `String`, optional
- `images` `List<MultipartFile>`, optional，最多 3 張

Response: `PurchaseRequestResponse`

### 11.4 編輯託購單

- Path: `/api/v1/purchase-requests/{requestId}`
- Method: `PUT`
- Auth: JWT required
- Content-Type: `application/json`

限制:

- 只有委託人可編輯
- 只有 `OPEN` 狀態可編輯
- 已有人報價時不能切換 `rewardType`

### 11.5 託購單圖片

- `POST /api/v1/purchase-requests/{requestId}/images`: 新增圖片，`multipart/form-data`，field `images`
- `PUT /api/v1/purchase-requests/{requestId}/images/order`: 調整圖片排序，body `{"imageUrls":["image-b.jpg","image-a.jpg"]}`
- `DELETE /api/v1/purchase-requests/{requestId}/images/{fileName}`: 刪除圖片

### 11.6 固定酬金承接

- Path: `/api/v1/purchase-requests/{requestId}/accept`
- Method: `POST`
- Auth: JWT required

限制:

- 只適用 `rewardType=FIXED`
- 跑腿者不能是委託人
- 託購單必須是 `OPEN`
- 跑腿者信用分必須符合 `minCreditScore`

效果:

- `assignedRunner` 設為目前使用者
- `status` 改為 `ASSIGNED`

### 11.7 跑腿者報價

- `POST /api/v1/purchase-requests/{requestId}/quotes`: 建立報價，body `{"quoteAmount":120,"note":"週末可以面交"}`
- `PUT /api/v1/purchase-requests/{requestId}/quotes/{quoteId}`: 編輯自己的待確認報價
- `POST /api/v1/purchase-requests/{requestId}/quotes/{quoteId}/cancel`: 取消自己的待確認報價
- `GET /api/v1/purchase-requests/{requestId}/quotes`: 委託人查詢完整報價列表
- `GET /api/v1/purchase-requests/{requestId}/quotes/me`: 跑腿者查詢自己的報價

### 11.8 接受報價並成立

- Path: `/api/v1/purchase-requests/{requestId}/quotes/{quoteId}/accept`
- Method: `POST`
- Auth: JWT required

效果:

- 指定報價改為 `ACCEPTED`
- 其他待確認報價改為 `REJECTED`
- `assignedRunner` 設為報價者
- `acceptedQuoteId` 設為該報價
- `status` 改為 `ASSIGNED`

### 11.9 交付與完成

- `POST /api/v1/purchase-requests/{requestId}/deliver`: 跑腿者標記已交付，狀態改為 `DELIVERED`
- `POST /api/v1/purchase-requests/{requestId}/complete`: 委託人確認完成，狀態改為 `COMPLETED`

### 11.10 取消託購

### 11.10 託購評價

- Path: `/api/v1/purchase-requests/{requestId}/reviews`
- Method: `POST`
- Auth: JWT required

Request Body:

```json
{
  "rating": 5,
  "comment": "溝通順利"
}
```

限制:

- 託購單必須是 `COMPLETED`
- 只有委託人和承接跑腿者可以互評
- 委託人只能評價 `assignedRunner`
- 跑腿者只能評價 `requester`
- 同一張託購單、同一評價方向只能評一次

信用分規則:

- `5` 星: `+1`
- `1` 星: `-3`
- 同一 reviewer 對同一 reviewee 的正評，7 天內只計分一次
- 託購評價與合購評價共用同一個 `users.credit_score`
- 託購評價造成的信用分紀錄會寫入 `credit_score_logs.purchase_request_id`

查詢我對這張託購單是否已評價:

- Path: `/api/v1/purchase-requests/{requestId}/reviews/status`
- Method: `GET`
- Auth: JWT required

查詢我收到的託購評價:

- Path: `/api/v1/purchase-requests/me/received-reviews`
- Method: `GET`
- Auth: JWT required
- Response: `Page<PurchaseRequestReviewResponse>`

### 11.11 取消託購

- Path: `/api/v1/purchase-requests/{requestId}/cancel`
- Method: `POST`
- Auth: JWT required

```json
{
  "reason": "暫時不需要了"
}
```

限制:

- 只有委託人可取消
- 第一版只允許 `OPEN` 狀態取消

### 11.11 我的託購

- `GET /api/v1/purchase-requests/my-created`: 我發起的託購
- `GET /api/v1/purchase-requests/my-assigned`: 我承接的託購
- `GET /api/v1/purchase-requests/my-quotes`: 我報價過的託購

## 12. WebSocket / STOMP

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
