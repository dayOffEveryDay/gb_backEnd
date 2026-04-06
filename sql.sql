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
    has_costco_membership BOOLEAN DEFAULT FALSE COMMENT '是否有好市多會員卡 (限制開團權限)',
    
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
  PRIMARY KEY (`id`),
  KEY `host_id` (`host_id`),
  KEY `category_id` (`category_id`),
  KEY `blame_user_id` (`blame_user_id`),
  KEY `idx_store_status_expire` (`store_id`,`status`,`expire_time`),
  CONSTRAINT `campaigns_ibfk_1` FOREIGN KEY (`host_id`) REFERENCES `users` (`id`),
  CONSTRAINT `campaigns_ibfk_2` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `campaigns_ibfk_3` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),
  CONSTRAINT `campaigns_ibfk_4` FOREIGN KEY (`blame_user_id`) REFERENCES `users` (`id`)
) ;

CREATE TABLE `participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `campaign_id` bigint NOT NULL COMMENT '所屬合購單',
  `user_id` bigint NOT NULL COMMENT '參與者',
  `quantity` int NOT NULL COMMENT '認購數量',
  `status` varchar(50) DEFAULT 'JOINED' COMMENT '狀態 (JOINED, CANCELLED, NO_SHOW)',
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