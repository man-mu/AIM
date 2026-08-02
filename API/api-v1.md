# AIM API v1 接口文档（当前契约）

> **本文档是前后端接口的唯一契约（Single Source of Truth），覆盖 IM 基础功能全部八域。**
>
> - 覆盖全部域：Auth / User / Friend / Conversation / Message / File / Notification / WebSocket
> - 每个接口标注实现状态：✅ 已实现（后端已落地并测试）/ ⏳ 待实现（后端未开发，契约以前端 mock 为准）
> - **维护铁律**：后端实现偏离本文档必须同步更新本文档（实现与文档同 commit）；前端 mock 变更必须回写本文档
>
> 契约基线时间：2026-07-31（依据：后端实际实现 + 前端 mock handlers 全量提取核对；2026-08-01 合并原 `api-v1-implemented.md` 为唯一文档）

---

## 1. 通用约定

### 1.1 网络与入口

| 项 | 约定 |
|---|---|
| 网关 Base URL | `http://localhost:9080/api/v1`（`gateway-service`，端口 9080） |
| WS 地址 | `ws://localhost:8081/ws`（`ws-gateway-service`，连接参数 `?token=<accessToken>&device_id=<deviceId>`） |
| 网关路由 | `/api/v1/auth/**`、`/api/v1/users/**` → user-service；`/api/v1/convs/**` → conv-service；`/api/v1/files/**` → file-service；`/api/v1/friends/**` → friend-service（✅ 已实现）；message/notification 路由**待实现** |
| 鉴权白名单 | `POST /auth/login`、`POST /auth/register`、`POST /auth/refresh`、`/public/**`（前缀匹配） |

### 1.2 统一响应壳 Result\<T\>

```json
{ "code": 0, "message": "success", "data": { } }
```

- `code === 0` 成功；非 0 失败时 `data` 为 `null`
- **业务错误**：HTTP 状态码仍为 **200**，仅 body 中 code 非 0
- **鉴权失败**（网关拦截）：HTTP **401** + `{code:401, message:<失败原因>, data:null}`（message 为动态文案，非固定错误码）
- 401 / 10005 / 10006 触发前端静默刷新（access 剩余 <30s 主动刷新；刷新失败清会话）

### 1.3 鉴权

- 请求头 `Authorization: Bearer <accessToken>`
- 网关校验链：签名 → 过期（leeway=0）→ `type=access` → jti 不在黑名单 → 未在改密后吊销（`pwd_changed:{userId}` 与 iat 比对）
- 网关注入 `X-User-Id` header，服务端一律以它为身份来源（**请求体/query 里的 userId 类字段不生效**）
- refresh token：`type=refresh`，有效期 30 天（默认）；**每次 refresh 轮换**（旧 refreshToken 立即失效）

### 1.4 分页

- 参数：`pageNum`（默认 1，最小 1）、`pageSize`（服务端钳制：`<=0 → 20`，`>100 → 100`；各接口默认值见接口表）
- 列表响应统一 `{ list: T[], total: number }` 或按接口表

### 1.5 时间与大整数

- 时间戳一律 **epoch 毫秒**；唯一例外 `muteUntil` / `muteUntilSec` 为 **epoch 秒**（0 = 未禁言或永久禁言）
- 所有 id/seq 为 Java long，前端经 json-bigint 承接为**十进制字符串**（wire 类型 `Int64 = string | number`）
- `balance` 为 BigDecimal，序列化为 JSON number（非 string）

### 1.6 批量接口

- `POST /users/batch`、`POST /files/batch` 请求体**直接是 JSON 数组**（非对象包裹）
- 数量上限 500（超出 → code 400）；空数组返回空结果不报错

### 1.7 布尔字段

- JSON 键名**无 `is-` 前缀**：`mutedAll` / `muted` / `pinned` / `member`（Lombok getter 推导）

### 1.8 幂等

