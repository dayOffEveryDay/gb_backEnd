# API 文件

本文件整理 `gb_backEnd` 目前提供的 REST API 與 WebSocket/STOMP 介面。

## 聊天室已讀進度

合購沿用現有 `chat_read_states` 表，依 `(campaign_id, user_id)` 保存團主與團員進度，首次標記時建立資料。未建立進度或 `last_read_message_id` 為 NULL 時，所有其他人發送的訊息均視為未讀；自己的訊息不計入未讀數。

- 歷史訊息與 STOMP 廣播的 `ChatMessageResponse` 都包含 `messageId`。
- 我的發起／參與合購列表在各筆摘要中回傳 `unreadCount`，不可閱讀的聊天室回傳 0。
- `POST /api/v1/campaigns/{campaignId}/chat-messages/read` 接收 `{"lastReadMessageId": 123}`，回傳 `200` 與 `{"unreadCount": 2}`。
- 合購標記已讀使用閱讀權限，包含唯讀成員；主動退出、被踢除或非成員不能更新。
- 合購與託購的已讀 ID 必須為正整數，且屬於指定聊天室；缺少或無效 ID 回傳 `400`。重複或較舊的 ID 不會倒退已讀進度。
- 前端在聊天室訊息渲染且分頁可見後提交實際讀到的最大 ID；開啟導覽、發送訊息本身不會自動標記資料庫最新訊息。
- 此進度與小鈴鐺通知的已讀狀態分開保存，現有通知流程維持原狀。

託購已讀 API 現在也必須傳入相同格式的 body，需同步更新前端部署。瀏覽器舊有的合購已讀時間不會匯入後端；首次使用新流程時，依後端進度計算未讀數。

## 基本資訊

- Base URL: `http://localhost:8080`
- REST Base Path: `/api/v1`
- REST Content-Type: `application/json`
- 上傳檔案 Content-Type: `multipart/form-data`
- 靜態圖片:
  - `/images/{fileName}`
  - `/uploads/**`
- WebSocket/STOMP Endpoint: `/ws`

需要登入的 REST API 請帶入 JWT:

```http
Authorization: Bearer <JWT_TOKEN>
```

STOMP `CONNECT` 也使用相同 header。

## 不需登入的 API

