-- AIM 业务数据库 Schema
-- 用法：手动导入 PG 的 aim 数据库
--       psql -h localhost -U postgres -d aim -f aim-schema.sql
--
-- PG 原生用户建议先建库：CREATE DATABASE aim;
-- 注意："user" 是 PG 保留字，schema 名必须加双引号

-- ============================================================
-- 1. user schema — user-service
-- 用户注册/登录/资料/设备管理
-- ============================================================
CREATE SCHEMA IF NOT EXISTS "user";

-- 用户表
CREATE TABLE IF NOT EXISTS "user".users (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL DEFAULT '',
    password_hash   VARCHAR(256) NOT NULL DEFAULT '',
    phone           VARCHAR(20)  NOT NULL DEFAULT '',
    email           VARCHAR(128) NOT NULL DEFAULT '',
    avatar          VARCHAR(512) NOT NULL DEFAULT '',
    gender          SMALLINT     NOT NULL DEFAULT 0,
    bio             TEXT         NOT NULL DEFAULT '',
    birthday        BIGINT       NOT NULL DEFAULT 0,
    balance         NUMERIC(12,6) NOT NULL DEFAULT 0,
    settings        JSONB        NOT NULL DEFAULT '{}'::JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON "user".users(username);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone   ON "user".users(phone) WHERE phone <> '';
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email   ON "user".users(email) WHERE email <> '';

-- 用户设备表
CREATE TABLE IF NOT EXISTS "user".user_devices (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    device_id       VARCHAR(128) NOT NULL DEFAULT '',
    platform        VARCHAR(32)  NOT NULL DEFAULT 'web',
    push_token      VARCHAR(512) NOT NULL DEFAULT '',
    ip              VARCHAR(64)  NOT NULL DEFAULT '',
    location        VARCHAR(128) NOT NULL DEFAULT '',
    last_active_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_devices_pair ON "user".user_devices(user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_user ON "user".user_devices(user_id);

-- ============================================================
-- 2. friend schema — friend-service
-- 好友关系/分组/申请/拉黑
-- ============================================================
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

-- 拉黑表
CREATE TABLE IF NOT EXISTS friend.user_blocks (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    blocked_user_id BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_blocks_pair ON friend.user_blocks(user_id, blocked_user_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_user ON friend.user_blocks(user_id);

-- ============================================================
-- 3. conv schema — conv-service
-- 会话群组/成员/已读位置/设置/Bot绑定
-- ============================================================
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

-- ============================================================
-- 4. msg schema — message-service（消息引擎核心）
-- 消息存储/seq/outbox/inbox/广播
-- ============================================================
CREATE SCHEMA IF NOT EXISTS msg;

-- 消息表
CREATE TABLE IF NOT EXISTS msg.messages (
    id              BIGINT PRIMARY KEY,
    conv_id         BIGINT       NOT NULL,
    sender_id       BIGINT       NOT NULL,
    sender_type     VARCHAR(16)  NOT NULL DEFAULT 'user',
    client_msg_id   VARCHAR(128) NOT NULL DEFAULT '',
    seq             BIGINT       NOT NULL DEFAULT 0,
    msg_type        INT          NOT NULL,
    content         JSONB        NOT NULL DEFAULT '{}'::JSONB,
    reply_to_msg_id BIGINT       NOT NULL DEFAULT 0,
    status          SMALLINT     NOT NULL DEFAULT 1,
    edit_history    JSONB        NOT NULL DEFAULT '[]'::JSONB,
    edit_count      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_messages_conv_seq ON msg.messages(conv_id, seq);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON msg.messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_created ON msg.messages(created_at);

-- 会话 seq 计数器表（核心：UPSERT RETURNING 原子递增）
CREATE TABLE IF NOT EXISTS msg.sequences (
    conv_id     BIGINT PRIMARY KEY,
    current_seq BIGINT NOT NULL DEFAULT 0
);

-- Outbox 事件表（核心：Transactional Outbox）
CREATE TABLE IF NOT EXISTS msg.outbox_events (
    id              BIGINT PRIMARY KEY,
    topic           VARCHAR(64)  NOT NULL,
    key             VARCHAR(128) NOT NULL DEFAULT '',
    payload         JSONB        NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 0,
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 10,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    dispatched_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending
    ON msg.outbox_events(status, next_retry_at, created_at)
    WHERE status = 0;

-- 用户收件箱表（核心：写扩散）
CREATE TABLE IF NOT EXISTS msg.user_inbox (
    user_id       BIGINT       NOT NULL,
    conv_id       BIGINT       NOT NULL,
    message_id    BIGINT       NOT NULL DEFAULT 0,
    seq           BIGINT       NOT NULL,
    last_read_seq BIGINT       NOT NULL DEFAULT 0,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, conv_id, seq)
);
CREATE INDEX IF NOT EXISTS idx_user_inbox_conv ON msg.user_inbox(user_id, conv_id);
CREATE INDEX IF NOT EXISTS idx_user_inbox_covering
    ON msg.user_inbox(user_id, conv_id, seq DESC)
    INCLUDE (message_id, last_read_seq, created_at)
    WHERE is_deleted = FALSE;

-- 广播消息表
CREATE TABLE IF NOT EXISTS msg.broadcasts (
    id              BIGINT PRIMARY KEY,
    sender_id       BIGINT       NOT NULL DEFAULT 0,
    content         JSONB        NOT NULL DEFAULT '{}'::JSONB,
    scope           VARCHAR(32)  NOT NULL DEFAULT 'all',
    scope_target_id BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 5. file schema — file-service
-- 文件元数据/MinIO 对象管理
-- ============================================================
CREATE SCHEMA IF NOT EXISTS file;

-- 文件表
CREATE TABLE IF NOT EXISTS file.files (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(512) NOT NULL DEFAULT '',
    key         VARCHAR(512) NOT NULL DEFAULT '',
    size        BIGINT       NOT NULL DEFAULT 0,
    mime_type   VARCHAR(256) NOT NULL DEFAULT '',
    ext         VARCHAR(32)  NOT NULL DEFAULT '',
    width       INT          NOT NULL DEFAULT 0,
    height      INT          NOT NULL DEFAULT 0,
    duration    INT          NOT NULL DEFAULT 0,
    md5         VARCHAR(64)  NOT NULL DEFAULT '',
    purpose     SMALLINT     NOT NULL DEFAULT 0,
    access      SMALLINT     NOT NULL DEFAULT 0,
    uploader_id BIGINT       NOT NULL DEFAULT 0,
    bucket      VARCHAR(128) NOT NULL DEFAULT 'aim',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_files_uploader ON file.files(uploader_id);
CREATE INDEX IF NOT EXISTS idx_files_key ON file.files(key);

-- ============================================================
-- 6. notify schema — signaling-service
-- 通知/设备推送 Token
-- ============================================================
CREATE SCHEMA IF NOT EXISTS notify;

-- 通知表
CREATE TABLE IF NOT EXISTS notify.notifications (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT       NOT NULL DEFAULT 0,
    type            INT          NOT NULL DEFAULT 0,
    title           TEXT         NOT NULL DEFAULT '',
    content         TEXT         NOT NULL DEFAULT '',
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    reference_id    VARCHAR(128) NOT NULL DEFAULT '',
    created_at      BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notify.notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notify.notifications(user_id, is_read);

-- 设备推送 Token 表
CREATE TABLE IF NOT EXISTS notify.device_tokens (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT       NOT NULL DEFAULT 0,
    device_id   VARCHAR(128) NOT NULL DEFAULT '',
    platform    VARCHAR(16)  NOT NULL DEFAULT 'web',
    token       VARCHAR(512) NOT NULL DEFAULT '',
    provider    VARCHAR(8)   NOT NULL DEFAULT 'fcm',
    created_at  BIGINT       NOT NULL DEFAULT 0,
    updated_at  BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_device_token_pair ON notify.device_tokens(user_id, device_id);
