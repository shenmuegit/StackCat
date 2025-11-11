-- 为 method_call 表添加 query 字段
-- 用于存储 Repository 方法执行的 SQL/JPQL 查询语句

ALTER TABLE method_call 
ADD COLUMN IF NOT EXISTS query TEXT;

COMMENT ON COLUMN method_call.query IS 'SQL/JPQL查询语句（仅Repository方法）';

