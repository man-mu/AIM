-- friend-service 集成测试专用 schema 脚本
-- 由 Testcontainers PostgreSQLContainer.withInitScript 在容器启动时执行
-- 内容从 docs/sql/auto/schemas/aim-schema.sql 第 46-101 行提取，仅保留 friend schema 4 张表

CREATE SCHEMA IF NOT EXISTS friend;

-- 好友关系表
CREATE TABLE IF NOT EXISTS friend.friends (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    friend_id   BIGINT       NOT NULL,
    group_id    BIGINT       NOT NULL DEFAULT 0,
    remark      VARCHAR(64)  NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_friends_pair ON friend.friends(user_id, friend_id);
CREATE INDEX IF NOT EXISTS idx_friends_user ON friend.friends(user_id);
CREATE INDEX IF NOT EXISTS idx_friends_friend ON friend.friends(friend_id);

-- 好友分组表
CREATE TABLE IF NOT EXISTS friend.friend_groups (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL DEFAULT '',
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_friend_groups_user ON friend.friend_groups(user_id);

-- 好友申请表
CREATE TABLE IF NOT EXISTS friend.friend_requests (
    id          BIGINT PRIMARY KEY,
    from_user_id BIGINT       NOT NULL,
    to_user_id   BIGINT       NOT NULL,
    message     VARCHAR(256) NOT NULL DEFAULT '',
    status      SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_friend_requests_from ON friend.friend_requests(from_user_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_to ON friend.friend_requests(to_user_id);
CREATE INDEX IF NOT EXISTS idx_friend_requests_status ON friend.friend_requests(status);
-- 待处理申请唯一约束：同一对 (from,to) 同时只能有一条 pending，并发防双申请
CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_requests_pending_pair
    ON friend.friend_requests(from_user_id, to_user_id) WHERE status = 1;

-- 拉黑表
CREATE TABLE IF NOT EXISTS friend.user_blocks (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    blocked_user_id BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_blocks_pair ON friend.user_blocks(user_id, blocked_user_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_user ON friend.user_blocks(user_id);
