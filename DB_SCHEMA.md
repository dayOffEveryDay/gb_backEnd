# DB Schema

## Latest Updates

- `campaigns.completed_at` is used to record when a campaign is fully completed.
- Current `campaigns.status` usage in code includes `OPEN`, `FULL`, `DELIVERED`, `COMPLETED`, `CANCELLED`, and `HOST_NO_SHOW`.
- `notifications.type` now includes `CAMPAIGN_COMPLETED` for review reminder pushes after campaign completion.
- Review lookup uses the existing unique key on `(campaign_id, reviewer_id, reviewee_id)` to query whether a review already exists.

本文件依據 [`sql.sql`](c:/Users/HowardLu/IdeaProjects/ahui/costoco/backend/gb_backEnd/sql.sql) 整理目前 `gbc` schema 的資料表結構。

## 1. 基礎資料

### `stores`

門市主檔。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `INT` | 主鍵，自增 |
| `name` | `VARCHAR(50)` | 門市名稱 |
| `latitude` | `DECIMAL(10,7)` | 緯度 |
| `longitude` | `DECIMAL(10,7)` | 經度 |
| `address` | `VARCHAR(255)` | 門市地址 |
| `open_time` | `TIME` | 營業開始時間 |
| `close_time` | `TIME` | 營業結束時間 |
| `is_active` | `BOOLEAN` | 是否啟用，預設 `TRUE` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

補充：
- `sql.sql` 內含門市初始資料。

### `categories`

商品分類主檔。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `INT` | 主鍵，自增 |
| `name` | `VARCHAR(100)` | 分類名稱 |
| `icon` | `VARCHAR(255)` | 分類圖示 |
| `sort_order` | `INT` | 排序值，預設 `0` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

補充：
- `sql.sql` 內含分類初始資料。

## 2. 使用者與社交

### `users`

使用者主檔。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `line_uid` | `VARCHAR(255)` | LINE UID，唯一 |
| `display_name` | `VARCHAR(255)` | 顯示名稱 |
| `profile_image_url` | `VARCHAR(512)` | 頭像網址 |
| `has_costco_membership` | `BOOLEAN` | 是否具有 Costco 會員，預設 `FALSE` |
| `credit_score` | `INT` | 信用分數，預設 `100` |
| `no_show_count` | `INT` | 爽約次數，預設 `0` |
| `total_hosted_count` | `INT` | 開團總次數，預設 `0` |
| `host_cancel_count` | `INT` | 團主取消次數，預設 `0` |
| `total_joined_count` | `INT` | 參團總次數，預設 `0` |
| `participant_cancel_count` | `INT` | 參團者取消次數，預設 `0` |
| `status` | `VARCHAR(50)` | 使用者狀態，預設 `ACTIVE` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

`status` 可能值：
- `ACTIVE`
- `SUSPENDED`

### `user_preferences`

使用者通知與偏好設定。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `user_id` | `BIGINT` | 主鍵，FK -> `users.id` |
| `receive_notifications` | `BOOLEAN` | 是否接收通知，預設 `TRUE` |
| `notification_mode` | `VARCHAR(50)` | 通知模式，預設 `ALL` |
| `favorite_store_ids` | `JSON` | 常用門市 ID 清單 |
| `preferred_category_ids` | `JSON` | 偏好分類 ID 清單 |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

`notification_mode` 可能值：
- `ALL`
- `PREF_ONLY`
- `NONE`

### `user_follows`

追蹤關係。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `follower_id` | `BIGINT` | 追蹤者，FK -> `users.id` |
| `host_id` | `BIGINT` | 被追蹤的團主，FK -> `users.id` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

限制：
- `UNIQUE (follower_id, host_id)`

### `blocks`

封鎖關係。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `blocker_id` | `BIGINT` | 封鎖者，FK -> `users.id` |
| `blocked_id` | `BIGINT` | 被封鎖者，FK -> `users.id` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

限制：
- `UNIQUE (blocker_id, blocked_id)`

## 3. 開團與參與

### `campaigns`

開團主表。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `host_id` | `BIGINT` | 團主 ID，FK -> `users.id` |
| `category_id` | `INT` | 分類 ID，FK -> `categories.id` |
| `store_id` | `INT` | 門市 ID，FK -> `stores.id` |
| `scenario_type` | `VARCHAR(50)` | 開團情境類型 |
| `item_name` | `VARCHAR(255)` | 商品名稱 |
| `image_urls` | `VARCHAR(1000)` | 商品圖片識別字串 |
| `price_per_unit` | `INT` | 單價 |
| `total_quantity` | `INT` | 總數量 |
| `available_quantity` | `INT` | 可分配數量 |
| `host_reserved_quantity` | `INT` | 團主保留數量，預設 `0` |
| `meetup_location` | `VARCHAR(255)` | 面交地點 |
| `meetup_time` | `DATETIME` | 面交時間 |
| `expire_time` | `DATETIME` | 開團截止時間 |
| `status` | `VARCHAR(50)` | 開團狀態，預設 `OPEN` |
| `blame_user_id` | `BIGINT` | 歸責使用者 ID，FK -> `users.id` |
| `cancel_reason` | `VARCHAR(255)` | 取消原因 |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |
| `allow_revision` | `BOOLEAN` | 是否允許修改，預設 `FALSE` |

