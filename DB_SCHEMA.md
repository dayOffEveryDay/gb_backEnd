# DB_SCHEMA.md

本文檔整理目前專案實際使用到的主要資料表與關鍵欄位，內容以 `entity` 與 `sql.sql` 為準。

## 1. campaigns

用途：團購主單。

主要欄位：

- `id` `BIGINT`：主鍵
- `host_id` `BIGINT`：主揪使用者 ID
- `category_id` `INT`：分類 ID
- `store_id` `INT`：門市 ID
- `scenario_type` `VARCHAR`：`INSTANT` / `SCHEDULED`
- `item_name` `VARCHAR`
- `image_urls` `VARCHAR`：多張圖片檔名以逗號串接
- `price_per_unit` `INT`
- `total_quantity` `INT`：對外開放認購總量
- `available_quantity` `INT`：剩餘可認購數量
- `host_reserved_quantity` `INT`：主揪保留數量
- `meetup_location` `VARCHAR`
- `meetup_time` `DATETIME`
- `expire_time` `DATETIME`
- `status` `VARCHAR`
- `blame_user_id` `BIGINT`：取消或違約責任人
- `cancel_reason` `VARCHAR`
- `allow_revision` `BOOLEAN`：是否允許滿單後修改，預設 `false`
- `created_at` `DATETIME`
- `updated_at` `DATETIME`

目前程式中會用到的 `status`：

- `OPEN`
- `FULL`
- `DELIVERED`
- `COMPLETED`
- `CANCELLED`
- `HOST_NO_SHOW`

`allow_revision` 行為：

- 主揪呼叫 `/api/v1/campaigns/{id}/unlock` 後會設為 `true`
- 團員在滿單狀態下，只有 `allow_revision = true` 才能修改數量或退出
- 一旦名額釋出、活動回到 `OPEN`，會自動重設為 `false`

## 2. participants

用途：活動參與者紀錄。

主要欄位：

- `id` `BIGINT`
- `campaign_id` `BIGINT`
- `user_id` `BIGINT`
- `quantity` `INT`
- `status` `VARCHAR`

目前程式中實際使用的狀態至少包含：

- `JOINED`
- `CONFIRMED`
- `CANCELLED`

## 3. notifications

用途：站內通知與 WebSocket 推播紀錄。

主要欄位：

- `id` `BIGINT`
- `user_id` `BIGINT`
- `type` `VARCHAR`
- `reference_id` `BIGINT`
- `content` `VARCHAR`
- `is_read` `BOOLEAN`
- `created_at` `DATETIME`

目前程式中已使用的通知型別：

- `CAMPAIGN_FULL`
- `CAMPAIGN_CANCELLED`
- `MEMBER_WITHDRAW`
- `KICKED`

通知除了寫入資料庫，也會透過 WebSocket 推送到：

- `/user/queue/notifications`

## 4. credit_score_logs

用途：信用分異動紀錄。

主要欄位：

- `id` `BIGINT`
- `user_id` `BIGINT`
- `score_change` `INT`
- `reason` `VARCHAR`
- `campaign_id` `BIGINT`
- `created_at` `DATETIME`

## 5. chat_messages

用途：聊天室訊息歷史紀錄。

主要欄位：

- `id` `BIGINT`
- `campaign_id` `BIGINT`
- `sender_id` `BIGINT`
- `message_type` `VARCHAR`
- `content` `TEXT`
- `created_at` `DATETIME`

## 6. users

用途：使用者主檔。

與本次文件更新較相關的欄位：

- `id`
- `display_name`
- `profile_image_url`
- `has_costco_membership`
- `credit_score`
- `participant_cancel_count`
