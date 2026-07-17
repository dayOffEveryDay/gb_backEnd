# API 文件

本文件整理 `gb_backEnd` 目前提供的 REST API 與 WebSocket/STOMP 介面。

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
- `GET /api/v1/auth/dev-login`
- `GET /api/v1/stores`
- `GET /api/v1/categories`
- `GET /api/v1/campaigns`
- `GET /api/v1/purchase-requests`
- `GET /api/v1/purchase-requests/{id}`
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
- `deliveryMethod`: `FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE`
- `profileType`: `FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE`
- `locationType`: `TRANSIT_RANGE` / `LANDMARK` / `ROAD` / `DISTRICT` / `ADDRESS` / `STORE` / `CUSTOM`

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
    "profileImageUrl": "https://profile.line-scdn.net/...",
    "hasCostcoMembership": true
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

僅供開發測試使用，回傳 JWT 與 refresh token。

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
  "displayName": "A Hui",
  "hasCostcoMembership": true
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

- `profileType`: optional，`FACE_TO_FACE` / `HOME_DELIVERY` / `STORE_TO_STORE`

Response: `200 OK`

```json
[
  {
    "id": 1,
    "profileType": "FACE_TO_FACE",
    "locationType": "TRANSIT_RANGE",
    "label": "常用捷運面交",
    "city": "台北市",
    "district": null,
    "locationName": "板南線",
    "rangeStart": "府中",
    "rangeEnd": "市政府",
    "displayText": "板南線 府中～市政府",
    "detail": {
      "transitSystem": "TAIPEI_MRT"
    },
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
  "locationType": "TRANSIT_RANGE",
  "label": "常用捷運面交",
  "city": "台北市",
  "district": null,
  "locationName": "板南線",
  "rangeStart": "府中",
  "rangeEnd": "市政府",
  "displayText": "板南線 府中～市政府",
  "detail": {
    "transitSystem": "TAIPEI_MRT"
  },
  "isDefault": false
}
```

Response: `201 Created`，body 為完整 `UserDeliveryProfileResponse`。

驗證規則:

- `profileType`、`locationType`、`displayText` 必填。
- enum 值必須符合本文件「Purchase request enum values」。

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

面交 Request:

```json
{
  "itemName": "莓姬梅酒",
  "itemDescription": "希望購買草莓口味",
  "imageUrls": [
    "http://localhost:8080/uploads/general/3c835b6a-552a-473c-aee3-51180c94976d.png"
  ],
  "rewardType": "FIXED",
  "fixedRewardAmount": 100,
  "requesterCity": "台北市",
  "requesterDistrict": "中山區",
  "deliveryMethod": "FACE_TO_FACE",
  "deliveryPublicSummary": "板南線 府中～市政府、台北車站",
  "requestExpireTime": "2026-07-10T23:59:59",
  "deliveryDeadlineHours": 72,
  "minCreditScore": 80,
  "selectedProfileIds": [1, 2],
  "manualMeetupRanges": [
    {
      "locationType": "LANDMARK",
      "city": "台北市",
      "district": "中正區",
      "locationName": "台北車站",
      "rangeStart": null,
      "rangeEnd": null,
      "displayText": "台北車站",
      "detail": {},
      "latitude": null,
      "longitude": null
    }
  ],
  "saveManualToProfile": true,
  "saveDeliveryToProfile": false
}
```

宅配或店到店時須提供 `deliveryPrivateDetail`，例如:

```json
{
  "deliveryMethod": "HOME_DELIVERY",
  "deliveryPublicSummary": "宅配：台北市中山區",
  "deliveryPrivateDetail": {
    "receiverName": "王小明",
    "receiverPhone": "0912345678",
    "city": "台北市",
    "district": "中山區",
    "addressDetail": "中山路 2 段 185 號 2 樓"
  },
  "saveDeliveryToProfile": true
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

- `itemName`、`rewardType`、`requesterCity`、`deliveryMethod`、`deliveryPublicSummary` 必填。
- `FIXED` 模式的 `fixedRewardAmount` 必須大於 `0`；`QUOTE` 模式會忽略固定酬金。
- `requestExpireTime` 必須是未來時間。
- `deliveryDeadlineHours` 必須大於 `0`，`minCreditScore` 不可小於 `0`。
- 面交至少需要一筆 `selectedProfileIds` 或 `manualMeetupRanges`。
- 宅配及店到店必須提供非空的 `deliveryPrivateDetail`。
- 選取的常用面交資料必須屬於目前登入者，且 `profileType` 必須是 `FACE_TO_FACE`。

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
      "itemName": "莓姬梅酒",
      "imageUrls": ["http://localhost:8080/uploads/general/3c835b6a-552a-473c-aee3-51180c94976d.png"],
      "rewardType": "FIXED",
      "fixedRewardAmount": 100,
      "requesterCity": "台北市",
      "requesterDistrict": "中山區",
      "deliveryMethod": "FACE_TO_FACE",
      "deliveryPublicSummary": "板南線 府中～市政府、台北車站",
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

列表不回傳 `deliveryPrivateDetail`。

### 取得委託單詳情

`GET /api/v1/purchase-requests/{id}`

Auth: not required；登入後若為已成立訂單的交易雙方，可取得私密交付資料。

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
  "requesterCity": "台北市",
  "requesterDistrict": "中山區",
  "deliveryMethod": "FACE_TO_FACE",
  "deliveryPublicSummary": "板南線 府中～市政府、台北車站",
  "deliveryPrivateDetail": null,
  "meetupRanges": [
    {
      "id": 1,
      "sourceProfileId": 1,
      "locationType": "TRANSIT_RANGE",
      "city": "台北市",
      "district": null,
      "locationName": "板南線",
      "rangeStart": "府中",
      "rangeEnd": "市政府",
      "displayText": "板南線 府中～市政府",
      "detail": {},
      "latitude": null,
      "longitude": null
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

`deliveryPrivateDetail` 可見條件:

- 委託單已產生 `purchase_orders`。
- 目前登入者是該訂單的委託人或接單人。
- 未登入、非交易雙方或尚未成立訂單時固定回傳 `null`。

### 修改委託單

`PUT /api/v1/purchase-requests/{id}`

Auth: required，只有委託人可操作。

Request 使用部分更新語意，只需傳入要修改的欄位:

```json
{
  "itemName": "更新後商品名稱",
  "requestExpireTime": "2026-07-15T23:59:59",
  "deliveryDeadlineHours": 96,
  "selectedProfileIds": [1],
  "manualMeetupRanges": []
}
```

規則:

- `ORDERED`、`EXPIRED`、`CANCELLED` 不可修改。
- `OPEN` 且沒有有效報價時，可修改商品、酬金、交付方式、交付資料與期限。
- `QUOTING` 或已有 `ACTIVE` 報價時，只能傳入較晚的 `requestExpireTime`。
- 傳入 `selectedProfileIds` 或 `manualMeetupRanges` 時會重建面交快照。
- 改成宅配或店到店時必須提供 `deliveryPrivateDetail`。

Response: `200 OK`，body 為更新後的 `PurchaseRequestDetailResponse`。此操作的 response 不揭露私密交付資料。

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
  "runnerId": 2,
  "runnerDisplayName": "Runner",
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

- 委託商品、圖片與交易雙方
- 固定酬金或得標報價金額
- `deliveryPrivateDetail` 與面交範圍快照
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
- `POST /api/v1/purchase-chat-rooms/{roomId}/read`: 將聊天室標記已讀，response `204`。
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
- 原委託未過期時重新開放，否則改為 `EXPIRED`。
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

- 交付方式必須是 `HOME_DELIVERY` 或 `STORE_TO_STORE`。
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