- 单聊创建：重复创建返回既有会话（成功，不报错）
- 消息发送：`clientMsgId`（`c-<uuid>`）按 `(conversationId, clientMsgId)` 幂等，重复 → 40004

---

## 2. Auth 域（✅ 已实现）

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 注册 | `POST /auth/register`（白名单） | `username` 必填 3-64 且 `^[A-Za-z0-9_]+$`；`password` 必填 6-32 且含字母+数字；`phone`? ≤20；`email`? ≤128；`deviceId`? ≤128；`platform`? ≤32 | `userId`、`tokens{accessToken, refreshToken, accessExpire, refreshExpire}`、`user`（本人完整视图） | 400；10002 用户名已存在；10003 手机号已注册；10009 邮箱已注册 |
| 登录 | `POST /auth/login`（白名单） | `account` 必填（用户名/手机号/邮箱）；`password` 必填；`deviceId`?；`platform`? | 同注册响应 | 400；**10004**（用户不存在与密码错误统一，防枚举）；**10008**（连续失败 5 次锁 15 分钟） |
| 刷新 | `POST /auth/refresh`（白名单） | `refreshToken` 必填 | **4 字段**：`accessToken`、`refreshToken`（新）、`accessExpire`、`refreshExpire`。**轮换语义**：旧 refreshToken 一次性吊销 | 10005 |
| 登出 | `POST /auth/logout`（需鉴权） | Header Bearer；body `{accessToken?, refreshToken?}`（accessToken 字段不生效，实际取 header） | `null`（恒成功；有效 token 入黑名单，无效静默忽略） | — |
| 校验 | `GET /auth/validate`（需鉴权） | 无 | `{valid: boolean, userId, expiresAt}`；一切无效返回 `valid=false`（HTTP 200 code 0，不抛业务错误码） | — |

---

## 3. User 域（✅ 已实现）

`UserInfo`：`id`、`username`、`phone`、`email`、`avatar`、`gender`(0/1/2)、`bio`、`birthday`、`createdAt`、`updatedAt`、`balance`

**隐私规则（重要）**：
- 本人（`/me` 或 `{userId} == X-User-Id`）：完整 phone/email/balance
- **他人视角一律脱敏**：phone 前 3 后 4（≤7 位原样）、email 仅保留 @ 前首字符、`balance` 恒为 0
- `POST /users/batch` 即使包含本人也**一律脱敏**

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 我的资料 | `GET /users/me` | Header X-User-Id | `UserInfo`（完整） | 10001 |
| 更新资料 | `PUT /users/me` | 全可选：`avatar`?、`gender`?(0/1/2)、`bio`?、`birthday`?、`phone`?（空串清空）、`email`?（空串清空） | `UserInfo`（完整） | 400；10001；10003；10009 |
| 修改密码 | `PUT /users/me/password` | `oldPassword` 必填；`newPassword` 必填 6-32 含字母+数字 | `null` | 400；10001；10004 旧密码错误 |
| 用户详情 | `GET /users/{userId}` | PathVariable | `UserInfo`（他人视图脱敏） | 10001；400（路径非数字） |
| 批量查询 | `POST /users/batch` | **裸数组** `[123,456]`（≤500） | `{users: UserInfo[]}`（一律脱敏，不存在则缺失） | 400 |
| 搜索用户 | `POST /users/search?keyword=&pageNum=&pageSize=` | **参数走 query**；`keyword` 必填；pageSize 默认 20 | `{users: UserInfo[]（脱敏）, total}` | 400 |
| 改密副作用 | — | — | 改密后**改密前签发的全部 token 即刻失效**（网关/validate/refresh 三处校验） | — |

> ⚠️ `search` 仅匹配 **username**（LIKE，通配符已转义），不匹配手机号/邮箱——与初始规划不同，按实现定稿。

---

## 4. Friend 域（✅ 已实现）

