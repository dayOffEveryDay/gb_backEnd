# LINE 商品分享頁 API

後端提供兩個不需 JWT 的 HTML endpoint，供前端複製網址到 LINE 聊天室。回應的初始 HTML 內含 Open Graph 標籤，因此 LINE 爬蟲不需執行 JavaScript，就能取得商品名稱、摘要與第一張商品圖片。

## 團購分享頁

```http
GET /share/campaigns/{campaignId}
Accept: text/html
```

頁面與 Open Graph 摘要包含：商品名稱、第一張商品圖片、門市、單價與剩餘數量。

```html
<meta property="og:title" content="金色三麥包種茶啤酒">
<meta property="og:description" content="中和店・NT$ 85・剩餘 5 件">
<meta property="og:image" content="https://example.com/images/campaign-8-1.jpg">
<meta property="og:url" content="https://gb.ttgtool.com/share/campaigns/8">
```

團購不存在時回 `404 Not Found`；狀態為 `CANCELLED` 或 `CANCEL_PENDING` 時回 `410 Gone`。

範例：

```text
https://api.example.com/share/campaigns/123
```

分享頁載入後，前端可透過不需 JWT 的公開詳情 API 取得顯示資料：

```http
GET /api/v1/public/campaigns/{campaignId}
Accept: application/json
```

公開詳情 API 不包含參加者、聊天、聯絡資料、團主私人會員資料或登入者參團狀態。已滿、已截止及失敗的團購仍回 `200 OK`，並透過 `status` 與 `canJoin: false` 呈現。

## 託購分享頁

```http
GET /share/purchase-requests/{purchaseRequestId}
Accept: text/html
```

頁面與 Open Graph 摘要包含：商品名稱、第一張商品圖片、酬勞、委託人名稱、公開交付摘要與目前狀態。此頁不會讀取或輸出 `deliveryPrivateDetail`。

範例：

```text
https://api.example.com/share/purchase-requests/456
```

## 部署設定

| 環境變數 | local 預設值 | prod | 用途 |
| --- | --- | --- | --- |
| `APP_BASE_URL` | `http://localhost:8080` | 必填 | 分享網址與商品圖片的公開後端來源 |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | optional，預設同 `APP_BASE_URL` | 分享頁「查看完整詳情」按鈕的前端來源；前後端分網域時才需設定 |
| `CAMPAIGN_DETAIL_PATH` | `/campaigns/{id}` | optional | 團購前端詳情路徑；必須保留 `{id}` |
| `PURCHASE_REQUEST_DETAIL_PATH` | `/purchase-requests/{id}` | optional | 託購前端詳情路徑；必須保留 `{id}` |

`prod` profile 啟動時必須提供 `APP_BASE_URL`，否則 Spring Boot 會因缺少設定而拒絕啟動。未設定 `FRONTEND_BASE_URL` 時，分享頁按鈕會沿用 `APP_BASE_URL`。`APP_BASE_URL` 與圖片網址必須是 LINE 伺服器可公開存取的 HTTPS 網址。分享頁使用 5 分鐘公開快取；商品更新後，聊天軟體已建立的預覽仍可能受 LINE 自身快取影響。