- `POST /api/v1/auth/line`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/dev-login`（僅 local profile 啟用）
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `GET /api/v1/public/campaigns/{campaignId}`
- `GET /api/v1/purchase-requests`
- `GET /api/v1/purchase-requests/{id}`
- `GET /api/v1/inquiries`
- `GET /api/v1/inquiries/similar`
- `GET /api/v1/inquiries/{id}`
- `GET /share/campaigns/{campaignId}`
- `GET /share/purchase-requests/{purchaseRequestId}`
- `/images/**`
- `/uploads/**`
- `/ws/**`

## 共用錯誤格式

```json
{
  "timestamp": "2026-06-04T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "錯誤訊息",
  "path": "/api/v1/example"
}
```

## 狀態值

### Campaign status

- `OPEN`: 開放加入
- `FULL`: 名額已滿
- `DELIVERED`: 主揪已完成交付，等待成員確認
- `COMPLETED`: 團購完成
- `CANCELLED`: 團購取消
- `HOST_NO_SHOW`: 主揪未出現
- `FAILED`: 團購失敗

### Participant status

- `JOINED`: 已加入
- `CANCELLED`: 已退出
- `KICKED`: 被主揪移除
- `CONFIRMED`: 已確認收貨
- `NO_SHOW`: 被標記未出現
- `DISPUTED`: 已提出爭議

### Purchase request status

- `OPEN`: 開放接單或報價
- `QUOTING`: 已有有效報價
- `ORDERED`: 已成立託購訂單
- `EXPIRED`: 委託期限已到期
- `CANCELLED`: 委託人已取消

### Purchase request enum values

- `rewardType`: `FIXED` / `QUOTE`
- `deliveryMethod`: `FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE` / `IBOX`
- `profileType`: `FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE` / `IBOX`
- `status`: `OPEN` / `QUOTING` / `ORDERED` / `AWAITING_REOPEN` / `EXPIRED` / `CANCELLED`

### Purchase quote status

- `ACTIVE`: 有效報價
- `WITHDRAWN`: 報價者已撤回
- `SELECTED`: 委託人已選定
- `NOT_SELECTED`: 訂單成立時未被選中
- `EXPIRED`: 委託取消或過期後失效
- `UNAVAILABLE_CANCELLED`: 得標者回報缺貨後失效

### Purchase order status

- `WAITING_CONFIRMATION`: 訂單已成立，等待必要方確認
- `CONFIRMED`: 必要方已確認
- `IN_PROGRESS`: 接單人處理中
- `SHIPPED`: 宅配或店到店已寄出
- `DELIVERED_PENDING_CONFIRM`: 面交已交付，等待委託人確認
- `COMPLETED`: 訂單已完成
- `CANCELLED`: 一般取消
- `UNAVAILABLE_CANCELLED`: 缺貨取消，不扣信用分
- `ABNORMAL_PENDING_RESPONSE`: 異常提出，等待對方回應
- `PENALIZED_CANCELLED`: 有責取消並已扣分
- `DISPUTE_CLOSED_NO_PENALTY`: 異常有回應，不扣分結案

## 1. Auth API

### LINE Login

`POST /api/v1/auth/line`

Request:

```json
{
  "code": "line-oauth-code",
  "redirectUri": "http://localhost:5173/login/callback"
}
```

Response:

```json
{
  "token": "jwt-token",
  "isNewUser": false,
  "refreshToken": "refresh-token",
  "user": {
    "id": 1,
    "displayName": "A Hui",
    "profileImageUrl": "https://profile.line-scdn.net/..."
  }
}
```

### Refresh Token

`POST /api/v1/auth/refresh`

Request:

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

### Dev Login

`GET /api/v1/auth/dev-login?userId=1`

僅供開發測試使用，回傳 JWT 與 refresh token。`local` profile 預設啟用，
可透過 `DEV_LOGIN_ENABLED=false` 關閉；`prod` profile 一律關閉並回傳 `404 Not Found`。

## 2. Reference Data API

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/stores` | No | 取得啟用中的 Costco 分店 |
| `GET` | `/api/v1/categories` | No | 取得商品分類 |

`StoreResponse`:

```json
{
  "id": 1,
  "name": "內湖店",
  "address": "台北市...",
  "openTime": "10:00",
  "closeTime": "21:30"
}
```

`CategoryResponse`:

```json
{
  "id": 1,
  "name": "食品",
  "icon": "food"
}
```

## 3. Campaign API

### 取得團購列表

`GET /api/v1/campaigns`

Query params:

- `storeId`: optional
- `categoryId`: optional
- `keyword`: optional
- `page`: optional, default `0`
- `size`: optional, default `10`

Response: `Page<CampaignSummaryResponse>`

`CampaignSummaryResponse` 主要欄位:

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
- `myParticipantStatus`

### 取得公開團購詳情

`GET /api/v1/public/campaigns/{campaignId}`

Auth: not required

此端點供前端分享頁取得團購公開資料。加入團購仍須使用需要登入的
`POST /api/v1/campaigns/{id}/join`。

Response `200 OK`:

```json
{
  "id": 8,
  "scenarioType": "SCHEDULED",
  "status": "OPEN",
  "itemName": "金色三麥包種茶啤酒",
  "categoryName": "飲料",
  "availableQuantity": 5,
  "totalQuantity": 12,
  "pricePerUnit": 85,
  "storeName": "中和店",
  "meetupLocation": "聊天討論",
  "meetupTime": "2026-09-06T12:27:00+08:00",
  "expireTime": "2026-09-06T12:27:00+08:00",
  "imageUrls": [
    "https://example.com/images/campaign-8-1.jpg"
  ],
  "host": {
    "id": 4,
    "displayName": "開發者 4",
    "profileImageUrl": "https://example.com/avatar.jpg",
    "creditScore": 100
  },
  "canJoin": true
}
```

`canJoin` 僅在下列條件全部成立時為 `true`：

- `status` 為 `OPEN`
- `availableQuantity` 大於 `0`
- `expireTime` 尚未到期，或未設定截止時間

HTTP status:

| Status | 說明 |
| --- | --- |
| `200 OK` | 團購可公開顯示。已滿、已截止、失敗或已完成仍回傳資料，並以 `status` 與 `canJoin: false` 呈現。 |
| `404 Not Found` | 團購不存在或已刪除。 |
| `410 Gone` | 團購狀態為 `CANCELLED` 或 `CANCEL_PENDING`，已下架且不再公開。 |

公開回應不包含參加者名單、聊天訊息、聯絡資料、團主私人會員資料或登入者的參團狀態。

### 團購 LINE 分享頁

`GET /share/campaigns/{campaignId}`

Auth: not required

回傳 `text/html`，供 LINE 爬蟲讀取初始 HTML 中的 Open Graph 標籤：

```html
<meta property="og:title" content="金色三麥包種茶啤酒">
<meta property="og:description" content="中和店・NT$ 85・剩餘 5 件">
<meta property="og:image" content="https://example.com/images/campaign-8-1.jpg">
<meta property="og:url" content="https://gb.ttgtool.com/share/campaigns/8">
```

分享頁不存在時回 `404 Not Found`；已取消或進入取消流程時回 `410 Gone`。

### 建立團購

`POST /api/v1/campaigns`

Auth: required

Content-Type: `multipart/form-data`

Form fields:

- `storeId`: required
- `categoryId`: required
- `scenarioType`: `INSTANT` 或 `SCHEDULED`
- `itemName`: required
- `pricePerUnit`: required
- `productTotalQuantity`: required
- `openQuantity`: required
- `meetupLocation`: required
- `meetupTime`: required, `LocalDateTime`
- `expireTime`: required, `LocalDateTime`
- `images`: optional, 最多 3 張

Response:

```json
{
  "success": true,
  "message": "團購建立成功"
}
```

### 團購操作

| Method | Path | Auth | Body | 說明 |
| --- | --- | --- | --- | --- |
| `PUT` | `/api/v1/campaigns/{campaignId}/images/order` | Yes | `{"imageUrls":["..."]}` | 調整團購圖片順序 |
| `PUT` | `/api/v1/campaigns/{id}/host-revise` | Yes | `{"newProductTotalQuantity":36,"newOpenQuantity":20}` | 主揪調整總量與開放數量 |
| `POST` | `/api/v1/campaigns/{id}/join` | Yes | `{"quantity":2}` | 加入團購 |
| `POST` | `/api/v1/campaigns/{id}/revise` | Yes | `{"quantity":1}` | 成員修改數量 |
| `POST` | `/api/v1/campaigns/{id}/withdraw` | Yes | none | 成員退出 |
| `POST` | `/api/v1/campaigns/{id}/unlock` | Yes | none | 主揪重新開放數量修改 |
| `POST` | `/api/v1/campaigns/{id}/participants/{participantId}/kick` | Yes | `{"reason":"..."}` | 主揪移除成員 |
| `POST` | `/api/v1/campaigns/{id}/deliver` | Yes | none | 主揪標記已交付 |
| `POST` | `/api/v1/campaigns/{id}/confirm` | Yes | none | 成員確認收貨 |
| `POST` | `/api/v1/campaigns/{id}/cancel` | Yes | none | 主揪取消團購 |
| `PUT` | `/api/v1/campaigns/{campaignId}/participants/{userId}/no-show` | Yes | `{"note":"..."}` | 主揪標記成員未出現 |
| `POST` | `/api/v1/campaigns/{campaignId}/dispute` | Yes | `{"reason":"..."}` | 成員提出爭議 |

注意:

- `kick` 路徑中的 `{participantId}` 目前 controller 實際傳入 service 時作為目標使用者 id 使用。
- `no-show` 路徑中的 `{userId}` 是目標使用者 id，不是 participant id。

### 我的團購資料

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| `GET` | `/api/v1/campaigns/my-hosted` | Yes | `Page<CampaignSummaryResponse>` |
| `GET` | `/api/v1/campaigns/my-joined` | Yes | `Page<CampaignSummaryResponse>` |
| `GET` | `/api/v1/campaigns/{id}/host-dashboard` | Yes | `HostDashboardResponse` |
| `GET` | `/api/v1/campaigns/{id}/participants/me` | Yes | `MyParticipationResponse` |

## 4. User API

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `PUT` | `/api/v1/users/me` | Yes | 更新個人資料 |
| `GET` | `/api/v1/users/{id}/profile` | Yes | 取得使用者公開資料 |
| `POST` | `/api/v1/users/{id}/block` | Yes | 封鎖使用者 |
| `DELETE` | `/api/v1/users/{id}/block` | Yes | 解除封鎖 |
| `GET` | `/api/v1/users/me/blocks?page=0&size=10` | Yes | 取得我的封鎖清單 |

更新個人資料 request:

```json
{
  "displayName": "A Hui"
}
```

`UserProfileResponse` 主要欄位:

- `userId`
- `displayName`
- `avatarUrl`
- `creditScore`
- `noShowCount`
- `totalHostedCount`
- `hostCancelCount`
- `totalJoinedCount`
- `participantCancelCount`
- `joinDate`
- `activeCampaigns`

## 5. Follow API

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/follows/{hostId}` | Yes | 追蹤主揪 |
| `DELETE` | `/api/v1/follows/{hostId}` | Yes | 取消追蹤 |
| `GET` | `/api/v1/follows/me?page=0&size=10` | Yes | 取得我的追蹤清單 |

## 6. Review API

### 團購評價

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/reviews` | Yes | 建立團購評價 |
| `GET` | `/api/v1/reviews/check?campaignId=1&revieweeId=2` | Yes | 檢查是否已評價 |
| `GET` | `/api/v1/reviews/me/received` | Yes | 取得我收到的團購評價 |

建立評價 request:

```json
{
  "campaignId": 1,
  "revieweeId": 2,
  "rating": 5,
  "comment": "交易順利"
}
```

信用分規則:

- `rating = 5`: 被評價者 `+1`
- `rating = 1`: 被評價者 `-3`
- `rating = 2 ~ 4`: 不調整信用分
- 同一 reviewer 對同一 reviewee 的評價信用分計算有 7 天限制

## 7. Notification API

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/notifications/unread` | Yes | 取得未讀通知 |
| `PUT` | `/api/v1/notifications/{id}/read` | Yes | 標記通知為已讀 |
| `GET` | `/api/v1/notifications/read` | Yes | 取得已讀通知 |

`NotificationResponse`:

```json
{
  "id": 1,
  "type": "CAMPAIGN_FULL",
  "referenceId": 123,
  "content": "通知內容",
  "createdAt": "2026-06-04T12:00:00"
}
```

## 8. Credit Score API

`GET /api/v1/credit-scores/me/logs?page=0&size=10`

Auth: required

Response: `Page<CreditScoreLogResponse>`

欄位:

- `id`
- `scoreChange`
- `reason`
- `campaignId`
- `sourceType`: `CAMPAIGN` / `SYSTEM`
- `createdAt`

## 9. Chat API

### REST

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/campaigns/{campaignId}/chat-messages` | Yes | 取得團購聊天室歷史訊息 |
| `POST` | `/api/v1/campaigns/{campaignId}/chat-messages/read` | Yes | 提交實際已讀訊息 ID，回傳剩餘未讀數 |
| `POST` | `/api/v1/files/upload` | Yes | 上傳聊天室圖片 |

上傳圖片 request:

- Content-Type: `multipart/form-data`
- `files`: required, `List<MultipartFile>`
- `campaignId`: optional

Response:

```json
{
  "success": true,
  "urls": [
    "http://localhost:8080/uploads/campaigns/123/example.jpg"
  ]
}
```

`urls` 會依 `app.base-url` 回傳完整後端網址。託購建立或更新收到這類 URL 時，後端會像團購圖片一樣只把 uploads 目錄下的相對 key 寫入 DB，例如 `general/example.jpg`。

團購聊天室權限:

- 團主建立團購後，以及使用者成功參團後，聊天室為 `READ_WRITE`。
- 主動退出或被踢除後為 `NONE`，不可讀取歷史訊息、訂閱或發言。
- 團主取消整團或系統判定團主未出現後，原有效成員為 `READ_ONLY`。
- 團購完成後保留 3 天 `READ_WRITE`，之後改為 `READ_ONLY`。
- `GET /api/v1/campaigns/{id}/participants/me` 會回傳 `chatAccess`：`READ_WRITE`、`READ_ONLY` 或 `NONE`。
- 前端應訂閱 `/user/queue/chat-access`；收到 `CHAT_ACCESS_REVOKED` 時立即取消聊天室訂閱並離開畫面。
- 後端也會在 outbound channel 逐一驗證接收者，已退出或被踢除的既有訂閱不會再收到後續訊息。

### WebSocket/STOMP

- Endpoint: `/ws`
- Application destination prefix: `/app`
- Broker prefix: `/topic`, `/queue`
- User destination prefix: `/user`

送出聊天室訊息:

- Client send: `/app/chat/{campaignId}/sendMessage`
- Server broadcast: `/topic/campaigns/{campaignId}`

Payload:

```json
{
  "content": "hello"
}
```

Response payload:

```json
{
  "senderId": 1,
  "avatarUrl": "https://...",
  "senderName": "A Hui",
  "content": "hello",
  "timestamp": "2026-06-04T12:00:00"
}
```

私人通知訂閱:

- Subscribe: `/user/queue/notifications`

團購狀態訂閱:

- Subscribe: `/topic/campaigns/{campaignId}`

## 10. User Delivery Profile API

常用交付資料是使用者模板。建立委託單時會複製成快照，因此後續修改或刪除模板不會影響既有委託單。

### 取得我的常用交付資料

`GET /api/v1/user-delivery-profiles`

Auth: required

Query params:

- `profileType`: optional，`FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE` / `IBOX`

Response: `200 OK`

```json
[
  {
    "id": 1,
    "profileType": "FACE_TO_FACE",
    "label": "台北車站面交",
    "city": "台北市",
    "district": "中正區",
    "locationName": "台北車站東一門",
    "locationCode": null,
    "provider": null,
    "address": null,
    "recipientName": null,
    "recipientPhone": null,
    "isDefault": false,
    "createdAt": "2026-07-03T12:00:00",
    "updatedAt": "2026-07-03T12:00:00"
  }
]
```

### 新增常用交付資料

`POST /api/v1/user-delivery-profiles`

Auth: required

Request:

```json
{
  "profileType": "FACE_TO_FACE",
  "label": "台北車站面交",
  "city": "台北市",
  "district": "中正區",
  "locationName": "台北車站東一門",
  "locationCode": null,
  "provider": null,
  "address": null,
  "recipientName": null,
  "recipientPhone": null,
  "isDefault": false
}
```

Response: `201 Created`，body 為完整 `UserDeliveryProfileResponse`。

驗證規則:

- `profileType` 必填，且必須符合本文件「Purchase request enum values」。
- `label`、`city`、`district`、`locationName`、`locationCode`、`provider`、`address`、`recipientName`、`recipientPhone` 皆為選填欄位。
- 前端應依 `profileType` 傳入需要的交付欄位；例如宅配使用 `address` 與收件資料，店到店／i郵箱使用 `provider`、`locationCode` 與 `locationName`。

### 修改常用交付資料

`PUT /api/v1/user-delivery-profiles/{id}`

Auth: required

Request 格式與新增相同。只有資料擁有者可以修改。

Response: `200 OK`，body 為更新後的 `UserDeliveryProfileResponse`。

### 刪除常用交付資料

`DELETE /api/v1/user-delivery-profiles/{id}`

Auth: required

只有資料擁有者可以刪除。已建立委託單保存的是快照，不受此操作影響。

Response: `204 No Content`

## 11. Purchase Request API

### 建立委託單

`POST /api/v1/purchase-requests`

Auth: required

Request（面交可選多筆常用交付資料）:

```json
{
  "itemName": "莓姬梅酒",
  "itemDescription": "希望購買草莓口味",
  "imageUrls": [
    "http://localhost:8080/uploads/general/3c835b6a-552a-473c-aee3-51180c94976d.png"
  ],
  "rewardType": "FIXED",
  "fixedRewardAmount": 100,
  "deliveryMethod": "FACE_TO_FACE",
  "requestExpireTime": "2026-07-10T23:59:59",
  "deliveryDeadlineHours": 72,
  "minCreditScore": 80,
  "selectedProfileIds": [1, 2]
}
```

宅配、店到店或 i郵箱同樣選取已建立的常用交付資料，例如:

```json
{
  "deliveryMethod": "HOME_DELIVERY",
  "selectedProfileIds": [3]
}
```

上例只列交付相關欄位，實際 request 仍須包含商品、酬金與期限等必填欄位。

Response: `201 Created`

```json
{
  "id": 1001
}
```

主要驗證規則:

- `itemName`、`rewardType`、`deliveryMethod`、`requestExpireTime`、`deliveryDeadlineHours`、`selectedProfileIds` 必填。
- `FIXED` 模式的 `fixedRewardAmount` 必須大於 `0`；`QUOTE` 模式會忽略固定酬金。
- `requestExpireTime` 必須是未來時間。
- `deliveryDeadlineHours` 必須大於 `0`，`minCreditScore` 不可小於 `0`。
- 至少選擇一筆 `selectedProfileIds`；可選多筆，重複 ID 只建立一份快照。
- 選取的常用交付資料必須存在且屬於目前登入者。
- 每筆 profile 的 `profileType` 必須與 `deliveryMethod` 相同。
- 建立後會將選取資料複製到 `purchase_request_delivery_details`；之後修改或刪除 profile 不影響既有託購單。

### 取得委託單列表

`GET /api/v1/purchase-requests`

Auth: not required

Query params:

- `status`: optional；未指定時只查未過期的 `OPEN`、`QUOTING`
- `city`: optional
- `district`: optional
- `deliveryMethod`: optional
- `rewardType`: optional
- `keyword`: optional，同時搜尋商品名稱與說明
- `page`: optional，default `0`
- `size`: optional，default `20`，範圍 `1` 到 `100`

Response: `200 OK`，`Page<PurchaseRequestSummaryResponse>`

```json
{
  "content": [
    {
      "id": 1001,
      "requesterId": 1,
      "requesterDisplayName": "A Hui",
      "requesterProfileImageUrl": "https://profile.example/requester.jpg",
      "itemName": "莓姬梅酒",
      "imageUrls": ["http://localhost:8080/uploads/general/3c835b6a-552a-473c-aee3-51180c94976d.png"],
      "rewardType": "FIXED",
      "fixedRewardAmount": 100,
      "deliveryMethod": "FACE_TO_FACE",
      "deliveryPublicSummary": "台北市中正區 台北車站東一門、台北市信義區 市政府站",
      "deliveryDetails": [
        {
          "id": 31,
          "city": "台北市",
          "district": "中正區",
          "locationName": "台北車站東一門"
        },
        {
          "id": 32,
          "city": "台北市",
          "district": "信義區",
          "locationName": "市政府站"
        }
      ],
      "requestExpireTime": "2026-07-10T23:59:59",
      "deliveryDeadlineHours": 72,
      "minCreditScore": 80,
      "status": "OPEN",
      "createdAt": "2026-07-03T12:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

列表的 `deliveryDetails` 只回傳公開欄位。宅配、店到店與 i郵箱不會公開地址、收件資料、來源 profile ID；非面交方式也不公開據點名稱與代碼。

### 取得委託單詳情

`GET /api/v1/purchase-requests/{id}`

Auth: not required；委託人以及已成立訂單的交易雙方可取得完整交付快照。

Response: `200 OK`

```json
{
  "id": 1001,
  "requesterId": 1,
  "requesterDisplayName": "A Hui",
  "requesterProfileImageUrl": "https://...",
  "requesterCreditScore": 100,
  "itemName": "莓姬梅酒",
  "itemDescription": "希望購買草莓口味",
  "imageUrls": ["http://localhost:8080/uploads/general/3c835b6a-552a-473c-aee3-51180c94976d.png"],
  "rewardType": "FIXED",
  "fixedRewardAmount": 100,
  "deliveryMethod": "FACE_TO_FACE",
  "deliveryPublicSummary": "台北市中正區 台北車站東一門",
  "deliveryDetails": [
    {
      "id": 31,
      "city": "台北市",
      "district": "中正區",
      "locationName": "台北車站東一門",
      "locationCode": null,
      "provider": null
    }
  ],
  "requestExpireTime": "2026-07-10T23:59:59",
  "deliveryDeadlineHours": 72,
  "minCreditScore": 80,
  "status": "OPEN",
  "reopenCount": 0,
  "cancelledAt": null,
  "expiredAt": null,
  "createdAt": "2026-07-03T12:00:00",
  "updatedAt": "2026-07-03T12:00:00"
}
```

`deliveryDetails` 私密欄位（`sourceProfileId`、`address`、`recipientName`、`recipientPhone`）可見條件:

- 目前登入者是託購單委託人；或
- 託購單已產生 `purchase_orders`，且目前登入者是該訂單的委託人或接單人。
- 未登入或非上述使用者時，私密欄位會因 `NON_NULL` 序列化設定而不出現在 JSON 中。

### 修改委託單

`PUT /api/v1/purchase-requests/{id}`

Auth: required，只有委託人可操作。

Request 使用部分更新語意，只需傳入要修改的欄位:

```json
{
  "itemName": "更新後商品名稱",
  "requestExpireTime": "2026-07-15T23:59:59",
  "deliveryDeadlineHours": 96,
  "selectedProfileIds": [1, 2]
}
```

規則:

- `ORDERED`、`EXPIRED`、`CANCELLED` 不可修改。
- `OPEN` 且沒有有效報價時，可修改商品、酬金、交付方式、交付資料與期限。
- `QUOTING` 或已有 `ACTIVE` 報價時，只能傳入較晚的 `requestExpireTime`。
- 傳入 `selectedProfileIds` 時會以選取的常用交付資料重建全部交付快照。
- 修改 `deliveryMethod` 時必須同時傳入 `selectedProfileIds`，且所有 profile 類型須與新的交付方式一致。

Response: `200 OK`，body 為更新後的 `PurchaseRequestDetailResponse`。委託人可取得完整交付快照。

### 取消委託單

`POST /api/v1/purchase-requests/{id}/cancel`

Auth: required，只有委託人可操作。

規則:

- 只有 `OPEN`、`QUOTING` 可以取消。
- 委託單改為 `CANCELLED` 並記錄取消時間。
- 所有 `ACTIVE` 報價改為 `EXPIRED`。
- 此操作不扣信用分。

Response: `204 No Content`

### 延長委託期限

`PATCH /api/v1/purchase-requests/{id}/extend`

Auth: required，只有委託人可操作。

Request:

```json
{
  "requestExpireTime": "2026-07-15T23:59:59"
}
```

規則:

- 委託單必須是 `OPEN` 或 `QUOTING`。
- 新期限必須是未來時間且晚於原期限。

Response: `200 OK`，body 為更新後的 `PurchaseRequestDetailResponse`。

### 重新開放缺貨委託

`POST /api/v1/purchase-requests/{id}/reopen`

Auth: required，只有原委託人可操作。

規則:

- 目前只支援 `FIXED` 固定酬金委託。
- 委託狀態必須是 `AWAITING_REOPEN`。
- 委託改回 `OPEN`，`reopenCount` 加 `1`，並將最近重開原因記為 `ITEM_UNAVAILABLE`。
- 新期限為原期限與「操作當下加 24 小時」兩者中較晚者，不需要 request body。
- 原本的 `purchase_orders` 維持 `UNAVAILABLE_CANCELLED`；下一次承接會建立新的訂單。

Response: `200 OK`，body 為更新後的 `PurchaseRequestDetailResponse`。

### 提出報價

`POST /api/v1/purchase-requests/{id}/quotes`

Auth: required

Request:

```json
{
  "quoteAmount": 150,
  "quoteNote": "明天下午可以協助購買"
}
```

規則:

- 只有 `QUOTE` 委託可報價，委託狀態必須是 `OPEN` 或 `QUOTING` 且尚未過期。
- 報價者不可是委託人，信用分須達門檻，雙方不可存在封鎖關係。
- 同一使用者對同一委託只能建立一筆報價；撤回後不可建立第二筆。
- 第一筆有效報價成立時，委託單狀態改為 `QUOTING`。

Response: `201 Created`，body 為 `PurchaseQuoteResponse`。

### 取得委託單報價列表

`GET /api/v1/purchase-requests/{id}/quotes`

Auth: required，只有委託人可查看完整列表。

Response: `200 OK`，body 為 `PurchaseQuoteResponse[]`。

### 取得我的報價

`GET /api/v1/purchase-quotes/me?page=0&size=20`

Auth: required

Response: `200 OK`，`Page<PurchaseQuoteResponse>`。

`PurchaseQuoteResponse` 範例:

```json
{
  "id": 2001,
  "purchaseRequestId": 1001,
  "itemName": "莓姬梅酒",
  "itemImageUrls": [
    "https://.../purchase-request-image-1.jpg"
  ],
  "requesterDisplayName": "委託人",
  "quoterId": 2,
  "quoterDisplayName": "Runner",
  "quoterProfileImageUrl": "https://...",
  "quoterCreditScore": 100,
  "quoteAmount": 150,
  "quoteNote": "明天下午可以協助購買",
  "status": "ACTIVE",
  "selectedAt": null,
  "withdrawnAt": null,
  "createdAt": "2026-07-03T12:00:00",
  "updatedAt": "2026-07-03T12:00:00"
}
```

### 修改自己的報價

`PUT /api/v1/purchase-quotes/{quoteId}`

Auth: required

Request 格式與提出報價相同。

規則:

- 只有報價者本人可修改。
- 報價與委託單都必須仍處於可報價狀態。

Response: `200 OK`，body 為更新後的 `PurchaseQuoteResponse`。

### 撤回自己的報價

`POST /api/v1/purchase-quotes/{quoteId}/withdraw`

Auth: required

只有報價者本人可撤回 `ACTIVE` 報價。最後一筆有效報價撤回後，`QUOTING` 委託會恢復為 `OPEN`。

Response: `200 OK`，body 為更新後的 `PurchaseQuoteResponse`。

### 選定報價並成立訂單

`POST /api/v1/purchase-quotes/{quoteId}/select`

Auth: required，只有委託人可操作。

此操作會以資料庫交易鎖定委託單，重新檢查期限、信用分、封鎖關係與報價狀態，避免同時成立多筆訂單。

成功後:

- 委託單改為 `ORDERED`。
- 選中報價改為 `SELECTED`，其他有效報價改為 `NOT_SELECTED`。
- 建立狀態為 `WAITING_CONFIRMATION` 的 `purchase_orders`。
- 寫入 `ORDER_CREATED_FROM_QUOTE` 訂單事件並通知報價者。

Response: `201 Created`，body 為 `PurchaseOrderResponse`。

### 固定酬金直接承接

`POST /api/v1/purchase-requests/{id}/accept`

Auth: required

規則:

- 委託必須是尚未過期的 `FIXED`、`OPEN` 委託。
- 接單人不可是委託人，信用分須達門檻，雙方不可存在封鎖關係。
- 使用交易鎖避免多人同時承接。

成功後建立 `WAITING_CONFIRMATION` 訂單、將委託改為 `ORDERED`，並寫入 `ORDER_CREATED_BY_ACCEPT` 事件。固定酬金由接單人主動承接，接單當下即視為接單人已確認，後續只需委託人確認即可進入 `CONFIRMED`。

Response: `201 Created`

```json
{
  "id": 3001,
  "purchaseRequestId": 1001,
  "itemName": "莓姬梅酒",
  "requesterId": 1,
  "requesterDisplayName": "A Hui",
  "requesterProfileImageUrl": "https://profile.example/requester.jpg",
  "runnerId": 2,
  "runnerDisplayName": "Runner",
  "runnerProfileImageUrl": "https://profile.example/runner.jpg",
  "selectedQuoteId": null,
  "status": "WAITING_CONFIRMATION",
  "createdAt": "2026-07-03T12:00:00",
  "updatedAt": "2026-07-03T12:00:00"
}
```

訂單成立時會同步建立託購聊天室，response 會回傳 `chatRoomId`。

### 取得我的發起列表

`GET /api/v1/purchase-requests/my-created?status=OPEN&page=0&size=20`

Auth: required

- `status`: optional
- `page`: default `0`
- `size`: default `20`，範圍 `1` 到 `100`

Response: `200 OK`，`Page<PurchaseRequestSummaryResponse>`。

## 12. Purchase Order API

### 取得我的託購訂單列表

`GET /api/v1/purchase-orders`

Auth: required

Query params:

- `role`: required，`REQUESTER` / `RUNNER`
- `status`: optional，參考 Purchase order status
- `page`: default `0`
- `size`: default `20`，範圍 `1` 到 `100`

Response: `200 OK`，`Page<PurchaseOrderSummaryResponse>`。

列表包含商品摘要、交易雙方、交付方式、酬金、訂單狀態與依成立時間計算的 `deliveryDeadlineAt`。

### 取得訂單詳情

`GET /api/v1/purchase-orders/{orderId}`

Auth: required，只有委託人與接單人可讀取。

Response: `200 OK`，`PurchaseOrderDetailResponse`，主要欄位:

- 委託商品、圖片與交易雙方，包含 `requesterProfileImageUrl`、`runnerProfileImageUrl`
- 固定酬金或得標報價金額
- `deliveryDetails` 交付資料快照（訂單雙方可取得完整欄位）
- 必要方確認、開始、寄出、交貨、完成與取消時間
- 物流公司、單號與查詢網址
- 歸責使用者與信用扣分
- `deliveryDeadlineAt`
- 異常提出者、被提出者、異常類型與提出／回應時間
- `availableActions`: `CONFIRM` / `START` / `SHIP` / `DELIVER` / `COMPLETE` / `CANCEL` / `REPORT_ABNORMAL` / `RESPOND_ABNORMAL`

### 委託人確認交易

`POST /api/v1/purchase-orders/{orderId}/confirm/requester`

Auth: required，只有委託人可操作。

- 訂單必須是 `WAITING_CONFIRMATION`。
- 重複確認採冪等處理，直接回傳目前訂單。
- `FIXED` 固定酬金接單時，接單人已在承接當下確認；委託人確認後訂單改為 `CONFIRMED`。
- `QUOTE` 報價模式若接單人尚未確認，需雙方都確認後訂單才改為 `CONFIRMED`。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

## 18. Inquiry API

商品詢問分為兩種 `type`：

- `STOCK`：庫存詢問，建立後固定 14 天到期。
- `EXPERIENCE`：商品心得，不設定到期時間。

以下文件以 `/api/v1/inquiries` 為正式路徑。為相容詢問頁初版規格，後端也接受相同功能的 `/api/inquiries` 路徑。

### API 一覽

| Method | Path | Auth | 說明 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/inquiries` | No | 問題列表、搜尋與篩選 |
| `GET` | `/api/v1/inquiries/similar` | No | 搜尋相似問題，最多 5 筆 |
| `GET` | `/api/v1/inquiries/{id}` | No | 取得問題詳情與回答 |
| `POST` | `/api/v1/inquiries` | Yes | 發表問題及上傳附圖 |
| `POST` | `/api/v1/inquiries/{id}/answers` | Yes | 新增回答 |
| `PUT` | `/api/v1/inquiries/{inquiryId}/answers/{answerId}` | Yes | 修改自己的回答 |
| `POST` | `/api/v1/inquiries/{id}/follow` | Yes | 關注問題 |
| `DELETE` | `/api/v1/inquiries/{id}/follow` | Yes | 取消關注問題 |

### 問題列表、搜尋與篩選

`GET /api/v1/inquiries`

Auth: optional。未登入時 `followed` 固定為 `false`；登入後會回傳目前使用者是否已關注。

Query params:

- `keyword`: optional，搜尋問題標題或商品名稱。
- `type`: optional，`STOCK` 或 `EXPERIENCE`。
- `storeId`: optional，指定門市 ID。
- `page`: optional，預設 `0`。
- `size`: optional，預設 `20`，範圍 `1` 到 `100`。

Response: `200 OK`，`Page<InquirySummaryResponse>`。每個 `content` 項目如下：

```json
{
  "id": 100,
  "type": "STOCK",
  "title": "中和店還有葡勝納嗎？",
  "productName": "葡勝納",
  "storeId": 3,
  "storeName": "中和店",
  "imageUrls": [
    "http://localhost:8080/uploads/inquiries/example.jpg"
  ],
  "status": "OPEN",
  "answerCount": 3,
  "followCount": 8,
  "followed": false,
  "createdAt": "2026-09-03T10:00:00",
  "expireTime": "2026-09-17T10:00:00"
}
```

`status` 為 `OPEN` 或 `EXPIRED`。`EXPERIENCE` 的 `expireTime` 為 `null`。

### 搜尋相似問題

`GET /api/v1/inquiries/similar?keyword=葡勝納哪裡有&type=STOCK&storeId=3`

Auth: not required

- `keyword`: required，搜尋問題標題或商品名稱。
- `type`: optional，`STOCK` 或 `EXPERIENCE`。
- `storeId`: optional，指定門市 ID。

Response: `200 OK`，最多 5 筆：

```json
[
  {
    "id": 100,
    "title": "葡勝納哪個賣場還有庫存？",
    "answerCount": 5,
    "followCount": 12,
    "createdAt": "2026-09-03T08:00:00"
  }
]
```

### 取得問題詳情

`GET /api/v1/inquiries/{id}`

Auth: optional。登入時會計算 `followed`。

Response: `200 OK`：

```json
{
  "id": 100,
  "type": "STOCK",
  "title": "葡勝納哪個賣場還有庫存？",
  "productName": "葡勝納",
  "content": "最近一直找不到，有人看到嗎？",
  "storeId": null,
  "storeName": null,
  "imageUrls": [
    "http://localhost:8080/uploads/inquiries/example.jpg"
  ],
  "status": "OPEN",
  "answerCount": 1,
  "followCount": 12,
  "followed": true,
  "createdAt": "2026-09-03T10:00:00",
  "expireTime": "2026-09-17T10:00:00",
  "author": {
    "id": 10,
    "displayName": "User A",
    "profileImageUrl": "https://example.com/avatar.jpg"
  },
  "answers": [
    {
      "id": 201,
      "storeId": 3,
      "storeName": "中和店",
      "stockStatus": "IN_STOCK",
      "content": "今天晚上七點左右還有看到。",
      "createdAt": "2026-09-03T11:00:00",
      "updatedAt": "2026-09-03T11:00:00",
      "author": {
        "id": 11,
        "displayName": "User B",
        "profileImageUrl": "https://example.com/avatar-b.jpg"
      }
    }
  ]
}
```

問題不存在時回傳 `404 Not Found`。

### 發表問題與上傳圖片

`POST /api/v1/inquiries`

Auth: required

Content-Type: `multipart/form-data`

Form parts:

- `request`: required，Content-Type 為 `application/json` 的問題資料。
- `images`: optional，圖片檔案，可重複傳送，最多 3 張。

`request` 範例：

```json
{
  "type": "STOCK",
  "title": "中和店有明太子醬嗎？",
  "productName": "明太子醬",
  "storeId": 3,
  "content": "最近有人看到嗎？"
}
```

規則:

- `type`、`title` 必填；`productName`、`storeId`、`content` 選填。
- `type` 僅能為 `STOCK` 或 `EXPERIENCE`。
- `storeId` 有值時必須是已存在的門市。
- 圖片必須為 image content type，且最多 3 張；後端負責產生檔名、壓縮與儲存。
- `STOCK` 建立時自動將 `expireTime` 設為建立後 14 天；`EXPERIENCE` 的 `expireTime` 為 `null`。

Response: `201 Created`，body 為完整 `InquiryDetailResponse`。

### 新增回答

`POST /api/v1/inquiries/{id}/answers`

Auth: required

STOCK 問題 request：

```json
{
  "storeId": 3,
  "stockStatus": "IN_STOCK",
  "content": "今天晚上七點左右還有看到。"
}
```

EXPERIENCE 問題 request：

```json
{
  "content": "我覺得滿好吃的，但口味比較重。"
}
```

規則:

- `STOCK` 回答的 `content` 選填，可只回報門市或庫存狀態。
- `EXPERIENCE` 回答的 `content` 必填，且不可為純空白。
- `stockStatus` 可為 `IN_STOCK`、`LOW_STOCK`、`OUT_OF_STOCK` 或 `UNKNOWN`；只適用 `STOCK` 問題。
- `EXPERIENCE` 回答不可填寫 `storeId` 或 `stockStatus`。
- `EXPIRED` 問題不可新增回答。
- 同一位使用者可以對同一問題新增多筆回答；每筆回答均可透過各自的 `answerId` 修改。
- 成功後會通知發問者及所有關注者，回答者本人不會收到這筆回答通知；通知類型為 `INQUIRY_NEW_ANSWER`。

Response: `201 Created`：

```json
{
  "id": 201,
  "storeId": 3,
  "storeName": "中和店",
  "stockStatus": "IN_STOCK",
  "content": "今天晚上七點左右還有看到。",
  "createdAt": "2026-09-03T11:00:00",
  "updatedAt": "2026-09-03T11:00:00",
  "author": {
    "id": 11,
    "displayName": "User B",
    "profileImageUrl": "https://example.com/avatar-b.jpg"
  }
}
```

所有回答回應都包含 `author`，其中 `author.profileImageUrl` 為回答者頭像網址，前端可直接用於回答列表與詳情顯示。

### 修改自己的回答

`PUT /api/v1/inquiries/{inquiryId}/answers/{answerId}`

Auth: required

Request 格式與新增回答相同：

```json
{
  "storeId": 3,
  "stockStatus": "LOW_STOCK",
  "content": "下午再去看時只剩少量。"
}
```

規則:

- 只有回答建立者本人可以修改。
- `answerId` 必須屬於 URL 中的 `inquiryId`。
- 不能修改 `EXPIRED` 問題的回答。
- `STOCK` 與 `EXPERIENCE` 的內容、門市與庫存狀態驗證規則與新增回答相同。
- 回傳的 `updatedAt` 是本次修改完成時間；前端可在 `updatedAt` 與 `createdAt` 不同時顯示已編輯標記。
- 修改回答不會發送新回答通知。

Response: `200 OK`，body 為更新後的 `InquiryAnswerResponse`。

### 關注與取消關注

`POST /api/v1/inquiries/{id}/follow`

Auth: required

Response: `200 OK`

```json
{
  "followed": true
}
```

此操作為冪等操作；已關注時再次呼叫仍回傳成功。`EXPIRED` 問題不可新增關注。

`DELETE /api/v1/inquiries/{id}/follow`

Auth: required

Response: `200 OK`

```json
{
  "followed": false
}
```

取消關注同樣為冪等操作，原本未關注時也會成功回傳 `followed: false`。

### 到期排程與通知

`InquiryScheduler.expireStockInquiries` 每分鐘執行一次：

- 將 `expireTime` 已到的 `STOCK` 問題從 `OPEN` 更新為 `EXPIRED`。
- 問題仍可搜尋與讀取歷史回答，但不可新增回答或關注。
- 若問題到期前沒有任何回答，會通知發問者及全部關注者；通知類型為 `INQUIRY_EXPIRED_NO_ANSWER`。

## 19. Purchase Order API（補充）

### 提出訂單異常

`POST /api/v1/purchase-orders/{orderId}/abnormal/report`

Auth: required，委託人或接單人皆可提出。

Request:

```json
{
  "abnormalType": "RUNNER_NOT_DELIVERED",
  "description": "超過約定時間仍未收到商品",
  "imageUrls": ["evidence.jpg"]
}
```

允許的異常類型:

- `REQUESTER_NO_SHOW`
- `RUNNER_NO_SHOW`
- `RUNNER_NOT_DELIVERED`
- `REQUESTER_UNREACHABLE`
- `ITEM_NOT_RECEIVED`
- `OTHER`

規則:

- 訂單必須是 `IN_PROGRESS`、`SHIPPED` 或 `DELIVERED_PENDING_CONFIRM`。
- 系統自動將交易對方設為 `abnormalReportedUser`。
- 訂單改為 `ABNORMAL_PENDING_RESPONSE`。
- 說明與證據圖片寫入 `ABNORMAL_REPORTED` 事件。
- 被提出異常者必須在 12 小時內回應。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 回應訂單異常

`POST /api/v1/purchase-orders/{orderId}/abnormal/respond`

Auth: required，只有 `abnormalReportedUser` 可以回應。

Request:

```json
{
  "responseText": "我有依照約定完成交付",
  "imageUrls": ["delivery-proof.jpg"]
}
```

規則:

- 訂單必須是 `ABNORMAL_PENDING_RESPONSE`。
- 回應時間必須早於提出時間加 12 小時。
- 成功回應後改為 `DISPUTE_CLOSED_NO_PENALTY`。
- 第一階段無人工仲裁，有回應即不扣任何一方信用分。
- 回應內容與圖片寫入 `ABNORMAL_RESPONDED_NO_PENALTY` 事件。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 查詢訂單事件

`GET /api/v1/purchase-orders/{orderId}/events`

Auth: required，只有訂單交易雙方可查詢。

Response: `200 OK`，`PurchaseOrderEventResponse[]`，依建立時間由舊到新排序。

```json
[
  {
    "id": 1,
    "orderId": 3001,
    "actorId": 1,
    "actorDisplayName": "A Hui",
    "eventType": "ABNORMAL_REPORTED",
    "fromStatus": "IN_PROGRESS",
    "toStatus": "ABNORMAL_PENDING_RESPONSE",
    "description": "超過約定時間仍未收到商品",
    "metadata": {
      "abnormalType": "RUNNER_NOT_DELIVERED",
      "imageUrls": ["evidence.jpg"]
    },
    "createdAt": "2026-07-03T12:00:00"
  }
]
```

### 異常逾時自動扣分

排程名稱：`checkPurchaseOrderAbnormalTimeout`

- 每分鐘檢查一次。
- 條件為 `ABNORMAL_PENDING_RESPONSE`、提出超過 12 小時且尚未回應。
- 取得訂單排他鎖後會重新驗證，避免覆蓋期限邊界送出的回應。
- 對 `abnormalReportedUser` 扣 `10` 分。
- 訂單改為 `PENALIZED_CANCELLED`，並設定歸責使用者與取消時間。
- 寫入 `ABNORMAL_TIMEOUT_PENALIZED` 事件並通知交易雙方。

## 13. Purchase Chat API

- `GET /api/v1/purchase-chat-rooms`: 我的聊天室清單，包含對方、最後訊息、未讀數與唯讀狀態。
- `GET /api/v1/purchase-chat-rooms/unread-count`: 所有託購聊天室未讀總數。
- `POST /api/v1/purchase-chat-rooms/{roomId}/read`: 提交實際已讀訊息 ID，response `204`。必須傳入 `{"lastReadMessageId": 123}`，不再自動標記到資料庫最新訊息。
- `POST /api/v1/purchase-chat-rooms/{roomId}/messages`: 發送文字或圖片 URL。
- `GET /api/v1/purchase-orders/{orderId}/messages`: 訂單聊天室歷史訊息。
- `POST /api/v1/purchase-orders/{orderId}/messages`: 發送文字訊息。
- `POST /api/v1/purchase-orders/{orderId}/messages/images`: 發送圖片 URL 訊息。

發送 request:

```json
{
  "messageType": "TEXT",
  "content": "明天下午會去購買"
}
```

聊天室只允許訂單委託人與接單人存取。訂單完成或取消後保留 3 天可寫入，之後轉為唯讀。

WebSocket/STOMP:

- Send: `/app/chat/rooms/{chatRoomId}/sendMessage`
- Subscribe: `/topic/chat/rooms/{chatRoomId}`
- Payload 使用既有 `ChatMessageRequest` 的 `content` 欄位。
- 訂閱與發送都會檢查聊天室成員權限。

## 14. Purchase Request Image API

- `POST /api/v1/purchase-requests/{id}/images`: `multipart/form-data`，欄位 `images`，最多 5 張。
- `DELETE /api/v1/purchase-requests/{id}/images/{fileName}`: 刪除單張圖片。
- `PUT /api/v1/purchase-requests/{id}/images/order`: 更新圖片排序。

排序 request:

```json
{
  "imageUrls": [
    "http://localhost:8080/uploads/purchase-requests/1001/a.jpg",
    "http://localhost:8080/uploads/purchase-requests/1001/b.jpg"
  ]
}
```

只有委託人可管理圖片，且委託必須是沒有有效報價的 `OPEN` 狀態。圖片只允許 image content type。
後端 DB 只保存 uploads 目錄下的相對 key，例如 `purchase-requests/1001/a.jpg`；API response 會依 `app.base-url` 回傳完整可讀 URL。

## 15. Purchase Order Review API

- `POST /api/v1/purchase-orders/{orderId}/reviews`: 評價交易對方。
- `GET /api/v1/purchase-orders/{orderId}/reviews/status`: 查詢是否已評價。
- `GET /api/v1/purchase-order-reviews/me/received?page=0&size=20`: 收到的託購評價。

評價 request:

```json
{
  "rating": 5,
  "comment": "交易順利"
}
```

只有 `COMPLETED` 訂單的交易雙方可評價，每人對同一訂單只能評價對方一次。5 星 `+1`、1 星 `-3`；正評沿用 7 天防刷分規則。

## 16. Purchase Block API

`POST /api/v1/blocks`

```json
{
  "blockedUserId": 2002
}
```

Response: `204 No Content`。封鎖後雙方不能成立新的報價或接單交易。

## 17. Purchase Scheduler

- `expirePurchaseRequests`: 每分鐘將過期 `OPEN` / `QUOTING` 委託與有效報價轉為 `EXPIRED`。
- `checkPurchaseOrderConfirmationTimeout`: 每分鐘處理成立超過 24 小時仍未完成必要方確認的訂單，依未確認責任方扣分並重開委託。
- `checkPurchaseOrderAbnormalTimeout`: 每分鐘處理異常 12 小時未回應並扣 10 分。
- `autoCompletePurchaseOrders`: 每 10 分鐘檢查交貨期限加 24 小時後仍無異常的訂單並自動完成。

### 接單人確認交易

`POST /api/v1/purchase-orders/{orderId}/confirm/runner`

Auth: required，只有接單人可操作。`FIXED` 固定酬金接單已在承接時自動標記接單人確認；此 API 主要提供 `QUOTE` 報價模式或相容既有訂單使用。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 接單人開始處理

`POST /api/v1/purchase-orders/{orderId}/start`

Auth: required，只有接單人可操作。

- 訂單必須是 `CONFIRMED`。
- 成功後改為 `IN_PROGRESS` 並記錄 `RUNNER_STARTED` 事件。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 回報商品缺貨

`POST /api/v1/purchase-orders/{orderId}/item-unavailable`

Auth: required，只有接單人可操作。

Request:

```json
{
  "reason": "現場找不到商品",
  "imageUrls": ["shelf.jpg"],
  "storeName": "中和店"
}
```

規則:

- 訂單必須是 `WAITING_CONFIRMATION`、`CONFIRMED` 或 `IN_PROGRESS`。
- 訂單改為 `UNAVAILABLE_CANCELLED`，不扣信用分。
- 原得標報價改為 `UNAVAILABLE_CANCELLED`。
- 固定酬金委託改為 `AWAITING_REOPEN`，等待委託人決定是否重新開放。
- 報價制委託維持既有規則：原委託未過期時重新開放，否則改為 `EXPIRED`。
- 原因、門市與證據圖片寫入訂單事件 metadata。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 委託人取消訂單

`POST /api/v1/purchase-orders/{orderId}/cancel/requester`

Request:

```json
{
  "reason": "臨時不需要了"
}
```

扣分與重開規則:

- `WAITING_CONFIRMATION` / `CONFIRMED`: 扣 `2` 分，原委託未過期時重新開放。
- `IN_PROGRESS`: 扣 `10` 分，原委託改為 `CANCELLED`，不重新募集。
- 訂單改為 `PENALIZED_CANCELLED`，扣分與原因寫入事件 metadata。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 接單人取消訂單

`POST /api/v1/purchase-orders/{orderId}/cancel/runner`

Request 格式與委託人取消相同。

扣分規則:

- `WAITING_CONFIRMATION`: 扣 `2` 分。
- `CONFIRMED` / `IN_PROGRESS`: 扣 `10` 分。
- 原委託未過期時重新開放，否則改為 `EXPIRED`。
- `SHIPPED`、`DELIVERED_PENDING_CONFIRM`、`COMPLETED` 不可取消。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 宅配或店到店標記寄出

`POST /api/v1/purchase-orders/{orderId}/ship`

Auth: required，只有接單人可操作。

Request:

```json
{
  "logisticsProvider": "黑貓宅急便",
  "trackingNumber": "123456789",
  "trackingUrl": "https://example.com/tracking/123456789"
}
```

- 交付方式必須是 `HOME_DELIVERY`、`STORE_TO_STORE` 或 `IBOX`。
- 訂單必須是 `IN_PROGRESS`。
- `logisticsProvider`、`trackingNumber` 必填。
- 成功後改為 `SHIPPED`。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 面交標記已交貨

`POST /api/v1/purchase-orders/{orderId}/deliver`

Auth: required，只有接單人可操作。

- 交付方式必須是 `FACE_TO_FACE`。
- 訂單必須是 `IN_PROGRESS`。
- 成功後改為 `DELIVERED_PENDING_CONFIRM`。

Response: `200 OK`，`PurchaseOrderDetailResponse`。

### 委託人完成訂單

`POST /api/v1/purchase-orders/{orderId}/complete`

Auth: required，只有委託人可操作。

- 一般允許狀態：`SHIPPED`、`DELIVERED_PENDING_CONFIRM`。
- 面交訂單也允許委託人在 `IN_PROGRESS` 直接確認完成。
- 成功後改為 `COMPLETED` 並記錄完成事件。

Response: `200 OK`，`PurchaseOrderDetailResponse`。