**状态码**：`FriendRequestStatus` 1=待处理 2=已接受 3=已拒绝 4=已取消

**DTO**：
- `FriendRequestDTO`：`requestId`、`fromUserId`、`fromUsername`、`fromAvatar`、`toUserId`、`toUsername`、`toAvatar`、`message`、`status`(1-4)、`createdAt`、`updatedAt`
- `FriendDTO`：`userId`、`username`、`avatar`、`remark`、`groupId`、`groupName`（'0' = 默认分组）、`status`('online'/'offline')、`createdAt`
- `FriendGroupDTO`：`groupId`、`name`、`friendCount`、`createdAt`
- `BlacklistEntryDTO`：`userId`、`username`、`avatar`、`createdAt`
- 分页壳 `PagedList<T>`：`{list: T[], total, pageNum, pageSize}`

> 在线状态说明：`FriendDTO.status` 的在线判定依赖 signaling-service 的 presence（Phase B），
> Phase 1 后端恒返回 `'offline'`，字段结构已就绪。

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 发申请 | `POST /friends/requests` | `toUserId`；`message` | `{requestId}`（有 pending 时幂等返回原申请） | 10001 目标不存在；20001；20004；20006；20007 |
| 待处理申请 | `GET /friends/requests/pending` | query：`pageNum`?、`pageSize`?（默认 50） | `PagedList<FriendRequestDTO>`（仅 status=1 incoming） | — |
| 已发送申请 | `GET /friends/requests/sent` | query：`pageNum`?、`pageSize`?（默认 50） | `PagedList<FriendRequestDTO>`（outgoing 全状态） | — |
| 接受申请 | `POST /friends/requests/{requestId}/accept` | body `{}` | `FriendRequestDTO`（status→2，双方建好友） | 20002 |
| 拒绝申请 | `POST /friends/requests/{requestId}/reject` | body `{}` | `FriendRequestDTO`（status→3） | 20002 |
| 取消申请 | `DELETE /friends/requests/{requestId}` | — | `null`（status→4，仅发起人） | 20002 |
| 好友列表 | `GET /friends` | query：`groupId`?（0=默认）、`pageNum`?、`pageSize`?（默认 100） | `PagedList<FriendDTO>` | — |
| 分组列表 | `GET /friends/groups` | — | `{list: FriendGroupDTO[], total}`（内置 groupId='0' 默认分组） | — |
| 建分组 | `POST /friends/groups` | `name`（空则落"新建分组"） | `{groupId, name}` | — |
| 重命名分组 | `PUT /friends/groups/{groupId}` | `name` | `{groupId, name}` | 20005 |
| 删除分组 | `DELETE /friends/groups/{groupId}` | — | `null`（组内好友回落默认分组） | 20005 |
| 黑名单 | `GET /friends/blacklist` | query：`pageNum`?、`pageSize`?（默认 100） | `PagedList<BlacklistEntryDTO>` | — |
| 拉黑 | `POST /friends/blacklist/{userId}` | body `{}` | `null`（拉黑即解除好友+取消双方 pending；重复拉黑幂等） | 10001 目标不存在；20004 不能拉黑自己 |
| 取消拉黑 | `DELETE /friends/blacklist/{userId}` | — | `null` | 20008 不在黑名单 |
| 删除好友 | `DELETE /friends/{friendId}` | — | `null` | 20003 |
| 设置备注 | `PUT /friends/{friendId}/remark` | `remark` | `null` | 20003 |
| 移动分组 | `PUT /friends/{friendId}/group` | `groupId`（0=默认） | `null` | 20003；20005 |

