-- 移除未經驗證且不再作為開團條件的會員卡自填欄位。
ALTER TABLE users
    DROP COLUMN has_costco_membership;
