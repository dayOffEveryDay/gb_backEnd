-- 正式環境一次性交付資料遷移腳本。
-- 執行前必須完整備份資料庫，並停止所有會寫入託購資料的應用程式。
-- MySQL DDL 會隱含提交；本腳本不可依賴 ROLLBACK，也不可直接重複執行。

-- 建立統一交付快照表；若已由新版 Hibernate 建立則沿用現有資料表。
CREATE TABLE IF NOT EXISTS purchase_request_delivery_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL COMMENT '託購單 ID',
    source_profile_id BIGINT NULL
        COMMENT '來源 user_delivery_profiles.id，僅供追蹤，不作為實際交付資料來源',
    city VARCHAR(50) NULL COMMENT '縣市',
    district VARCHAR(50) NULL COMMENT '行政區',
    location_name VARCHAR(255) NULL COMMENT '面交地點補充、超商門市名稱、i郵箱名稱',
    location_code VARCHAR(100) NULL COMMENT '超商門市代碼、i郵箱代碼',
    provider VARCHAR(50) NULL COMMENT '7_ELEVEN、FAMILY_MART、IBOX 等',
    address VARCHAR(255) NULL COMMENT '宅配完整地址',
    recipient_name VARCHAR(100) NULL COMMENT '收件人姓名',
    recipient_phone VARCHAR(30) NULL COMMENT '收件人電話',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_request_delivery_details_request
        FOREIGN KEY (request_id) REFERENCES purchase_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_request_delivery_details_profile
        FOREIGN KEY (source_profile_id) REFERENCES user_delivery_profiles(id) ON DELETE SET NULL,
    INDEX idx_pr_delivery_details_request (request_id),
    INDEX idx_pr_delivery_details_area (city, district),
    INDEX idx_pr_delivery_details_provider_location (provider, location_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='託購單交付資料快照';

-- 搬移舊面交快照。NOT EXISTS 可避免新表已有部分資料時產生完全相同的重複資料。
INSERT INTO purchase_request_delivery_details (
    request_id, source_profile_id, city, district, location_name,
    location_code, provider, address, recipient_name, recipient_phone, created_at
)
SELECT
    old_range.request_id,
    old_range.source_profile_id,
    old_range.city,
    old_range.district,
    LEFT(COALESCE(NULLIF(old_range.display_text, ''), old_range.location_name), 255),
    LEFT(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.locationCode')), 'null'), 100),
    LEFT(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.provider')), 'null'), 50),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.address')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.addressDetail')), 'null')
    ), 255),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.recipientName')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.receiverName')), 'null')
    ), 100),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.recipientPhone')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(old_range.detail, '$.receiverPhone')), 'null')
    ), 30),
    COALESCE(old_range.created_at, CURRENT_TIMESTAMP)
FROM purchase_request_meetup_ranges old_range
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase_request_delivery_details current_detail
    WHERE current_detail.request_id = old_range.request_id
      AND current_detail.source_profile_id <=> old_range.source_profile_id
      AND current_detail.city <=> old_range.city
      AND current_detail.district <=> old_range.district
      AND current_detail.location_name <=> LEFT(
          COALESCE(NULLIF(old_range.display_text, ''), old_range.location_name), 255
      )
);

-- 對沒有舊面交快照的委託，從 purchase_requests 舊欄位建立一筆快照。
-- 同時相容 receiver/recipient 與 store/location 等曾使用過的 JSON key。
INSERT INTO purchase_request_delivery_details (
    request_id, source_profile_id, city, district, location_name,
    location_code, provider, address, recipient_name, recipient_phone, created_at
)
SELECT
    pr.id,
    NULL,
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.city')), 'null'),
        pr.requester_city
    ), 50),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.district')), 'null'),
        pr.requester_district
    ), 50),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.locationName')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.storeName')), 'null'),
        NULLIF(pr.delivery_public_summary, '')
    ), 255),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.locationCode')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.storeCode')), 'null')
    ), 100),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.provider')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.storeProvider')), 'null')
    ), 50),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.address')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.addressDetail')), 'null')
    ), 255),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.recipientName')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.receiverName')), 'null')
    ), 100),
    LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.recipientPhone')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.delivery_private_detail, '$.receiverPhone')), 'null')
    ), 30),
    COALESCE(pr.created_at, CURRENT_TIMESTAMP)
FROM purchase_requests pr
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase_request_delivery_details current_detail
    WHERE current_detail.request_id = pr.id
);

-- 先將常用交付資料的 JSON 內容展開至新欄位，再移除舊欄位。
UPDATE user_delivery_profiles
SET
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