> ⚠️ 与初始规划差异（按前端定稿）：拉黑路径为 `/friends/blacklist/{userId}`（非 `/friends/{userId}/block`）；分组字段 `groupId`（非 `id`）；黑名单字段 `createdAt`（非 `blockedAt`）；分组列表外层 `list`（非 `groups`）。
>
> ℹ️ 语义补充（2026-08-02 实现定稿）：删除好友/拉黑**不影响既有会话与历史消息**（会话由 conv-service 独立管理）；好友列表 `groupId` 缺省或 `0` 时返回全部好友（不按默认分组过滤）；pending 申请并发幂等由部分唯一索引 `(from_user_id, to_user_id) WHERE status=1` 保证（撞键幂等返回原申请）。

---

## 5. Conversation 域（✅ 已实现）

**DTO**：
- `ConversationDTO`：`id`、`type`(1=单聊 2=群聊)、`name`、`avatar`、`ownerId`、`memberCount`、`maxSeq`、`lastMessageId`、`lastMessagePreview`、`announcement`、`mutedAll`(boolean)、`createdAt`、`updatedAt`、`unreadCount`
- `ConversationMemberDTO`：`userId`、`username`、`avatar`、`role`(**1=OWNER 2=ADMIN 3=MEMBER**)、`alias`、`joinedAt`、`lastReadSeq`、`muted`(boolean)、`muteUntil`(epoch 秒)、`memberType`(1=user 2=bot)、`botId`
- `ConversationSettingsData`：`muted`(boolean)、`pinned`(boolean)、`nickname`

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 创建会话 | `POST /convs` | type=1：`type`、`peerUserId`；type=2：`type`、`name`(必填≤32)、`avatar`?、`memberIds`? | `{conversationId, conversation}`（**单聊幂等**：已存在返回原会话；不能与自己建单聊） | 400；30008 |
| 会话列表 | `GET /convs` | query：`pageNum`?、`pageSize`?（默认 20，上限 100） | `{conversations: ConversationDTO[], total}`（按 maxSeq 降序） | — |
| 会话详情 | `GET /convs/{conversationId}` | PathVariable | `ConversationDTO` | **30001 优先**；30004 非成员 |
| 成员列表 | `GET /convs/{conversationId}/members` | query：`pageNum`?、`pageSize`?（默认 50，上限 100） | `{members: ConversationMemberDTO[], total}`（OWNER→ADMIN→MEMBER，同级按加入时间） | 30001；30004 |
| 邀请入群 | `POST /convs/{conversationId}/members/invite` | `userIds`（请求内自动去重） | `{addedUserIds, alreadyMemberIds}`（不存在的用户静默跳过；超限整事务回滚） | 30001；30004；30005；30008 |
| 移出群聊 | `POST /convs/{conversationId}/members/kick` | `userIds` | `null`（管理员不能踢同级/上级；**OWNER 自退自动转移群主**给最早加入成员，无成员则 ownerId=0） | 30001；30004；30005；30003 |
| 禁言 | `PUT /convs/{conversationId}/members/{userId}/mute` | `durationSeconds`（**秒**，0=永久禁言） | `null` | 30001；30004；30005；30003 |
| 解除禁言 | `DELETE /convs/{conversationId}/members/{userId}/mute` | — | `null`（**清除禁言**：muted=false/muteUntil=0；与永久禁言 muted=true/0 区分） | 30001；30004；30005；30003 |
| 转让群主 | `POST /convs/{conversationId}/transfer` | `newOwnerId` | `null`（原群主降为成员） | 30001；30004；30005；30009 转给自己；30003 |
| 设置公告 | `PUT /convs/{conversationId}/announcement` | `content`（≤500，空串=清除） | `null` | 30001；30004；30005；400 |
| 删除公告 | `DELETE /convs/{conversationId}/announcement` | — | `null` | 同上 |
| 查设置 | `GET /convs/{conversationId}/settings` | — | `{muted, pinned, nickname}`（**不校验成员身份**） | — |
| 改设置 | `PUT /convs/{conversationId}/settings` | 全可选：`isMuted`?、`isPinned`?、`nickname`?（null 不更新；不校验成员身份） | `null` | — |
| 标记已读 | `PUT /convs/{conversationId}/read` | `seq`（UPSERT GREATEST 只增不减） | `null` | 30001；30004 |

