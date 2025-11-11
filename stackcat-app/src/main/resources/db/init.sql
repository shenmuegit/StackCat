-- StackCat PostgreSQL 数据库初始化脚本
-- 包含数据库创建和表结构定义

-- 创建数据库（如果不存在）
-- 注意：需要在 PostgreSQL 中手动执行，或使用 postgres 用户执行
-- CREATE DATABASE stackcat;

-- 连接到数据库后执行以下脚本
-- \c stackcat;

-- ============================================
-- 表结构定义
-- ============================================

-- 请求追踪表
CREATE TABLE IF NOT EXISTS request_trace (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(255),
    thread_id BIGINT,
    thread_name VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    http_headers TEXT,
    created_at TIMESTAMP
);

-- 方法调用表
CREATE TABLE IF NOT EXISTS method_call (
    id BIGSERIAL PRIMARY KEY,
    request_trace_id BIGINT,
    parent_call_id BIGINT,
    method_name VARCHAR(500),
    class_name VARCHAR(500),
    package_name VARCHAR(500),
    call_time TIMESTAMP,
    sequence INTEGER,
    depth INTEGER,
    query TEXT,
    CONSTRAINT fk_method_call_request_trace 
        FOREIGN KEY (request_trace_id) 
        REFERENCES request_trace(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_method_call_parent 
        FOREIGN KEY (parent_call_id) 
        REFERENCES method_call(id) 
        ON DELETE CASCADE
);

-- 方法统计表
CREATE TABLE IF NOT EXISTS method_statistics (
    id BIGSERIAL PRIMARY KEY,
    method_name VARCHAR(500),
    class_name VARCHAR(500),
    package_name VARCHAR(500),
    call_count BIGINT,
    thread_count BIGINT,
    last_call_time TIMESTAMP,
    statistics_date DATE
);

-- ============================================
-- 索引定义
-- ============================================

-- 请求追踪表索引
CREATE INDEX IF NOT EXISTS idx_request_trace_request_id ON request_trace(request_id);
CREATE INDEX IF NOT EXISTS idx_request_trace_start_time ON request_trace(start_time);
CREATE INDEX IF NOT EXISTS idx_request_trace_end_time ON request_trace(end_time);

-- 方法调用表索引
CREATE INDEX IF NOT EXISTS idx_method_call_request_trace_id ON method_call(request_trace_id);
CREATE INDEX IF NOT EXISTS idx_method_call_parent_call_id ON method_call(parent_call_id);
CREATE INDEX IF NOT EXISTS idx_method_call_sequence ON method_call(request_trace_id, sequence);
CREATE INDEX IF NOT EXISTS idx_method_call_depth ON method_call(request_trace_id, depth);
CREATE INDEX IF NOT EXISTS idx_method_call_class_name ON method_call(class_name);
CREATE INDEX IF NOT EXISTS idx_method_call_method_name ON method_call(method_name);

-- 方法统计表索引
CREATE INDEX IF NOT EXISTS idx_method_statistics_method_name ON method_statistics(method_name);
CREATE INDEX IF NOT EXISTS idx_method_statistics_class_name ON method_statistics(class_name);
CREATE INDEX IF NOT EXISTS idx_method_statistics_statistics_date ON method_statistics(statistics_date);

-- ============================================
-- 表注释
-- ============================================

COMMENT ON TABLE request_trace IS '请求追踪表，记录每个请求的基本信息';
COMMENT ON COLUMN request_trace.id IS '主键ID';
COMMENT ON COLUMN request_trace.request_id IS '请求ID（从HTTP头requestid获取）';
COMMENT ON COLUMN request_trace.thread_id IS '线程ID';
COMMENT ON COLUMN request_trace.thread_name IS '线程名称';
COMMENT ON COLUMN request_trace.start_time IS '请求开始时间';
COMMENT ON COLUMN request_trace.end_time IS '请求结束时间';
COMMENT ON COLUMN request_trace.http_headers IS 'HTTP请求头（JSON格式）';
COMMENT ON COLUMN request_trace.created_at IS '记录创建时间';

COMMENT ON TABLE method_call IS '方法调用表，记录每个请求中的所有方法调用';
COMMENT ON COLUMN method_call.id IS '主键ID';
COMMENT ON COLUMN method_call.request_trace_id IS '关联的请求追踪ID';
COMMENT ON COLUMN method_call.parent_call_id IS '父方法调用ID（用于构建调用树）';
COMMENT ON COLUMN method_call.method_name IS '方法名';
COMMENT ON COLUMN method_call.class_name IS '类名';
COMMENT ON COLUMN method_call.package_name IS '包名';
COMMENT ON COLUMN method_call.call_time IS '方法调用时间';
COMMENT ON COLUMN method_call.sequence IS '调用序列号（用于排序）';
COMMENT ON COLUMN method_call.depth IS '调用深度（用于构建调用树）';
COMMENT ON COLUMN method_call.query IS 'SQL/JPQL查询语句（仅Repository方法）';

COMMENT ON TABLE method_statistics IS '方法统计表，记录方法的调用统计信息';
COMMENT ON COLUMN method_statistics.id IS '主键ID';
COMMENT ON COLUMN method_statistics.method_name IS '方法名';
COMMENT ON COLUMN method_statistics.class_name IS '类名';
COMMENT ON COLUMN method_statistics.package_name IS '包名';
COMMENT ON COLUMN method_statistics.call_count IS '调用次数';
COMMENT ON COLUMN method_statistics.thread_count IS '线程数量';
COMMENT ON COLUMN method_statistics.last_call_time IS '最后调用时间';
COMMENT ON COLUMN method_statistics.statistics_date IS '统计日期';

