# Costco Group Buying - 資料庫設計 (Database Schema) v2.0

本專案採用關聯式資料庫 (MySQL / PostgreSQL)，共設計 11 張核心實體表。
*備註：所有資料表皆具備 `created_at` 與 `updated_at` (DATETIME) 系統自動維護之審計欄位。*

## 🏢 1. 基礎設定與實體店鋪 (Infrastructure)

### 1.1 `stores` (門市與營業時間表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | INT | PK, Auto Inc. | | |
| `name` | VARCHAR | | | 門市名稱 (例：中和店) |
| `latitude` | DECIMAL(10,7) | | | 官方緯度 (供 LBS 搜尋) |
| `longitude`| DECIMAL(10,7) | | | 官方經度 (供 LBS 搜尋) |
| `address` | VARCHAR | | | 完整地址 |
| `open_time` | TIME | | | 常態開店時間 (例：10:00:00) |
| `close_time` | TIME | | | 常態閉店時間 (例：21:30:00) |
| `is_active` | BOOLEAN | | TRUE | 是否營業中 |

### 1.2 `categories` (商品分類表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | INT | PK, Auto Inc. | | |
| `name` | VARCHAR | | | 分類名稱 (如：牛奶、生鮮) |
| `icon` | VARCHAR | | | UI 顯示用圖示 (URL 或 Emoji) |
| `sort_order` | INT | | 0 | 排序權重 |

---

## 👤 2. 使用者與社交關聯 (Users & Social)

### 2.1 `users` (使用者主表)
處理 Line OAuth2 登入、權限與個人信用數據指標。
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | 系統唯一識別碼 |
| `line_uid` | VARCHAR | UNIQUE, INDEX | | Line 授權回傳的 UID |
| `display_name` | VARCHAR | | | 顯示暱稱 |
| `profile_image_url`| VARCHAR | | | 頭像連結 |
| `has_costco_membership`| BOOLEAN| | FALSE | 是否有好市多會員卡 (限制開團) |
| `credit_score` | INT | | 100 | 綜合信用評分 |
| `no_show_count` | INT | | 0 | 面交放鳥次數 |
| `total_hosted_count`| INT | | 0 | 總發起成團數 |
| `host_cancel_count` | INT | | 0 | 團主咎責取消次數 |
| `total_joined_count`| INT | | 0 | 總參與成團數 |
| `participant_cancel_count`|INT| | 0 | 團員咎責反悔次數 |
| `status` | VARCHAR | | 'ACTIVE' | 帳號狀態 (ACTIVE, SUSPENDED) |

### 2.2 `user_preferences` (用戶設定表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `user_id` | BIGINT | PK, FK (users.id) | | |
| `receive_notifications`| BOOLEAN | | TRUE | 是否接收站內推播 |
| `notification_mode` | VARCHAR | | 'ALL' | 通知層級 (ALL, PREF_ONLY, NONE) |
| `favorite_store_ids`| JSON | | [] | 常用門市 ID 陣列 (例：[1, 3]) |
| `preferred_category_ids`| JSON | | [] | 偏好的商品分類 ID 陣列 |

### 2.3 `user_follows` (關注表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `follower_id` | BIGINT | FK (users.id) | | 粉絲的 ID |
| `host_id` | BIGINT | FK (users.id) | | 被關注的團主 ID |
> ⚠️ **UNIQUE KEY (follower_id, host_id):** 防止重複追蹤。

### 2.4 `blocks` (黑名單表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `blocker_id` | BIGINT | FK (users.id) | | 執行拉黑者 |
| `blocked_id` | BIGINT | FK (users.id) | | 被封鎖者 |
> ⚠️ **UNIQUE KEY (blocker_id, blocked_id):** 防止重複拉黑。

---

## 🛒 3. 合購與認購核心 (Campaigns & Orders)

### 3.1 `campaigns` (開團主表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `host_id` | BIGINT | FK (users.id), INDEX| | 開團者 ID |
| `category_id` | INT | FK (categories.id) | | 商品分類 ID |
| `store_id` | INT | FK (stores.id), INDEX | | 關聯門市 ID |
| `scenario_type` | VARCHAR | | | 開團情境 (INSTANT, SCHEDULED) |
| `item_name` | VARCHAR | | | 商品名稱 |
| `item_image_url` | VARCHAR | | | 現場照片 S3 連結 |
| `price_per_unit` | INT | | | 預估單份金額 |
| `total_quantity` | INT | | | 總共分出數量 |
| `available_quantity`| INT | | | 剩餘可認購數量 (與 Redis 同步) |
| `meetup_location` | VARCHAR | | | 預計面交地點 |
| `meetup_time` | DATETIME | | NULL | 預計面交時間 |
| `expire_time` | DATETIME | INDEX | NULL | 單據失效/流局時間 |
| `status` | VARCHAR | INDEX | 'OPEN' | 狀態 (OPEN, FULL, COMPLETED, CANCEL_PENDING, CANCELLED, EXPIRED) |
| `blame_user_id` | BIGINT | FK (users.id) | NULL | 若取消，歸咎於哪位會員 (記點用) |
| `cancel_reason` | VARCHAR | | NULL | 取消原因說明 |

### 3.2 `participants` (認購明細表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `campaign_id` | BIGINT | FK (campaigns.id) | | 所屬合購單 |
| `user_id` | BIGINT | FK (users.id) | | 參與者 |
| `quantity` | INT | | | 認購數量 |
| `status` | VARCHAR | | 'JOINED' | 狀態 (JOINED, CANCELLED, NO_SHOW) |
> ⚠️ **UNIQUE KEY (campaign_id, user_id):** 防止同一個人在同一單產生兩筆明細 (防連點)。

---

## 💬 4. 互動、通訊與評價 (Interactions & Logs)

### 4.1 `chat_messages` (聊天紀錄表)
> ⚠️ 系統排程：保留 3 個月後自動刪除。
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `campaign_id` | BIGINT | FK (campaigns.id), INDEX| | 所屬聊天室 (等同團單 ID) |
| `sender_id` | BIGINT | FK (users.id) | | 發送者 |
| `message_type` | VARCHAR | | 'TEXT' | 訊息類型 (TEXT, IMAGE, SYSTEM) |
| `content` | TEXT | | | 訊息內容 |

### 4.2 `reviews` (評價表)
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `campaign_id` | BIGINT | FK (campaigns.id) | | 發生交易的團單 |
| `reviewer_id` | BIGINT | FK (users.id) | | 評價者 |
| `reviewee_id` | BIGINT | FK (users.id) | | 被評價者 |
| `rating` | INT | CHECK (1-5) | | 星等 (1-5) |
| `comment` | VARCHAR | | | 文字評論 |
> ⚠️ **UNIQUE KEY (campaign_id, reviewer_id, reviewee_id):** 確保一單只能互評一次 (防洗評價)。

### 4.3 `notifications` (站內通知表)
> ⚠️ 系統排程：保留過期後 7 天自動刪除。
| 欄位名稱 | 型態 | 鍵值 / 約束 | 預設值 | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| `id` | BIGINT | PK, Auto Inc. | | |
| `user_id` | BIGINT | FK (users.id), INDEX| | 接收通知的會員 |
| `type` | VARCHAR | | | 通知類型 (NEW_CAMPAIGN, CAMPAIGN_FULL 等) |
| `reference_id` | BIGINT | | | 關聯的 Entity ID (如 campaign_id) |
| `content` | VARCHAR | | | 推播文字內容 |
| `is_read` | BOOLEAN | | FALSE | 是否已讀 |
| `expire_time` | DATETIME | INDEX | NULL | 跟隨關聯團單的過期時間 |