> ⚠️ 与初始规划差异（按实现定稿）：role 枚举 1=OWNER/2=ADMIN/3=MEMBER（前端 mock 曾用 0/1/2）；settings 键名 `muted`/`pinned` 无 is- 前缀；群名上限 32、公告上限 500。

---

## 6. Message 域（⏳ 待实现，契约按前端 mock 定稿）

**枚举**：`MsgType` 1=文本 2=图片 3=文件 4=视频 5=语音 6=位置 7=系统；`MessageStatus` 1=正常 2=已撤回 3=已删除
**内容类型**：`TextContent{text, mentionUserIds?, mentionAll?}`；`ImageContent{fileId, url, thumbnailUrl, width, height, size, format}`；`FileContent{fileId, url, name, size, ext, mimeType}`；`SystemContent{action, detail, relatedUserIds?, actorId?, actorType?, payload?}`
**DTO**：`MessageDTO{messageId, conversationId, seq, fromUserId, msgType, status, content（已撤回为 {}）, replyToId, replyToPreview, editCount, editedAt, createdAt}`

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 发送消息 | `POST /messages/send` | `conversationId`；`msgType`；`content`；`replyToId`?；`clientMsgId`（幂等键） | `{messageId, seq, createdAt}`（发送者自动 markRead） | 40004 重复发送；30004；30006 被禁言；30007 全员禁言 |
| 回复消息 | `POST /messages/{messageId}/reply` | 同 send body | 同 send | 同 send |
| 消息列表 | `GET /messages/{conversationId}` | query：`cursor`（**seq 游标，0=最新，降序**）；`limit`?（默认 20，上限 50） | `{list: MessageDTO[], nextCursor: string\|null, hasMore, total}` | 30001；30004 |
| 增量同步 | `GET /messages/{conversationId}/sync` | query：`fromSeq`?（默认 0，**升序**）；`limit`?（默认 50，上限 200） | `{list: MessageDTO[], hasMore, maxSeq}` | 30001；30004 |
| 撤回 | `POST /messages/{messageId}/recall` | body `{}` | `null`（推下行 `message.recalled`） | 40001；40002 超 120s；40005 非本人且非管理 |
| 编辑 | `PUT /messages/{messageId}` | `newContent` | `null`（推下行 `message.edited`） | 40001；40003 超 120s；40005 仅作者 |
| 删除 | `DELETE /messages/{messageId}` | `deleteForAll`（true=全员删除需本人或管理；false/缺省=仅自己隐藏） | `null` | 40001；40005 |
| 搜索 | `GET /messages/search` | query：`keyword`；`conversationId`?（缺省='0'=全部）；`pageNum`?；`pageSize`? | `{list: MessageDTO[], total, pageNum, pageSize}`（仅文本+正常状态，createdAt 降序） | — |

**时间窗**：撤回/编辑窗口 120 秒（`RECALL_WINDOW` / `EDIT_WINDOW`）

---

## 7. File 域（✅ 已实现）

**上传三步**：`POST /files/upload-url` → 直传 `uploadUrl`（对象存储预签名 URL）→ `POST /files/confirm`

