-- CREATE SCHEMA `gbc` ;
-- ==============================================================================
-- 1. 基礎設定與實體店鋪 (Infrastructure)
-- ==============================================================================
USE `gbc`;
CREATE TABLE stores (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '門市名稱 (例：中和店)',
    latitude DECIMAL(10, 7) NOT NULL COMMENT '官方緯度',
    longitude DECIMAL(10, 7) NOT NULL COMMENT '官方經度',
    address VARCHAR(255) COMMENT '完整地址',
    open_time TIME NOT NULL COMMENT '常態開店時間 (例：10:00:00)',
    close_time TIME NOT NULL COMMENT '常態閉店時間 (例：21:30:00)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否營業中',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO stores (
    name,
    latitude,
    longitude,
    address,
    open_time,
    close_time,
    is_active
)
SELECT
    t.name,
    t.latitude,
    t.longitude,
    t.address,
    t.open_time,
    t.close_time,
    t.is_active
FROM (
    SELECT '內湖店' AS name, 25.0644051 AS latitude, 121.5755129 AS longitude, '台北市內湖區舊宗路一段268號' AS address, '10:00:00' AS open_time, '21:30:00' AS close_time, TRUE AS is_active
    UNION ALL
    SELECT '汐止店', 25.0561720, 121.6331690, '新北市汐止區大同路一段158號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '中和店', 25.0027050, 121.4919580, '新北市中和區中山路二段347號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '新莊店', 25.0274737, 121.4346690, '新北市新莊區建國一路138號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '北投店', 25.1268244, 121.4714015, '台北市北投區立德路117號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '桃園南崁店', 25.0542610, 121.2821200, '桃園市蘆竹區南崁路一段369號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '桃園中壢店', 24.9630700, 121.1557800, '桃園市中壢區民族路六段508號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '新竹店', 24.7932400, 121.0133800, '新竹市東區慈雲路188號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '北台中店', 24.1868057, 120.7079927, '台中市北屯區敦富路366號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '台中店', 24.1331640, 120.6497470, '台中市南屯區文心南三路289號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '嘉義店', 23.5017710, 120.4503097, '嘉義市東區忠孝路668號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '台南店', 23.0099820, 120.1935730, '台南市北區和緯路四段8號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '北高雄大順店', 22.6566236, 120.3070700, '高雄市鼓山區大順一路111號', '10:00:00', '21:30:00', TRUE
    UNION ALL
    SELECT '高雄店', 22.6037260, 120.3063700, '高雄市前鎮區中華五路656號', '10:00:00', '21:30:00', TRUE
) AS t
WHERE NOT EXISTS (
    SELECT 1
    FROM stores s
    WHERE s.name = t.name
);
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '分類名稱 (如：牛奶、生鮮)',
    icon VARCHAR(255) COMMENT 'UI 顯示用圖示',
    sort_order INT DEFAULT 0 COMMENT '排序權重',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO `categories` (`name`, `icon`, `sort_order`)
SELECT t.`name`, t.`icon`, t.`sort_order`
FROM (
    SELECT '生鮮' AS `name`, '🥬' AS `icon`, 1 AS `sort_order`
    UNION ALL SELECT '麵包', '🍞', 2
    UNION ALL SELECT '熟食', '🍱', 3
    UNION ALL SELECT '冷凍食品', '🧊', 4
    UNION ALL SELECT '飲料', '🥤', 5
    UNION ALL SELECT '動植物奶', '🥛', 6
    UNION ALL SELECT '肉類', '🥩', 7
    UNION ALL SELECT '零食', '🍪', 8
    UNION ALL SELECT '衛生紙', '🧻', 9
    UNION ALL SELECT '清潔劑', '🧴', 10
    UNION ALL SELECT '保健品', '💊', 11
    UNION ALL SELECT '其他', '📦', 12
) AS t
LEFT JOIN `categories` c ON c.`name` = t.`name`
WHERE c.`id` IS NULL;
-- ==============================================================================
-- 2. 使用者與社交關聯 (Users & Social)
-- ==============================================================================

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    line_uid VARCHAR(255) UNIQUE NOT NULL COMMENT 'Line 授權 UID',
    display_name VARCHAR(255) NOT NULL COMMENT '顯示暱稱',
    profile_image_url VARCHAR(512) COMMENT '頭像連結',
    
    -- 信用與交易指標
    credit_score INT DEFAULT 100 COMMENT '綜合信用評分 (受評價影響)',
    no_show_count INT DEFAULT 0 COMMENT '面交放鳥次數',
    total_hosted_count INT DEFAULT 0 COMMENT '總發起成團數',
    host_cancel_count INT DEFAULT 0 COMMENT '團主咎責取消次數 (算開團取消率)',
    total_joined_count INT DEFAULT 0 COMMENT '總參與成團數',
    participant_cancel_count INT DEFAULT 0 COMMENT '團員咎責反悔次數 (算跟團反悔率)',
    
    status VARCHAR(50) DEFAULT 'ACTIVE' COMMENT '帳號狀態 (ACTIVE, SUSPENDED)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    receive_notifications BOOLEAN DEFAULT TRUE COMMENT '是否接收站內推播',
    notification_mode VARCHAR(50) DEFAULT 'ALL' COMMENT '通知層級 (ALL, PREF_ONLY, NONE)',
    favorite_store_ids JSON COMMENT '常用門市 ID 陣列 (例：[1, 3])',
    preferred_category_ids JSON COMMENT '偏好的商品分類 ID 陣列',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL COMMENT '粉絲的 ID',
    host_id BIGINT NOT NULL COMMENT '被關注的團購主 ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (host_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_follow (follower_id, host_id)
);

CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) NOT NULL COMMENT '給前端換發用的隨機字串',
  `expiry_date` datetime(6) NOT NULL COMMENT '絕對到期時間',
  `user_id` bigint NOT NULL COMMENT '關聯到哪一個使用者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token` (`token`), -- 確保 token 不重複，加快查詢速度
  KEY `idx_refresh_token_user` (`user_id`),
  CONSTRAINT `fk_refresh_token_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存放長效型 Refresh Token';

CREATE TABLE blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT NOT NULL COMMENT '執行拉黑者',
    blocked_id BIGINT NOT NULL COMMENT '被封鎖者',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_block (blocker_id, blocked_id)
);

-- ==============================================================================
-- 3. 合購與認購核心 (Campaigns & Orders)
-- ==============================================================================

CREATE TABLE `campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `host_id` bigint NOT NULL COMMENT '開團者 ID',
  `category_id` int NOT NULL COMMENT '商品分類 ID',
  `store_id` int NOT NULL COMMENT '關聯 stores 表 ID',
  `scenario_type` varchar(50) NOT NULL COMMENT '開團情境 (INSTANT, SCHEDULED)',
  `item_name` varchar(255) NOT NULL COMMENT '商品名稱',
  `image_urls` varchar(1000) DEFAULT NULL COMMENT '存放圖片UUID，多張以逗號分隔',
  `price_per_unit` int DEFAULT NULL COMMENT '預估單份金額',
  `total_quantity` int NOT NULL COMMENT '總共分出數量',
  `available_quantity` int NOT NULL COMMENT '剩餘可認購數量 (與 Redis 同步)',
  `host_reserved_quantity` int NOT NULL DEFAULT '0' COMMENT '團主自留數量',
  `meetup_location` varchar(255) DEFAULT NULL COMMENT '預計面交地點',
  `meetup_time` datetime DEFAULT NULL COMMENT '預計面交時間',
  `expire_time` datetime DEFAULT NULL COMMENT '單據失效時間 (需做防呆限制)',
  `status` varchar(50) DEFAULT 'OPEN' COMMENT '狀態 (OPEN, FULL, COMPLETED, EXPIRED, CANCEL_PENDING, CANCELLED)',
  `blame_user_id` bigint DEFAULT NULL COMMENT '若取消，歸咎於哪位會員 (記點用)',
  `cancel_reason` varchar(255) DEFAULT NULL COMMENT '取消原因說明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `allow_revision` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否允許滿單後修改',
  `completed_at` datetime DEFAULT NULL COMMENT '合購單結案時間',
  PRIMARY KEY (`id`),
  KEY `host_id` (`host_id`),
  KEY `category_id` (`category_id`),
  KEY `blame_user_id` (`blame_user_id`),
  KEY `idx_store_status_expire` (`store_id`,`status`,`expire_time`),
  CONSTRAINT `campaigns_ibfk_1` FOREIGN KEY (`host_id`) REFERENCES `users` (`id`),
  CONSTRAINT `campaigns_ibfk_2` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `campaigns_ibfk_3` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),
  CONSTRAINT `campaigns_ibfk_4` FOREIGN KEY (`blame_user_id`) REFERENCES `users` (`id`)
)  ;

