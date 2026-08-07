# LINE 商品分享頁 API

後端提供兩個不需 JWT 的 HTML endpoint，供前端複製網址到 LINE 聊天室。回應的初始 HTML 內含 Open Graph 標籤，因此 LINE 爬蟲不需執行 JavaScript，就能取得商品名稱、摘要與第一張商品圖片。

## 團購分享頁

```http
GET /share/campaigns/{campaignId}
Accept: text/html
```

頁面與 Open Graph 摘要包含：商品名稱、第一張商品圖片、單價、主揪名稱、面交地點與目前狀態。

範例：

```text
https://api.example.com/share/campaigns/123
```

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

| 環境變數 | 預設值 | 用途 |
| --- | --- | --- |
| `APP_BASE_URL` | `http://localhost:8080` | 分享網址與商品圖片的公開後端來源 |
| `FRONTEND_BASE_URL` | `http://localhost:5173` | 分享頁「查看完整詳情」按鈕的前端來源 |
| `CAMPAIGN_DETAIL_PATH` | `/campaigns/{id}` | 團購前端詳情路徑；必須保留 `{id}` |
| `PURCHASE_REQUEST_DETAIL_PATH` | `/purchase-requests/{id}` | 託購前端詳情路徑；必須保留 `{id}` |

正式環境的 `APP_BASE_URL` 與圖片網址必須是 LINE 伺服器可公開存取的 HTTPS 網址。分享頁使用 5 分鐘公開快取；商品更新後，聊天軟體已建立的預覽仍可能受 LINE 自身快取影響。