`FileInfo`：`fileId`、`name`、`key`、`size`、`mimeType`、`ext`、`width`、`height`、`duration`、`md5`、`purpose`(1=附件 2=头像)、`access`(1=私有 2=会话内 3=公开)、`uploaderId`、`bucket`、`status`(0=PENDING 1=CONFIRMED 2=DELETED)、`createdAt`

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 申请上传 | `POST /files/upload-url` | `name` 必填；`mimeType` 必填；`size` 必填>0；`purpose`；`access`（**expiresIn 无效，服务端固定 1800s**） | `{fileId, uploadUrl, key, expiresAt}` | 400；50004（类型不支持/SVG）；50003（超限）；50002 |
| 确认上传 | `POST /files/confirm` | `fileId`；`md5`?（仅记录不校验） | `{file: FileInfo}` | 50001；50007 非上传者；400 状态非 PENDING；50002 对象未上传；50003 实际超声明（拒绝并删对象） |
| 下载链接 | `GET /files/{fileId}/download` | PathVariable（无 query） | `{downloadUrl, expiresAt, file}` | 50001；50005 PENDING；50006 DELETED |
| 文件信息 | `GET /files/{fileId}/info` | PathVariable | `FileInfo` | 50001；50005；50006 |
| 删除文件 | `DELETE /files/{fileId}` | PathVariable（无 body） | `null`（软删 status→2；仅上传者；**非幂等**：重复删 → 50006） | 50001；50007；50006 |
| 批量信息 | `POST /files/batch` | **裸数组**（≤500） | **`FileInfo[]`（裸数组，非对象包裹）**（仅 CONFIRMED） | 400 |

**大小限制**：`purpose=2`（头像）上限 **5MB**；其余一律 **100MB**；`image/svg+xml` 禁止（XSS）
**访问控制（Phase 1）**：任何登录用户可下载 CONFIRMED 文件（不校验成员/上传者关系；Phase 2 接入会话级校验）

---

## 8. Notification 域（⏳ 待实现，契约按前端 mock 定稿）

`NotificationDTO`：`id`、`userId`、`type`(1=系统 2=审核 3=Bot)、`title`、`content`、`isRead`(boolean)、`referenceId`、`createdAt`

| 接口 | 方法+路径 | 请求 | 响应 data | 错误码 |
|---|---|---|---|---|
| 通知列表 | `GET /notifications` | query：`pageNum`?、`pageSize`?（默认 20）、`type`?（0=不过滤）、`isRead`?（'true'/'false'） | `PagedList<NotificationDTO>`（createdAt 降序） | — |
| 未读数 | `GET /notifications/unread-count` | — | `{count}` | — |
| 标记已读 | `POST /notifications/{notificationId}/read` | body `{}` | `null` | 60001 |
| 全部已读 | `POST /notifications/read-all` | body `{}` | `null` | — |
| 删除 | `DELETE /notifications/{notificationId}` | — | `null` | 60001 |

---

## 9. WebSocket 协议（⏳ 待实现，事件名已按后端常量定稿）

**帧格式**（上下行统一）：`{ event: string, data: unknown, timestamp: number }`
**连接**：`ws://<host>/ws?token=<accessToken>&device_id=<deviceId>`；心跳 `ping` 30s/次；90s 无下行帧判断线；重连指数退避 1s→30s

### 9.1 上行（客户端 → 服务端）

| 事件 | data | 说明 |
|---|---|---|
| `ping` | `{}` | 心跳 |
| `subscribe_presence` | `{userIds}` | 订阅在线状态 |
| `unsubscribe_presence` | `{userIds}` | 取消订阅 |
| `typing` | `{convId, userId}` | 正在输入 |
| `typing_stop` | `{convId, userId}` | 停止输入 |
| `ack` | `{messageId, convId, seq}` | 消息送达确认 |

### 9.2 下行（服务端 → 客户端）

| 事件 | data | 说明 |
|---|---|---|
| `pong` | `{}` | 心跳回应 |
| `message.new` | `messageId, convId, seq, fromUserId, msgType, status, content, replyToId, replyToPreview, createdAt, unreadCount, senderInfo{id, username, avatar}` | 新消息 |
| `message.recalled` | `{messageId, convId, userId}` | 消息撤回 |
| `message.edited` | `{messageId, convId, userId, newContent}` | 消息编辑 |
| `presence` | `{userId, status:'online'\|'offline'}` | 在线状态（Phase B） |
| `read_sync` | `{convId, userId, lastReadSeq}` | 已读同步（Phase B） |
| `typing.notify` | `{convId, userId}` | **正在输入通知**（下行专用，与上行 `typing` 区分） |
| `typing.stop` | `{convId, userId}` | 停止输入通知 |
| `unread_count` | `{convId, count}` | 未读数变化 |
| `read_receipt` | `{messageId, userId, readAt}` | 已读回执（Phase B） |
| `conversation.updated` | `{convId}` | 会话变更（前端扩展） |
| `notification.new` | `NotificationDTO` | 新通知（前端扩展） |