`scenario_type` 可能值：
- `INSTANT`
- `SCHEDULED`

`status` 可能值：
- `OPEN`
- `FULL`
- `COMPLETED`
- `EXPIRED`
- `CANCEL_PENDING`
- `CANCELLED`

索引：
- `KEY host_id (host_id)`
- `KEY category_id (category_id)`
- `KEY blame_user_id (blame_user_id)`
- `KEY idx_store_status_expire (store_id, status, expire_time)`

### `participants`

參團資料。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `campaign_id` | `BIGINT` | FK -> `campaigns.id` |
| `user_id` | `BIGINT` | FK -> `users.id` |
| `quantity` | `INT` | 參團數量 |
| `status` | `VARCHAR(50)` | 參團狀態，預設 `JOINED` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

`status` 可能值：
- `JOINED`
- `CANCELLED`
- `NO_SHOW`

限制：
- `UNIQUE (campaign_id, user_id)`

## 4. 互動與紀錄

### `chat_messages`

開團聊天室訊息。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `campaign_id` | `BIGINT` | FK -> `campaigns.id` |
| `sender_id` | `BIGINT` | 發送者，FK -> `users.id` |
| `message_type` | `VARCHAR(50)` | 訊息類型，預設 `TEXT` |
| `content` | `TEXT` | 訊息內容 |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

`message_type` 可能值：
- `TEXT`
- `IMAGE`
- `SYSTEM`

索引：
- `INDEX idx_created_at (created_at)`

### `reviews`

評價紀錄。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `campaign_id` | `BIGINT` | FK -> `campaigns.id` |
| `reviewer_id` | `BIGINT` | 評價者，FK -> `users.id` |
| `reviewee_id` | `BIGINT` | 被評價者，FK -> `users.id` |
| `rating` | `INT` | 評分，1 到 5 |
| `comment` | `VARCHAR(255)` | 評語 |
| `is_score_counted` | `BOOLEAN` | 是否已納入分數計算，預設 `FALSE` |
| `created_at` | `DATETIME` | 建立時間 |

限制與索引：
- `UNIQUE (campaign_id, reviewer_id, reviewee_id)`
- `INDEX idx_anti_fraud (reviewer_id, reviewee_id, is_score_counted, created_at)`

### `notifications`

通知資料。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `user_id` | `BIGINT` | 接收者，FK -> `users.id` |
| `type` | `VARCHAR(50)` | 通知類型 |
| `reference_id` | `BIGINT` | 關聯實體 ID |
| `content` | `VARCHAR(500)` | 通知內容 |
| `is_read` | `BOOLEAN` | 是否已讀，預設 `FALSE` |
| `expire_time` | `DATETIME` | 過期時間，可為 `NULL` |
| `created_at` | `DATETIME` | 建立時間 |
| `updated_at` | `DATETIME` | 更新時間 |

索引：
- `INDEX idx_expire_time (expire_time)`

### `credit_score_logs`

信用分數異動紀錄。

| 欄位 | 型別 | 說明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主鍵，自增 |
| `user_id` | `BIGINT` | FK -> `users.id` |
| `score_change` | `INT` | 分數增減 |
| `reason` | `VARCHAR(100)` | 異動原因 |
| `campaign_id` | `BIGINT` | 關聯開團 ID，FK -> `campaigns.id`，可為 `NULL` |
| `created_at` | `DATETIME` | 建立時間 |

索引：
- `INDEX idx_user_created (user_id, created_at)`

刪除規則：
- 關聯的 `campaign` 被刪除時，`campaign_id` 會被設為 `NULL`。

## 5. Foreign Key Summary

- `user_preferences.user_id` -> `users.id`
- `user_follows.follower_id` -> `users.id`
- `user_follows.host_id` -> `users.id`
- `blocks.blocker_id` -> `users.id`
- `blocks.blocked_id` -> `users.id`
- `campaigns.host_id` -> `users.id`
- `campaigns.category_id` -> `categories.id`
- `campaigns.store_id` -> `stores.id`
- `campaigns.blame_user_id` -> `users.id`
- `participants.campaign_id` -> `campaigns.id`
- `participants.user_id` -> `users.id`
- `chat_messages.campaign_id` -> `campaigns.id`
- `chat_messages.sender_id` -> `users.id`
- `reviews.campaign_id` -> `campaigns.id`
- `reviews.reviewer_id` -> `users.id`
- `reviews.reviewee_id` -> `users.id`
- `notifications.user_id` -> `users.id`
- `credit_score_logs.user_id` -> `users.id`
- `credit_score_logs.campaign_id` -> `campaigns.id`