ALTER TABLE user_delivery_profiles
    MODIFY profile_type VARCHAR(30) NOT NULL
        COMMENT 'FACE_TO_FACE 面交, STORE_TO_STORE 店到店, HOME_DELIVERY 宅配, IBOX i郵箱',
    MODIFY label VARCHAR(100) NULL
        COMMENT '使用者自訂名稱，例如：家裡、公司、常用面交點',
    MODIFY city VARCHAR(50) NULL COMMENT '縣市',
    MODIFY district VARCHAR(50) NULL COMMENT '行政區',
    MODIFY location_name VARCHAR(255) NULL
        COMMENT '面交地點補充、超商門市名稱、i郵箱名稱',
    ADD COLUMN location_code VARCHAR(100) NULL
        COMMENT '門市代碼、i郵箱代碼等外部據點識別碼' AFTER location_name,
    ADD COLUMN provider VARCHAR(50) NULL
        COMMENT '交付服務提供者，例如 7_ELEVEN、FAMILY_MART、IBOX' AFTER location_code,
    ADD COLUMN address VARCHAR(255) NULL
        COMMENT '完整宅配地址，主要供 HOME_DELIVERY 使用' AFTER provider,
    ADD COLUMN recipient_name VARCHAR(100) NULL COMMENT '收件人姓名' AFTER address,
    ADD COLUMN recipient_phone VARCHAR(30) NULL COMMENT '收件人電話' AFTER recipient_name,
    MODIFY is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否為此交付類型的預設資料',
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE user_delivery_profiles
SET
    location_name = LEFT(COALESCE(NULLIF(display_text, ''), location_name), 255),
    location_code = LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.locationCode')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.storeCode')), 'null')
    ), 100),
    provider = LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.provider')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.storeProvider')), 'null')
    ), 50),
    address = LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.address')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.addressDetail')), 'null')
    ), 255),
    recipient_name = LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.recipientName')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.receiverName')), 'null')
    ), 100),
    recipient_phone = LEFT(COALESCE(
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.recipientPhone')), 'null'),
        NULLIF(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.receiverPhone')), 'null')
    ), 30);

-- 在刪除任何舊欄位前強制驗證搬移完整性；未通過時會中止腳本並保留舊資料。
DROP PROCEDURE IF EXISTS validate_purchase_delivery_migration;
DELIMITER $$
CREATE PROCEDURE validate_purchase_delivery_migration()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM purchase_requests pr
        WHERE NOT EXISTS (
            SELECT 1
            FROM purchase_request_delivery_details detail
            WHERE detail.request_id = pr.id
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '交付資料搬移失敗：仍有委託缺少 delivery detail';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM purchase_request_meetup_ranges old_range
        WHERE NOT EXISTS (
            SELECT 1
            FROM purchase_request_delivery_details detail
            WHERE detail.request_id = old_range.request_id
              AND detail.source_profile_id <=> old_range.source_profile_id
              AND detail.city <=> old_range.city
              AND detail.district <=> old_range.district
              AND detail.location_name <=> LEFT(
                  COALESCE(NULLIF(old_range.display_text, ''), old_range.location_name), 255
              )
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '交付資料搬移失敗：仍有舊面交範圍未搬移';
    END IF;
END$$
DELIMITER ;
CALL validate_purchase_delivery_migration();
DROP PROCEDURE validate_purchase_delivery_migration;

ALTER TABLE user_delivery_profiles
    DROP INDEX idx_user_delivery_profiles_city_type,
    DROP COLUMN location_type,
    DROP COLUMN range_start,
    DROP COLUMN range_end,
    DROP COLUMN display_text,
    DROP COLUMN detail,
    ADD INDEX idx_user_delivery_profiles_area (city, district),
    ADD INDEX idx_user_delivery_profiles_provider_location (provider, location_code);

-- 確保改成 NOT NULL 前沒有舊的 NULL 資料。
UPDATE purchase_requests
SET
    min_credit_score = COALESCE(min_credit_score, 0),
    reopen_count = COALESCE(reopen_count, 0),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

-- 新快照已保存公開與私密交付資料，移除 purchase_requests 上的重複舊欄位與城市索引。
ALTER TABLE purchase_requests
    DROP INDEX idx_purchase_requests_city_status,
    DROP COLUMN requester_city,
    DROP COLUMN requester_district,
    DROP COLUMN delivery_public_summary,
    DROP COLUMN delivery_private_detail,
    MODIFY delivery_method VARCHAR(30) NOT NULL
        COMMENT '交付方式：FACE_TO_FACE 面交、STORE_TO_STORE 店到店、IBOX i郵箱、HOME_DELIVERY 宅配',
    MODIFY request_expire_time DATETIME(6) NOT NULL
        COMMENT '託購需求截止時間，超過後不可再承接或報價',
    MODIFY min_credit_score INT NOT NULL DEFAULT 0 COMMENT '承接者最低信用分數限制',
    MODIFY status VARCHAR(50) NOT NULL DEFAULT 'OPEN'
        COMMENT '託購狀態：OPEN 開放中、QUOTING 報價中、ORDERED 已成立、AWAITING_REOPEN 缺貨待決定、EXPIRED 已過期、CANCELLED 已取消',
    MODIFY reopen_count INT NOT NULL DEFAULT 0 COMMENT '託購單重新開放次數',
    MODIFY cancelled_at DATETIME(6) NULL COMMENT '取消時間',
    MODIFY expired_at DATETIME(6) NULL COMMENT '託購單實際過期時間',
    MODIFY created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    MODIFY updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最後更新時間',
    COMMENT='託購需求主表';

-- 舊面交資料已完成搬移，最後才移除舊快照表。
DROP TABLE purchase_request_meetup_ranges;

-- 部署後核對：missing_delivery_detail 必須為 0，並抽查各交付方式的實際內容。
SELECT COUNT(*) AS purchase_request_count FROM purchase_requests;
SELECT COUNT(DISTINCT request_id) AS request_with_delivery_detail_count
FROM purchase_request_delivery_details;
SELECT COUNT(*) AS missing_delivery_detail
FROM purchase_requests pr
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase_request_delivery_details detail
    WHERE detail.request_id = pr.id
);
SELECT
    pr.delivery_method,
    COUNT(*) AS detail_count
FROM purchase_requests pr
JOIN purchase_request_delivery_details detail ON detail.request_id = pr.id
GROUP BY pr.delivery_method;