> ⚠️ 与初始规划差异（按后端定稿）：下行输入中事件为 **`typing.notify`**（非 `typing`），避免与上行同名混淆。

---

## 10. 错误码全表

| code | 含义 | 产生场景 |
|---|---|---|
| 0 | 成功 | 所有成功响应 |
| 400 | 请求参数错误 | 校验失败/批量超 500/群名公告超长/文件参数非法等 |
| 401 | 未认证 | 网关鉴权失败（HTTP 401）；X-User-Id 缺失（user-service） |
| 500 | 服务器内部错误 | 未知异常兜底 |
| 10001 | 用户不存在 | 资料查询/改密/更新资料目标不存在 |
| 10002 | 用户名已存在 | 注册 |
| 10003 | 手机号已被注册 | 注册/更新资料 |
| 10004 | 密码错误 | **登录（用户不存在与密码错误统一，防枚举）**/改密旧密码错误 |
| 10005 | Token 无效或已过期 | refresh 失败（验签/过期/type/吊销/改密前签发） |
| 10006 | Token 已过期 | 预留（未产生） |
| 10007 | 会话不存在 | 预留（未产生） |
| 10008 | **登录失败次数过多** | 登录连续失败 5 次锁定 15 分钟 |
| 10009 | 邮箱已被注册 | 注册/更新资料 |
| 20001 | 你们已经是好友了 | friend |
| 20002 | 好友申请不存在或已处理 | friend |
| 20003 | 对方不是你的好友 | friend |
| 20004 | 不能添加自己为好友 | friend |
| 20005 | 好友分组不存在 | friend |
| 20006 | 对方已被你拉黑 | friend |
| 20007 | 你已被对方拉黑 | friend |
| 20008 | 未拉黑该用户 | friend（取消拉黑时目标不在黑名单） |
| 30001 | 会话不存在 | conv（优先于 30004） |
| 30002 | 用户已是会话成员 | 预留（invite 已存在者归入 alreadyMemberIds） |
| 30003 | 用户不在会话中 | kick/mute/transfer 目标不在会话 |
| 30004 | 非会话成员 | conv 操作者身份校验 |
| 30005 | 权限不足 | 非管理操作/对同级上级操作 |
| 30006 | 已被禁言 | message 发送拦截（待实现） |
| 30007 | 全员禁言中 | message 发送拦截（待实现） |
| 30008 | 成员数量已达上限（500） | 群聊创建/invite |
| 30009 | 不能转让给自己 | transfer |
| 40001 | 消息不存在 | message |
| 40002 | 已超过可撤回时间（120s） | message |
| 40003 | 已超过可编辑时间（120s） | message |
| 40004 | 请勿重复发送 | message（clientMsgId 幂等） |
| 40005 | 没有操作该消息的权限 | message |
| 50001 | 文件不存在 | file |
| 50002 | 文件上传失败 | upload-url/confirm |
| 50003 | 文件过大 | 按 purpose 限制（头像 5MB/其他 100MB） |
| 50004 | 不支持的文件类型 | MIME 白名单外/SVG |
| 50005 | 文件尚未上传确认 | download/info 遇 PENDING |
| 50006 | 文件已删除 | download/info/delete 遇 DELETED |
| 50007 | 无权操作他人文件 | confirm/delete 归属校验 |
| 60001 | 通知不存在 | notification |

