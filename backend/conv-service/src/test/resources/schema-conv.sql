-- conv-service 集成测试专用 schema 脚本
-- 由 Testcontainers PostgreSQLContainer.withInitScript 在容器启动时执行
-- 内容从 docs/sql/auto/schemas/aim-schema.sql 第 103-175 行提取，仅保留 conv schema 5 张表

CREATE SCHEMA IF NOT EXISTS conv;

-- 会话表
CREATE TABLE IF NOT EXISTS conv.conversations (
    id                    BIGINT PRIMARY KEY,
    type                  INT          NOT NULL DEFAULT 1,
    name                  TEXT         NOT NULL DEFAULT '',
    avatar                TEXT         NOT NULL DEFAULT '',
    owner_id              BIGINT       NOT NULL DEFAULT 0,
    announcement          TEXT         NOT NULL DEFAULT '',
    is_muted_all          BOOLEAN      NOT NULL DEFAULT FALSE,
    background            TEXT         NOT NULL DEFAULT '',
    max_seq               BIGINT       NOT NULL DEFAULT 0,
    last_message_id       BIGINT       NOT NULL DEFAULT 0,
    last_message_preview  TEXT         NOT NULL DEFAULT '',
    member_count          INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 会话成员表
CREATE TABLE IF NOT EXISTS conv.conv_members (
    id          BIGINT PRIMARY KEY,
    conv_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    member_type VARCHAR(16)  NOT NULL DEFAULT 'user',
    bot_id      BIGINT       NOT NULL DEFAULT 0,
    role        INT          NOT NULL DEFAULT 0,
    alias       TEXT         NOT NULL DEFAULT '',
    is_muted    BOOLEAN      NOT NULL DEFAULT FALSE,
    mute_until  BIGINT       NOT NULL DEFAULT 0,
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_conv_members_pair ON conv.conv_members(conv_id, user_id);
CREATE INDEX IF NOT EXISTS idx_conv_members_conv ON conv.conv_members(conv_id);
CREATE INDEX IF NOT EXISTS idx_conv_members_user ON conv.conv_members(user_id);

-- 已读位置表
CREATE TABLE IF NOT EXISTS conv.conv_read_seqs (
    id              BIGINT PRIMARY KEY,
    conv_id         BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    last_read_seq   BIGINT       NOT NULL DEFAULT 0,
    read_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_conv_read_seqs_pair ON conv.conv_read_seqs(conv_id, user_id);

-- 用户会话设置表
CREATE TABLE IF NOT EXISTS conv.conv_settings (
    id        BIGINT PRIMARY KEY,
    conv_id   BIGINT  NOT NULL,
    user_id   BIGINT  NOT NULL,
    is_muted  BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_conv_settings_pair ON conv.conv_settings(conv_id, user_id);

-- 会话 Bot 绑定表
CREATE TABLE IF NOT EXISTS conv.conv_bots (
    id                  BIGINT PRIMARY KEY,
    conv_id             BIGINT       NOT NULL,
    bot_id              BIGINT       NOT NULL,
    added_by            BIGINT       NOT NULL DEFAULT 0,
    response_triggers   JSONB        NOT NULL DEFAULT '{}'::JSONB,
    bot_settings        TEXT         NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_conv_bots_pair ON conv.conv_bots(conv_id, bot_id);
CREATE INDEX IF NOT EXISTS idx_conv_bots_conv ON conv.conv_bots(conv_id);
CREATE INDEX IF NOT EXISTS idx_conv_bots_bot ON conv.conv_bots(bot_id);