CREATE TABLE `participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `campaign_id` bigint NOT NULL COMMENT '所屬合購單',
  `user_id` bigint NOT NULL COMMENT '參與者',
  `quantity` int NOT NULL COMMENT '認購數量',
  `status` varchar(50) DEFAULT 'JOINED' COMMENT 'JOINED, CONFIRMED, DISPUTED, NO_SHOW, CANCELLED, KICKED, CANCELLED_BY_HOST, CANCELLED_BY_SYSTEM',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `dispute_reason` varchar(500) DEFAULT NULL,
  `host_note` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_participant` (`campaign_id`,`user_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `participants_ibfk_1` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`id`) ON DELETE CASCADE,
  CONSTRAINT `participants_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
);

-- ==============================================================================
-- 4. 互動、通訊與評價 (Interactions & Logs)
-- ==============================================================================

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL COMMENT '對應合購單/房間',
    sender_id BIGINT NOT NULL COMMENT '發送者',
    message_type VARCHAR(50) DEFAULT 'TEXT' COMMENT '訊息類型 (TEXT, IMAGE, SYSTEM)',
    content TEXT NOT NULL COMMENT '訊息內容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_created_at (created_at) -- 加速 3 個月排程清理
);

-- ==========================================
-- 評價與防刷單紀錄表
-- ==========================================
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    campaign_id BIGINT NOT NULL COMMENT '關聯的合購單 ID',
    reviewer_id BIGINT NOT NULL COMMENT '給評價的人 (評分者)',
    reviewee_id BIGINT NOT NULL COMMENT '收評價的人 (被評者)',
    rating INT NOT NULL COMMENT '星等 (1~5)',
    comment VARCHAR(255) COMMENT '文字評價內容',
    is_score_counted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已產生信用加分 (防刷單機制)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '評價時間',

    -- 設定外鍵關聯 (如果合購單或會員被刪除，評價跟著刪除)
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE,

    -- 🌟 核心防護網加速器：針對 7 天防刷單查詢建立的複合索引
    INDEX idx_anti_fraud (reviewer_id, reviewee_id, is_score_counted, created_at),
    -- 防止同一人在同一單重複評價的約束
    UNIQUE INDEX uk_campaign_reviewer_reviewee (campaign_id, reviewer_id, reviewee_id)
);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收通知的會員',
    type VARCHAR(50) NOT NULL COMMENT '通知類型',
    reference_id BIGINT COMMENT '關聯的 Entity ID (如 campaign_id)',
    content VARCHAR(500) NOT NULL COMMENT '推播文字內容',
    is_read BOOLEAN DEFAULT FALSE COMMENT '是否已讀',
    expire_time DATETIME NULL COMMENT '跟隨關聯團單的過期時間',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_expire_time (expire_time) -- 加速 7 天排程清理
);

-- 信用分異動明細表 (信用存摺)
CREATE TABLE credit_score_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '被異動分數的會員',
    score_change INT NOT NULL COMMENT '異動分數 (+5, -10 等)',
    reason VARCHAR(100) NOT NULL COMMENT '異動原因 (例：團主主動取消已成團合購單)',
    campaign_id BIGINT COMMENT '關聯的合購單 ID (可選，方便追溯)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE SET NULL,
    INDEX idx_user_created (user_id, created_at) -- 加速前端查詢歷史明細
);

