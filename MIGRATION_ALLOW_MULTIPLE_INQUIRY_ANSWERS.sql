-- 若曾套用回答唯一限制，執行此 migration 後可讓同一使用者重複回答同一問題。
ALTER TABLE inquiry_answers
    DROP INDEX uk_inquiry_answer_user;