**前端文案优先级**：映射表（`errorCodes.ts`）> 服务端 message > 默认"操作失败（code）"
**会话失效码**：401 / 10005 / 10006 → 触发静默刷新流程

---

## 11. 契约决策记录（本次基线修正）

| # | 冲突点 | 决策 | 依据 |
|---|--------|------|------|
| 1 | refresh 响应结构 | 后端版：4 字段 + 轮换 | 安全最佳实践（旧 refreshToken 泄露可复用 30 天） |
| 2 | 登录错误码 | 后端版：10001 与 10004 统一 | 防账户枚举 |
| 3 | search 匹配范围 | 后端版：仅 username | 隐私与实现一致 |
| 4 | 他人资料 | 后端版：脱敏 + balance=0 | 隐私修复 |
| 5 | settings 键名 | 后端版：`muted`/`pinned` | JSON 规范（无 is- 前缀） |
| 6 | WS 下行 typing | 后端版：`typing.notify` | 避免与上行 `typing` 同名 |
| 7 | 拉黑路径 | 前端版：`/friends/blacklist/{userId}` | 前端已实现且语义清晰 |
| 8 | friend 字段 | 前端版：`groupId`/`createdAt`/`list` | 前端 mock 自洽 |
| 9 | friend/message/notification 全契约 | 前端版 | 前端领先、后端未实现，成本最低 |
| 10 | 错误码 10008 文案 | 统一为"登录失败次数过多" | 与实现语义一致 |
| 11 | role 枚举 | 后端版：1=OWNER 2=ADMIN 3=MEMBER | 后端已实现 |
| 12 | 群名/公告上限 | 后端版：32/500 | 后端已实现 |
| 13 | 取消拉黑错误码 | 后端版：20008（NOT_BLOCKED，"未拉黑该用户"） | ErrorCode 20001-20008 已定稿；契约表格旧值 20002 同步修正，前端 errorCodes.ts 补 20008 |
| 14 | 好友申请幂等并发 | 部分唯一索引 `(from_user_id, to_user_id) WHERE status=1` | 防并发双 pending，无锁；撞键捕获 DuplicateKeyException 幂等返回原申请 |
| 15 | 发申请/拉黑目标不存在 | 复用 user 域 10001 | mock requireUser 抛 10001（非 400），契约错误码列补 10001 |
| 16 | 好友在线状态 | Phase 1 恒 `'offline'` | signaling presence 未实现（Phase B 接入），字段结构已就绪 |

---

## 附录 A：历史说明

- 2026-07-11 的初始规划版（draft，端口 8080、logout body、validate 含 deviceId、friend 路径、message 游标分页等已过时内容）已删除，不再保留
- 本文件（2026-07-31 契约基线，合并自原 `api-v1-implemented.md`）是唯一契约
- 后续所有开发（后端实现 / 前端 mock 修改）以本文件为准，变更必须回写本文件

## 附录 B：后续开发指引

- **friend-service 实现**（✅ 2026-08-02 完成）：按第 4 章契约落地（HTTP Controller + Dubbo Provider），错误码 20001-20008 全部投入使用；common 模块 friend DTO 已对齐契约（`groupId/createdAt/list` 等字段），新增 `BlacklistEntryDTO`/`MoveGroupReq`/`RenameGroupResp`；集成测试（Testcontainers）与单测全绿
- **message-service 实现**：按第 6 章契约 + common 模块 `event/MessageCreatedEvent` 等事件 DTO；发送拦截用 30006/30007；幂等用 clientMsgId
- **signaling-service + ws-gateway**：按第 9 章协议（事件名已定稿），下行统一 `typing.notify`；未读 key 前缀 `aim:unread:`（CommonConst.REDIS_KEY_UNREAD）
- **Notification**：按第 8 章契约
- **前端适配项**（已按本契约修正）：refresh 4 字段轮换、settings 键名、WS typing.notify、logout 带 Authorization、errorCodes 补 10009/改 10008