-- 使用者常用交付資料表
CREATE TABLE `user_delivery_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '使用者ID',
  `profile_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FACE_TO_FACE 面交, STORE_TO_STORE 店到店, HOME_DELIVERY 宅配, IBOX i郵箱',
  `label` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用者自訂名稱，例如：家裡、公司、常用面交點',
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '縣市',
  `district` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '行政區',
  `location_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '面交地點補充、超商門市名稱、i郵箱名稱',
  `location_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '門市代碼、i郵箱代碼等外部據點識別碼',
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交付服務提供者，例如 7_ELEVEN、FAMILY_MART、IBOX',
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完整宅配地址，主要供 HOME_DELIVERY 使用',
  `recipient_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收件人姓名',
  `recipient_phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收件人電話',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否為此交付類型的預設資料',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_delivery_profiles_user_type` (`user_id`,`profile_type`),
  KEY `idx_user_delivery_profiles_area` (`city`,`district`),
  KEY `idx_user_delivery_profiles_provider_location` (`provider`,`location_code`),
  CONSTRAINT `fk_user_delivery_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='使用者常用交付資料表';

-- 委託單主表
CREATE TABLE `purchase_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT
    COMMENT '託購單ID',

  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '建立時間',

  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    COMMENT '最後更新時間',

  `cancelled_at` datetime(6) DEFAULT NULL
    COMMENT '取消時間',

  `delivery_deadline_hours` int NOT NULL
    COMMENT '託購成立後，要求在幾小時內完成交付',

  `delivery_method` varchar(30) NOT NULL
    COMMENT '交付方式：FACE_TO_FACE 面交、STORE_TO_STORE 店到店、IBOX i郵箱、HOME_DELIVERY 宅配',

  `expired_at` datetime(6) DEFAULT NULL
    COMMENT '託購單實際過期時間',

  `fixed_reward_amount` int DEFAULT NULL
    COMMENT '固定酬金金額，reward_type=FIXED 時使用',

  `image_urls` json DEFAULT NULL
    COMMENT '商品圖片URL或檔名陣列',

  `item_description` text
    COMMENT '託購商品補充說明',

  `item_name` varchar(255) NOT NULL
    COMMENT '託購商品名稱',

  `last_reopen_reason` varchar(100) DEFAULT NULL
    COMMENT '最近一次重新開放託購單的原因',

  `min_credit_score` int NOT NULL DEFAULT '0'
    COMMENT '承接者最低信用分數限制',

  `reopen_count` int NOT NULL DEFAULT '0'
    COMMENT '託購單重新開放次數',

  `request_expire_time` datetime(6) NOT NULL
    COMMENT '託購需求截止時間，超過後不可再承接或報價',

  `reward_type` varchar(30) NOT NULL
    COMMENT '酬金模式：FIXED 固定酬金、QUOTE 承接者報價',

  `status` varchar(50) NOT NULL DEFAULT 'OPEN'
    COMMENT '託購狀態：OPEN 開放中、QUOTING 報價中、ORDERED 已成立、AWAITING_REOPEN 缺貨待決定、EXPIRED 已過期、CANCELLED 已取消',

  `requester_id` bigint NOT NULL
    COMMENT '託購發起會員ID',

  PRIMARY KEY (`id`),

  KEY `idx_purchase_requests_status_expire` (`status`,`request_expire_time`),

  KEY `idx_purchase_requests_requester_status` (`requester_id`,`status`),

  KEY `idx_purchase_requests_reward_status` (`reward_type`,`status`),

  KEY `idx_purchase_requests_delivery_status` (`delivery_method`,`status`),

  CONSTRAINT `fk_purchase_requests_requester`
    FOREIGN KEY (`requester_id`)
    REFERENCES `users` (`id`)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='託購需求主表';

-- 託購單交付資料快照
CREATE TABLE purchase_request_delivery_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL
        COMMENT '託購單 ID',
    source_profile_id BIGINT NULL
        COMMENT '來源 user_delivery_profiles.id，僅供追蹤，不作為實際交付資料來源',
    city VARCHAR(50) NULL
        COMMENT '縣市',
    district VARCHAR(50) NULL
        COMMENT '行政區',
    location_name VARCHAR(255) NULL
        COMMENT '面交地點補充、超商門市名稱、i郵箱名稱',
    location_code VARCHAR(100) NULL
        COMMENT '超商門市代碼、i郵箱代碼',
    provider VARCHAR(50) NULL
        COMMENT '7_ELEVEN、FAMILY_MART、IBOX 等',
    address VARCHAR(255) NULL
        COMMENT '宅配完整地址',
    recipient_name VARCHAR(100) NULL
        COMMENT '收件人姓名',
    recipient_phone VARCHAR(30) NULL
        COMMENT '收件人電話',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_request_delivery_details_request
        FOREIGN KEY (request_id)
        REFERENCES purchase_requests(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_purchase_request_delivery_details_profile
        FOREIGN KEY (source_profile_id)
        REFERENCES user_delivery_profiles(id)
        ON DELETE SET NULL,
    INDEX idx_pr_delivery_details_request (
        request_id
    ),
    INDEX idx_pr_delivery_details_area (
        city,
        district
    ),
    INDEX idx_pr_delivery_details_provider_location (
        provider,
        location_code
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='託購單交付資料快照';

-- 報價單表
CREATE TABLE purchase_quotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    request_id BIGINT NOT NULL COMMENT '委託單ID',
    quoter_id BIGINT NOT NULL COMMENT '報價者ID',

    quote_amount INT NOT NULL COMMENT '報價酬金',
    quote_note VARCHAR(500) NULL COMMENT '報價備註',

    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE, WITHDRAWN, SELECTED, NOT_SELECTED, EXPIRED, UNAVAILABLE_CANCELLED',

    selected_at DATETIME NULL COMMENT '被選中時間',
    withdrawn_at DATETIME NULL COMMENT '撤回時間',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_quotes_request
        FOREIGN KEY (request_id) REFERENCES purchase_requests(id) ON DELETE CASCADE,

    CONSTRAINT fk_purchase_quotes_quoter
        FOREIGN KEY (quoter_id) REFERENCES users(id),

    UNIQUE KEY uk_purchase_quotes_request_quoter (request_id, quoter_id),

    INDEX idx_purchase_quotes_request_status (request_id, status),
    INDEX idx_purchase_quotes_quoter_status (quoter_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='託購報價單表';

-- 委託訂單表
CREATE TABLE purchase_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    request_id BIGINT NOT NULL COMMENT '來源委託單ID',

    requester_id BIGINT NOT NULL COMMENT '委託人ID',
    runner_id BIGINT NOT NULL COMMENT '接單人ID',

    selected_quote_id BIGINT NULL COMMENT '報價制時，被選中的報價ID；固定酬金則為NULL',

    status VARCHAR(50) NOT NULL DEFAULT 'WAITING_CONFIRMATION'
        COMMENT 'WAITING_CONFIRMATION, CONFIRMED, IN_PROGRESS, SHIPPED, DELIVERED_PENDING_CONFIRM, COMPLETED, CANCELLED, ABNORMAL_PENDING_RESPONSE, PENALIZED_CANCELLED, DISPUTE_CLOSED_NO_PENALTY, UNAVAILABLE_CANCELLED',

    requester_confirmed_at DATETIME NULL COMMENT '委託人確認交易時間',
    runner_confirmed_at DATETIME NULL COMMENT '接單人確認交易時間',

    started_at DATETIME NULL COMMENT '接單人開始處理時間',

    logistics_provider VARCHAR(50) NULL COMMENT '物流公司，宅配/店到店使用',
    tracking_number VARCHAR(100) NULL COMMENT '物流單號，宅配/店到店使用',
    tracking_url VARCHAR(500) NULL COMMENT '物流查詢網址',

    shipped_at DATETIME NULL COMMENT '宅配/店到店寄出時間',
    delivered_at DATETIME NULL COMMENT '接單人標記交貨時間',
    completed_at DATETIME NULL COMMENT '訂單完成時間',
    cancelled_at DATETIME NULL COMMENT '訂單取消時間',

    cancel_reason VARCHAR(100) NULL COMMENT '取消原因',
    blame_user_id BIGINT NULL COMMENT '責任方ID，無責則為NULL',
    credit_penalty INT DEFAULT 0 COMMENT '本次扣除信用分',

    abnormal_reporter_id BIGINT NULL COMMENT '異常提出者ID',
    abnormal_reported_user_id BIGINT NULL COMMENT '被提出異常者ID',
    abnormal_type VARCHAR(100) NULL COMMENT '異常類型',
    abnormal_reported_at DATETIME NULL COMMENT '異常提出時間',
    abnormal_responded_at DATETIME NULL COMMENT '被提出異常方回應時間',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_orders_request
        FOREIGN KEY (request_id) REFERENCES purchase_requests(id),

    CONSTRAINT fk_purchase_orders_requester
        FOREIGN KEY (requester_id) REFERENCES users(id),

    CONSTRAINT fk_purchase_orders_runner
        FOREIGN KEY (runner_id) REFERENCES users(id),

    CONSTRAINT fk_purchase_orders_selected_quote
        FOREIGN KEY (selected_quote_id) REFERENCES purchase_quotes(id),

    CONSTRAINT fk_purchase_orders_blame_user
        FOREIGN KEY (blame_user_id) REFERENCES users(id),

    CONSTRAINT fk_purchase_orders_abnormal_reporter
        FOREIGN KEY (abnormal_reporter_id) REFERENCES users(id),

    CONSTRAINT fk_purchase_orders_abnormal_reported_user
        FOREIGN KEY (abnormal_reported_user_id) REFERENCES users(id),

    INDEX idx_purchase_orders_request (request_id),
    INDEX idx_purchase_orders_requester_status (requester_id, status),
    INDEX idx_purchase_orders_runner_status (runner_id, status),
    INDEX idx_purchase_orders_status_created (status, created_at),
    INDEX idx_purchase_orders_abnormal (status, abnormal_reported_at),
    INDEX idx_purchase_orders_delivery_time (status, shipped_at, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='託購訂單表';

-- 訂單事件紀錄表

CREATE TABLE purchase_order_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    order_id BIGINT NOT NULL COMMENT '訂單ID',
    actor_id BIGINT NULL COMMENT '操作人ID，系統事件可為NULL',

    event_type VARCHAR(80) NOT NULL COMMENT '事件類型',
    from_status VARCHAR(50) NULL COMMENT '變更前狀態',
    to_status VARCHAR(50) NULL COMMENT '變更後狀態',

    description VARCHAR(1000) NULL COMMENT '事件描述',
    metadata JSON NULL COMMENT '補充資料，例如扣分、原因、物流單號、缺貨說明',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_purchase_order_events_order
        FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,

    CONSTRAINT fk_purchase_order_events_actor
        FOREIGN KEY (actor_id) REFERENCES users(id),

    INDEX idx_purchase_order_events_order_time (order_id, created_at),
    INDEX idx_purchase_order_events_actor_time (actor_id, created_at),
    INDEX idx_purchase_order_events_type_time (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='託購訂單事件紀錄表';

CREATE TABLE purchase_chat_rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '託購訂單ID',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, READ_ONLY',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_chat_rooms_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    UNIQUE KEY uk_purchase_chat_rooms_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='託購聊天室';

CREATE TABLE purchase_chat_room_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NULL,
    last_read_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_chat_members_room FOREIGN KEY (room_id) REFERENCES purchase_chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_chat_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_purchase_chat_members_room_user (room_id, user_id),
    INDEX idx_purchase_chat_members_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='託購聊天室成員與已讀進度';

CREATE TABLE purchase_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT, IMAGE, SYSTEM',
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_chat_messages_room FOREIGN KEY (room_id) REFERENCES purchase_chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    INDEX idx_purchase_chat_messages_room_time (room_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='託購聊天室訊息';

CREATE TABLE purchase_order_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(255) NULL,
    is_score_counted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_reviews_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_purchase_reviews_order_reviewer_reviewee (order_id, reviewer_id, reviewee_id),
    INDEX idx_purchase_reviews_reviewee_time (reviewee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='託購訂單評價';

-- 問題主表
CREATE TABLE inquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
        COMMENT '問題 ID',

    user_id BIGINT NOT NULL
        COMMENT '發問者使用者 ID，關聯 users.id',

    type VARCHAR(50) NOT NULL
        COMMENT '問題類型：STOCK=庫存詢問、EXPERIENCE=商品心得或使用體驗',

    title VARCHAR(255) NOT NULL
        COMMENT '問題標題，例如：中和店有明太子醬嗎？',

    product_name VARCHAR(255) NULL
        COMMENT '商品名稱，例如：明太子醬、葡勝納',

    store_id INT NULL
        COMMENT '指定詢問門市 ID，關聯 stores.id；NULL 表示不限門市或未指定門市',

    content TEXT NULL
        COMMENT '問題詳細內容',

    image_urls VARCHAR(1024) NULL
        COMMENT '問題附圖檔名或圖片路徑，多張圖片以逗號分隔，最多 3 張',

    status VARCHAR(50) NOT NULL DEFAULT 'OPEN'
        COMMENT '問題狀態：OPEN=開放回答、EXPIRED=已過期',

    expire_time DATETIME NULL
        COMMENT '問題到期時間；STOCK 庫存問題固定為建立後 14 天，EXPERIENCE 心得問題可為 NULL',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '問題建立時間',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '問題最後更新時間',

    CONSTRAINT fk_inquiries_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_inquiries_store
        FOREIGN KEY (store_id)
        REFERENCES stores(id),

    INDEX idx_inquiry_user (user_id),

    INDEX idx_inquiry_type_status (type, status),

    INDEX idx_inquiry_store_status (store_id, status),

    INDEX idx_inquiry_expire_time (expire_time),

    INDEX idx_inquiry_created_at (created_at),

    INDEX idx_inquiry_product_name (product_name)

) COMMENT='商品詢問問題主表';

-- 問題回答表
CREATE TABLE inquiry_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
        COMMENT '回答 ID',

    inquiry_id BIGINT NOT NULL
        COMMENT '所屬問題 ID，關聯 inquiries.id',

    user_id BIGINT NOT NULL
        COMMENT '回答者使用者 ID，關聯 users.id',

    store_id INT NULL
        COMMENT '此回答所回報的門市 ID，關聯 stores.id；庫存問題可填寫，心得問題通常為 NULL',

    stock_status VARCHAR(50) NULL
        COMMENT '庫存狀態：IN_STOCK=有貨、LOW_STOCK=少量、OUT_OF_STOCK=無貨、UNKNOWN=不確定；心得問題可為 NULL',

    content TEXT
        COMMENT '回答內容',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '回答建立時間',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '回答最後更新時間',

    CONSTRAINT fk_inquiry_answers_inquiry
        FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_inquiry_answers_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT fk_inquiry_answers_store
        FOREIGN KEY (store_id)
        REFERENCES stores(id),

    INDEX idx_answer_inquiry (inquiry_id),

    INDEX idx_answer_user (user_id),

    INDEX idx_answer_store (store_id),

    INDEX idx_answer_created_at (created_at)

) COMMENT='商品詢問回答表';

-- 問題關注表
CREATE TABLE inquiry_follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
        COMMENT '問題關注紀錄 ID',

    inquiry_id BIGINT NOT NULL
        COMMENT '被關注的問題 ID，關聯 inquiries.id',

    user_id BIGINT NOT NULL
        COMMENT '關注問題的使用者 ID，關聯 users.id',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '開始關注時間',

    CONSTRAINT fk_inquiry_follows_inquiry
        FOREIGN KEY (inquiry_id)
        REFERENCES inquiries(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_inquiry_follows_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    UNIQUE KEY uk_inquiry_follow (inquiry_id, user_id),

    INDEX idx_inquiry_follow_user (user_id),

    INDEX idx_inquiry_follow_inquiry (inquiry_id)

) COMMENT='使用者關注商品詢問問題紀錄表';
