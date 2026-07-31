# AIM API v1 接口文档（历史规划）

> **版本**：v1.0.0-draft（Phase 1 核心 IM，不含 Bot/AI 模块）

> ## ⚠️ 本文档已过时（2026-07-31 起）
>
> 本文档为项目初始规划契约（draft 版），部分内容与当前实现不符（端口 8080、接口路径、字段、错误码语义等均已变化）。
> **当前契约以 [api-v1-implemented.md](./api-v1-implemented.md) 为准（唯一事实来源）**，本文档仅作历史参考。
>

> **Base URL**：`http://{host}:8080/api/v1`
> **Content-Type**：`application/json`
> **字符编码**：UTF-8
> **日期格式**：epoch 毫秒时间戳（`number`）

---

## 1. 通用约定

### 1.1 统一响应格式

所有接口返回 JSON，外层结构为：

```json
{
  "code": 0,           // 0=成功, 非0=失败（见错误码表）
  "message": "success",
  "data": { ... }      // 具体数据，失败时为 null
}
```

分页接口中 `data` 结构为：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [ ... ],            // 数据列表
    "total": 100,               // 总数
    "pageNum": 1,               // 当前页码（从 1 开始）
    "pageSize": 20              // 每页数量
  }
}
```

消息列表使用游标分页：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [ ... ],
    "nextCursor": "50",         // 下一页游标，为 null 表示已到最后一页
    "hasMore": true,
    "total": 100
  }
}
```

### 1.2 认证方式

除注册和登录外，所有接口需在 Header 携带 JWT：

```
Authorization: Bearer <access_token>
```

Token 通过登录接口获取，有效期 2 小时。

### 1.3 错误码段

| 段 | 服务 | 示例 |
|---|---|---|
| 10xxx | user-service | 10001 用户不存在 |
| 20xxx | friend-service | 20001 已是好友 |
| 30xxx | conv-service | 30001 会话不存在 |
| 40xxx | message-service | 40001 消息不存在 |
| 50xxx | file-service | 50001 文件不存在 |
| 60xxx | signaling-service | 60001 通知不存在 |

完整错误码表见 [§9 错误码参考](#9-错误码参考)。

---

## 2. Auth 认证

### 2.1 注册

```
POST /auth/register
```

**Request Body**:

```json
{
  "username": "zhangsan",      // 必填, 3~32字符
  "password": "Abc@123456",    // 必填, 6~32字符
  "phone": "13800138000",     // 可选
  "email": "zhangsan@foo.com", // 可选
  "deviceId": "device-uuid",   // 必填, 设备唯一标识
  "platform": "web"            // 必填, ios/android/web
}
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1234567890123456789,
    "tokens": {
      "accessToken": "eyJhbGciOi...",
      "refreshToken": "eyJhbGciOi...",
      "accessExpire": 1707123400000,
      "refreshExpire": 1707728200000
    },
    "user": {
      "id": 1234567890123456789,
      "username": "zhangsan",
      "phone": "138****8000",
      "email": "zhan****@foo.com",
      "avatar": "",
      "gender": 0,
      "bio": "",
      "birthday": 0,
      "createdAt": 1707100000000,
      "updatedAt": 1707100000000,
      "balance": 0
    }
  }
}
```

### 2.2 登录

```
POST /auth/login
```

**Request Body**:

```json
{
  "account": "zhangsan",       // 用户名/手机号/邮箱
  "password": "Abc@123456",
  "deviceId": "device-uuid",
  "platform": "web"
}
```

**Response**：同注册返回，`code=0` 且 `data` 结构一致。

### 2.3 登出

```
POST /auth/logout
```

**Headers**: `Authorization: Bearer <token>`

**Request Body**:

```json
{
  "userId": 1234567890123456789,
  "tokenId": "session-uuid"
}
```

**Response**: `{"code":0,"message":"success","data":null}`

### 2.4 Token 校验

```
GET /auth/validate
```

**Headers**: `Authorization: Bearer <token>`

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "valid": true,
    "userId": 1234567890123456789,
    "deviceId": "device-uuid",
    "expiresAt": 1707123400000
  }
}
```

---

## 3. User 用户

### 3.1 获取当前用户资料

```
GET /users/me
```

**Headers**: `Authorization: Bearer <token>`

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1234567890123456789,
    "username": "zhangsan",
    "phone": "138****8000",
    "email": "zhan****@foo.com",
    "avatar": "https://minio.xx.com/aim/avatar/abc.jpg",
    "gender": 1,
    "bio": "Hello IM!",
    "birthday": 946656000000,
    "createdAt": 1707100000000,
    "updatedAt": 1707100000000,
    "balance": 0
  }
}
```

### 3.2 更新用户资料

```
PUT /users/me
```

**Headers**: `Authorization: Bearer <token>`

**Request Body**（所有字段可选，传什么更新什么）：

```json
{
  "avatar": "https://new-avatar.jpg",
  "gender": 1,
  "bio": "新签名",
  "birthday": 946656000000
}
```

**Response**：返回更新后的完整 `UserInfo`（同 3.1）。

### 3.3 修改密码

```
PUT /users/me/password
```

```json
{
  "oldPassword": "Abc@123456",
  "newPassword": "Xyz@654321"
}
```

### 3.4 获取指定用户信息

```
GET /users/{userId}
```

**Response**: `data` 为单个 `UserInfo` 对象（同 3.1）。

### 3.5 批量获取用户信息

```
POST /users/batch
```

**Request Body**:

```json
{
  "userIds": [123456, 789012, 345678]
}
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "users": [ { ...UserInfo }, { ...UserInfo }, { ...UserInfo } ]
  }
}
```

### 3.6 搜索用户

```
POST /users/search
```

```json
{
  "keyword": "zhang",
  "pageNum": 1,
  "pageSize": 20
}
```

**Response**: 分页格式，`list` 中每项为 `UserInfo`。

### 3.7 批量查询在线状态

```
POST /users/batch-status
```

```json
{
  "userIds": [123456, 789012]
}
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": {
      "123456": true,
      "789012": false
    }
  }
}
```

---

## 4. Friend 好友

### 4.1 发送好友申请

```
POST /friends/requests
```

```json
{
  "fromUserId": 123456,
  "toUserId": 789012,
  "message": "我是张三"   // 验证消息
}
```

**Response**: `{"requestId": 987654321}`

### 4.2 接受好友申请

```
POST /friends/requests/{requestId}/accept
```

```json
{
  "requestId": 987654321,
  "userId": 789012
}
```

### 4.3 拒绝好友申请

```
POST /friends/requests/{requestId}/reject
```

```json
{
  "requestId": 987654321,
  "userId": 789012
}
```

### 4.4 取消发出的申请

```
DELETE /friends/requests/{requestId}
```

```json
{
  "requestId": 987654321,
  "userId": 123456
}
```

### 4.5 待处理申请列表（发给我的）

```
GET /friends/requests/pending?pageNum=1&pageSize=20
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "requestId": 9876,
        "fromUserId": 123456,
        "fromUsername": "zhangsan",
        "fromAvatar": "https://...",
        "toUserId": 789012,
        "message": "我是张三",
        "status": 1,                   // 1=待处理
        "createdAt": 1707100000000,
        "updatedAt": 1707100000000
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

### 4.6 我发出的申请列表

```
GET /friends/requests/sent?pageNum=1&pageSize=20
```

Response 结构同 4.5。

### 4.7 好友列表

```
GET /friends?pageNum=1&pageSize=50&groupId=0
```

`groupId` 可选，指定分组 ID 则只查该分组好友，不传/传 0 查全部。

**Response**:

```json
{
  "list": [
    {
      "userId": 789012,
      "username": "lisi",
      "avatar": "https://...",
      "remark": "李四",              // 备注名
      "groupId": 0,
      "groupName": "默认分组",
      "status": "online",            // online / offline
      "createdAt": 1707100000000
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 50
}
```

### 4.8 删除好友

```
DELETE /friends/{friendUserId}
```

```json
{
  "userId": 123456,
  "friendId": 789012
}
```

### 4.9 设置好友备注

```
PUT /friends/{friendUserId}/remark
```

```json
{
  "userId": 123456,
  "friendId": 789012,
  "remark": "李四"
}
```

### 4.10 移动好友到分组

```
PUT /friends/{friendUserId}/group
```

```json
{
  "userId": 123456,
  "friendId": 789012,
  "groupId": 5
}
```

### 4.11 创建好友分组

```
POST /friends/groups
```

```json
{
  "userId": 123456,
  "name": "同事"
}
```

**Response**: `{"groupId": 10001}`

### 4.12 重命名分组

```
PUT /friends/groups/{groupId}
```

```json
{
  "groupId": 10001,
  "userId": 123456,
  "name": "前同事"
}
```

### 4.13 删除分组

```
DELETE /friends/groups/{groupId}
```

```json
{
  "groupId": 10001,
  "userId": 123456
}
```

### 4.14 分组列表

```
GET /friends/groups
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "groups": [
      {
        "id": 1,
        "name": "默认分组",
        "sortOrder": 0,
        "friendCount": 15,
        "createdAt": 1707100000000
      },
      {
        "id": 10001,
        "name": "同事",
        "sortOrder": 1,
        "friendCount": 8,
        "createdAt": 1707100000000
      }
    ]
  }
}
```

### 4.15 拉黑用户

```
POST /friends/{userId}/block
```

```json
{
  "userId": 123456,
  "blockedUserId": 789012
}
```

### 4.16 解除拉黑

```
DELETE /friends/{userId}/block
```

```json
{
  "userId": 123456,
  "blockedUserId": 789012
}
```

### 4.17 黑名单列表

```
GET /friends/blacklist?pageNum=1&pageSize=50
```

**Response**:

```json
{
  "list": [
    {
      "userId": 789012,
      "username": "lisi",
      "avatar": "https://...",
      "blockedAt": 1707100000000
    }
  ],
  "total": 1,
  "pageNum": 1,
  "pageSize": 50
}
```

---

## 5. Conversation 会话

### 5.1 创建会话

```
POST /convs
```

**单聊**:

```json
{
  "type": 1,                    // 1=单聊 2=群聊
  "creatorId": 123456,
  "peerUserId": 789012          // 单聊时指定对方用户ID
}
```

**群聊**:

```json
{
  "type": 2,
  "creatorId": 123456,
  "name": "技术讨论组",         // 群名称
  "avatar": "",                 // 可选，群头像URL
  "memberIds": [789012, 345678] // 初始成员列表
}
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "conversationId": 5550000000000001,
    "conversation": {
      "id": 5550000000000001,
      "type": 2,
      "name": "技术讨论组",
      "avatar": "",
      "ownerId": 123456,
      "memberCount": 3,
      "maxSeq": 0,
      "lastMessageId": 0,
      "lastMessagePreview": "",
      "announcement": "",
      "isMutedAll": false,
      "createdAt": 1707100000000,
      "updatedAt": 1707100000000
    }
  }
}
```

### 5.2 获取会话详情

```
GET /convs/{conversationId}
```

**Response**: `data` 为 `Conversation` 对象。

### 5.3 会话列表

```
GET /convs?cursor=&limit=20&type=0&pinnedFirst=true
```

| 参数 | 说明 |
|---|---|
| `cursor` | 游标（首次传空），取上一页最后一条会话的 `maxSeq` |
| `limit` | 每页数量，默认 20 |
| `type` | 0=全部, 1=单聊, 2=群聊 |
| `pinnedFirst` | 是否置顶优先 |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 5550000000000001,
        "type": 2,
        "name": "技术讨论组",
        "avatar": "",
        "ownerId": 123456,
        "memberCount": 3,
        "maxSeq": 10,
        "lastMessageId": 8880001,
        "lastMessagePreview": "[图片]",
        "lastReadSeq": 8,
        "unreadCount": 2,
        "isMuted": false,
        "isPinned": false,
        "isMutedAll": false,
        "announcement": "",
        "background": "",
        "createdAt": 1707100000000,
        "updatedAt": 1707100000001
      }
    ],
    "nextCursor": "10",
    "hasMore": false,
    "total": 1
  }
}
```

### 5.4 更新会话信息

```
PUT /convs/{conversationId}/info
```

```json
{
  "conversationId": 5550001,
  "userId": 123456,
  "name": "新群名",          // 可选
  "avatar": "https://...",   // 可选
  "background": ""           // 可选，聊天背景
}
```

### 5.5 解散/退出会话

```
DELETE /convs/{conversationId}
```

```json
{
  "conversationId": 5550001,
  "userId": 123456
}
```

### 5.6 成员列表

```
GET /convs/{conversationId}/members?pageNum=1&pageSize=50
```

**Response**:

```json
{
  "list": [
    {
      "userId": 123456,
      "username": "zhangsan",
      "avatar": "https://...",
      "role": 1,           // 0=Member, 1=Owner, 2=Admin
      "alias": "",
      "joinedAt": 1707100000000,
      "lastReadSeq": 10,
      "isMuted": false,
      "muteUntil": 0,
      "memberType": 1      // 1=user, 2=bot(Phase 2)
    },
    {
      "userId": 789012,
      "username": "lisi",
      "avatar": "https://...",
      "role": 0,
      "alias": "",
      "joinedAt": 1707100000000,
      "lastReadSeq": 8,
      "isMuted": false,
      "muteUntil": 0,
      "memberType": 1
    }
  ],
  "total": 2,
  "pageNum": 1,
  "pageSize": 50
}
```

### 5.7 邀请成员

```
POST /convs/{conversationId}/members/invite
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "userIds": [333333, 444444]
}
```

**Response**: `{"addedUserIds": [333333, 444444], "failedUserIds": []}`

### 5.8 移除成员

```
POST /convs/{conversationId}/members/kick
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "userIds": [789012]
}
```

### 5.9 设置成员角色

```
PUT /convs/{conversationId}/members/{userId}/role
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "userId": 789012,
  "role": 2             // 0=Member, 1=Owner, 2=Admin
}
```

### 5.10 禁言成员

```
PUT /convs/{conversationId}/members/{userId}/mute
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "userId": 789012,
  "durationSeconds": 3600   // 0=永久
}
```

### 5.11 解除禁言

```
DELETE /convs/{conversationId}/members/{userId}/mute
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "userId": 789012
}
```

### 5.12 全员禁言

```
POST /convs/{conversationId}/mute-all
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456
}
```

### 5.13 取消全员禁言

```
DELETE /convs/{conversationId}/mute-all
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456
}
```

### 5.14 设置群公告

```
PUT /convs/{conversationId}/announcement
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "content": "本周五团建"
}
```

### 5.15 删除群公告

```
DELETE /convs/{conversationId}/announcement
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456
}
```

### 5.16 转让群主

```
POST /convs/{conversationId}/transfer
```

```json
{
  "conversationId": 5550001,
  "operatorId": 123456,
  "newOwnerId": 789012
}
```

### 5.17 获取会话设置

```
GET /convs/{conversationId}/settings
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "isMuted": false,
    "isPinned": false
  }
}
```

### 5.18 更新会话设置

```
PUT /convs/{conversationId}/settings
```

```json
{
  "conversationId": 5550001,
  "userId": 123456,
  "isMuted": true,     // 可选，免打扰
  "isPinned": true     // 可选，置顶
}
```

### 5.19 标记已读

```
PUT /convs/{conversationId}/read
```

```json
{
  "conversationId": 5550001,
  "userId": 123456,
  "seq": 10           // 已读到第几条消息的 seq
}
```

### 5.20 消息已读状态

```
GET /convs/{conversationId}/read-status/{messageId}
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "readCount": 2,
    "totalCount": 5,
    "readUsers": [
      { "userId": 123456, "readAt": 1707100001000, "lastReadSeq": 10 },
      { "userId": 789012, "readAt": 1707100002000, "lastReadSeq": 9 }
    ]
  }
}
```

---

## 6. Message 消息

### 6.1 消息类型定义

| type | 含义 | content 结构 |
|---|---|---|
| 1 | 文本 | `{ "text": "hello", "mentionUserIds": [], "mentionAll": false }` |
| 2 | 图片 | `{ "fileId": 123, "url": "...", "thumbnailUrl": "...", "width": 800, "height": 600, "size": 102400, "format": "jpeg" }` |
| 3 | 文件 | `{ "fileId": 123, "url": "...", "name": "doc.pdf", "size": 204800, "ext": "pdf", "mimeType": "application/pdf" }` |
| 4 | 视频 | `{ "fileId": 123, "url": "...", "thumbnailUrl": "...", "duration": 30, "width": 1920, "height": 1080, "size": 5242880 }` |
| 5 | 语音 | `{ "fileId": 123, "url": "...", "duration": 15, "size": 81920 }` |
| 6 | 位置 | `{ "latitude": 39.9042, "longitude": 116.4074, "address": "北京市...", "name": "天安门" }` |
| 7 | 系统 | `{ "action": "member.joined", "detail": "张三邀请李四加入群聊", "relatedUserIds": [333], "actorId": 123, "actorType": "user", "payload": "{}" }` |

### 6.2 发送消息

```
POST /messages/send
```

**Request Body**:

```json
{
  "conversationId": 5550001,
  "fromUserId": 123456,
  "msgType": 1,
  "content": {
    "text": "Hello everyone!",
    "mentionUserIds": [789012],
    "mentionAll": false
  },
  "replyToId": 0,
  "clientMsgId": "client-uuid-20240711-001"
}
```

| 字段 | 说明 |
|---|---|
| `msgType` | 消息类型，见 6.1 |
| `content` | 消息内容，结构因 msgType 而异 |
| `replyToId` | 可选，引用的消息 ID |
| `clientMsgId` | **必填**，客户端幂等 key（同一消息重复发送返回 40004） |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "messageId": 8880001,
    "seq": 11,
    "createdAt": 1707100000000
  }
}
```

### 6.3 撤回消息

```
POST /messages/{messageId}/recall
```

```json
{
  "messageId": 8880001,
  "conversationId": 5550001,
  "userId": 123456
}
```

> 限发送后 120 秒内可撤回，超时返回 40002。

### 6.4 编辑消息

```
PUT /messages/{messageId}
```

```json
{
  "messageId": 8880001,
  "conversationId": 5550001,
  "userId": 123456,
  "newContent": {
    "text": "Hello everyone! (已修改)"
  }
}
```

> 限发送后 120 秒内可编辑，超时返回 40003。

### 6.5 删除消息

```
DELETE /messages/{messageId}
```

```json
{
  "messageId": 8880001,
  "conversationId": 5550001,
  "userId": 123456,
  "deleteForAll": false
}
```

### 6.6 获取消息列表（游标分页）

```
GET /messages/{conversationId}?cursor=50&limit=20&beforeTime=0&afterTime=0
```

| 参数 | 说明 |
|---|---|
| `cursor` | 游标（上页最后一条的 seq），首次传 `0` |
| `limit` | 每页数量，默认 20，最大 50 |
| `beforeTime` | 可选，只拉该时间之前的消息 |
| `afterTime` | 可选，只拉该时间之后的消息 |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "messageId": 8880002,
        "conversationId": 5550001,
        "seq": 11,
        "fromUserId": 123456,
        "msgType": 1,
        "status": 1,
        "content": { "text": "hello", "mentionUserIds": [], "mentionAll": false },
        "replyToId": 0,
        "replyToPreview": "",
        "editCount": 0,
        "editedAt": 0,
        "createdAt": 1707100000002
      },
      {
        "messageId": 8880001,
        "conversationId": 5550001,
        "seq": 10,
        "fromUserId": 789012,
        "msgType": 2,
        "status": 1,
        "content": { "fileId": 123, "url": "https://...", "thumbnailUrl": "https://...", "width": 800, "height": 600, "size": 102400, "format": "jpeg" },
        "replyToId": 0,
        "replyToPreview": "",
        "editCount": 0,
        "editedAt": 0,
        "createdAt": 1707100000001
      }
    ],
    "nextCursor": "9",
    "hasMore": true,
    "total": 100
  }
}
```

> 消息按 `seq` 降序排列（最新在上），`cursor` 传本页最后一条的 `seq` 获取更早的消息。

### 6.7 增量同步消息

```
GET /messages/{conversationId}/sync?fromSeq=10&limit=50
```

| 参数 | 说明 |
|---|---|
| `fromSeq` | 客户端本地最后的 seq，拉取 `seq > fromSeq` 的消息 |
| `limit` | 默认 50，最大 200 |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [ ... ],
    "hasMore": false,
    "maxSeq": 15
  }
}
```

> `syncMessages` 是消息不丢的兜底机制——客户端断线重连后调用此接口拉取遗漏的消息。

### 6.8 获取单条消息

```
GET /messages/{messageId}
```

**Response**: `data` 为单个 `Message` 对象。

### 6.9 批量获取消息

```
POST /messages/batch
```

```json
{ "messageIds": [8880001, 8880002, 8880003] }
```

### 6.10 搜索消息

```
GET /messages/search?keyword=hello&conversationId=0&pageNum=1&pageSize=20
```

| 参数 | 说明 |
|---|---|
| `keyword` | 搜索关键词 |
| `conversationId` | 可选，限定会话 |
| `startTime` | 可选，起始时间（epoch ms） |
| `endTime` | 可选，结束时间（epoch ms） |
| `senderId` | 可选，发送者 |

**Response**: 分页格式，list 中每项为 `Message` 对象。

### 6.11 引用回复

```
POST /messages/{messageId}/reply
```

```json
{
  "conversationId": 5550001,
  "fromUserId": 123456,
  "msgType": 1,
  "content": { "text": "收到~" },
  "replyToId": 8880001,
  "clientMsgId": "client-uuid-20240711-002"
}
```

> 本质就是 `sendMessage` 带 `replyToId`，网关层统一封装。

---

## 7. File 文件

### 7.1 获取上传 URL

```
POST /files/upload-url
```

```json
{
  "name": "photo.jpg",
  "mimeType": "image/jpeg",
  "size": 102400,
  "uploaderId": 123456,
  "purpose": 1,
  "access": 2,
  "expiresIn": 3600
}
```

| 字段 | 说明 |
|---|---|
| `purpose` | 1=消息附件, 2=头像 |
| `access` | 1=私有, 2=会话内可见, 3=公开 |
| `expiresIn` | 上传 URL 有效期（秒），默认 3600 |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": 6660001,
    "uploadUrl": "https://minio.xx.com/aim/abc.jpg?X-Amz-...",
    "key": "aim/2024/02/abc.jpg",
    "expiresAt": 1707103600000
  }
}
```

**上传流程**：

1. 调本接口获取 `uploadUrl`
2. 客户端直接 `PUT` 文件到 `uploadUrl`（二进制，Content-Type 设为原始 mimeType）
3. 上传完成后调 `confirmUpload` 通知服务端

### 7.2 确认上传

```
POST /files/confirm
```

```json
{
  "fileId": 6660001,
  "uploaderId": 123456,
  "md5": "d41d8cd98f00b204e9800998ecf8427e"   // 可选
}
```

**Response**: `data` 返回完整 `FileInfo`。

### 7.3 获取下载 URL

```
GET /files/{fileId}/download?userId=123456&expiresIn=3600
```

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "downloadUrl": "https://minio.xx.com/aim/abc.jpg?X-Amz-...",
    "expiresAt": 1707107200000,
    "file": {
      "fileId": 6660001,
      "name": "photo.jpg",
      "key": "aim/2024/02/abc.jpg",
      "size": 102400,
      "mimeType": "image/jpeg",
      "ext": "jpg",
      "width": 800,
      "height": 600,
      "duration": 0,
      "md5": "",
      "purpose": 1,
      "access": 2,
      "uploaderId": 123456,
      "bucket": "aim",
      "createdAt": 1707100000000
    }
  }
}
```

### 7.4 获取文件信息

```
GET /files/{fileId}/info?userId=123456
```

### 7.5 删除文件

```
DELETE /files/{fileId}
```

```json
{
  "fileId": 6660001,
  "userId": 123456
}
```

---

## 8. Notification 通知

### 8.1 通知列表

```
GET /notifications?pageNum=1&pageSize=20&type=0&isRead=false
```

| 参数 | 说明 |
|---|---|
| `type` | 可选，1=系统, 2=审核, 3=Bot |
| `isRead` | 可选，筛选已读/未读 |

**Response**:

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 7770001,
        "userId": 123456,
        "type": 1,
        "title": "新好友申请",
        "content": "张三申请添加你为好友",
        "isRead": false,
        "referenceId": "987654321",
        "createdAt": 1707100000000
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

### 8.2 未读通知数

```
GET /notifications/unread-count
```

**Response**: `{"code":0,"message":"success","data":{"count":5}}`

### 8.3 标记已读

```
POST /notifications/{notificationId}/read
```

```json
{
  "notificationId": 7770001,
  "userId": 123456
}
```

### 8.4 全部已读

```
POST /notifications/read-all
```

```json
{ "userId": 123456 }
```

### 8.5 删除通知

```
DELETE /notifications/{notificationId}
```

---

## 9. 错误码参考

### 通用错误

| code | message |
|---|---|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### user-service (10xxx)

| code | message |
|---|---|
| 10001 | 用户不存在 |
| 10002 | 用户名已存在 |
| 10003 | 手机号已被注册 |
| 10004 | 密码错误 |
| 10005 | Token 无效或已过期 |
| 10006 | Token 已过期 |
| 10007 | 会话不存在 |
| 10008 | 用户被禁用 |

### friend-service (20xxx)

| code | message |
|---|---|
| 20001 | 已经是好友 |
| 20002 | 非好友关系 |
| 20003 | 好友申请已存在 |
| 20004 | 好友申请不存在 |
| 20005 | 申请已处理 |
| 20006 | 已被对方拉黑 |
| 20007 | 已拉黑该用户 |
| 20008 | 未拉黑该用户 |

### conv-service (30xxx)

| code | message |
|---|---|
| 30001 | 会话不存在 |
| 30002 | 用户已是会话成员 |
| 30003 | 用户不在会话中 |
| 30004 | 非会话成员 |
| 30005 | 权限不足 |
| 30006 | 已被禁言 |
| 30007 | 全员禁言中 |
| 30008 | 成员数超上限 (500) |
| 30009 | 不能转让给自己 |

### message-service (40xxx)

| code | message |
|---|---|
| 40001 | 消息不存在 |
| 40002 | 撤回超时 (120s) |
| 40003 | 编辑超时 (120s) |
| 40004 | 重复消息 |
| 40005 | 非消息发送者 |
| 40006 | 消息已撤回 |
| 40007 | 序号生成失败 |

### file-service (50xxx)

| code | message |
|---|---|
| 50001 | 文件不存在 |
| 50002 | 文件上传失败 |
| 50003 | 文件过大 (100MB) |
| 50004 | 不支持的文件类型 |

### signaling-service (60xxx)

| code | message |
|---|---|
| 60001 | 通知不存在 |

---

## 10. WebSocket 实时通信

### 10.1 连接

```
ws://{host}:8081/ws?token=<access_token>&device_id=<deviceId>
```

握手时校验 JWT Token，失败则拒绝连接。

### 10.2 消息帧格式

所有帧为 JSON 文本帧，固定结构：

```json
{
  "event": "message.new",
  "data": { ... },
  "timestamp": 1707100000000
}
```

### 10.3 客户端 → 服务端（上行）

| event | 说明 | data |
|---|---|---|
| `ping` | 心跳，每 30 秒发一次 | `{}` |
| `subscribe_presence` | 订阅指定用户在线状态 | `{ "userIds": [789012] }` |
| `unsubscribe_presence` | 取消订阅 | `{ "userIds": [789012] }` |
| `typing` | 正在输入 | `{ "convId": 5550001, "userId": 123456 }` |
| `typing_stop` | 停止输入 | `{ "convId": 5550001, "userId": 123456 }` |
| `ack` | 消息已读回执（客户端确认收到） | `{ "messageId": 8880001, "convId": 5550001, "seq": 11 }` |

### 10.4 服务端 → 客户端（下行）

| event | 说明 | data 结构 |
|---|---|---|
| `pong` | 心跳回复 | `{}` |
| `message.new` | 新消息 | 见下方 |
| `message.recalled` | 消息被撤回 | `{ "messageId": 8880001, "convId": 5550001, "userId": 123456 }` |
| `message.edited` | 消息被编辑 | `{ "messageId": 8880001, "convId": 5550001, "userId": 123456, "newContent": {...} }` |
| `presence` | 在线状态变更 | `{ "userId": 789012, "status": "online" }` |
| `read_sync` | 已读状态同步 | `{ "convId": 5550001, "userId": 789012, "lastReadSeq": 11 }` |
| `typing` | 对方正在输入 | `{ "convId": 5550001, "userId": 789012 }` |
| `typing.stop` | 对方停止输入 | `{ "convId": 5550001, "userId": 789012 }` |
| `unread_count` | 未读计数更新 | `{ "convId": 5550001, "count": 3 }` |
| `read_receipt` | 已读回执 | `{ "messageId": 8880001, "userId": 789012, "readAt": 1707100001000 }` |

### 10.5 `message.new` 事件 data 结构

```json
{
  "messageId": 8880001,
  "convId": 5550001,
  "seq": 11,
  "fromUserId": 123456,
  "msgType": 1,
  "status": 1,
  "content": {
    "text": "Hello!",
    "mentionUserIds": [],
    "mentionAll": false
  },
  "replyToId": 0,
  "replyToPreview": "",
  "createdAt": 1707100000000,
  "unreadCount": 3,
  "senderInfo": {
    "id": 123456,
    "username": "zhangsan",
    "avatar": "https://..."
  }
}
```

> `unreadCount` 是当前用户在该会话的未读消息数，由 signaling-service 扇出时按人计算。

### 10.6 在线状态管理

- 客户端 WebSocket 连接成功后自动标记为 `online`
- 客户端断开连接（心跳超时 90 秒无 ping）自动标记为 `offline`
- 客户端可通过 `subscribe_presence` 订阅关注用户的在线状态变化
- 在线状态变更通过 `presence` 事件推送

---

## 附录 A：实体字段速查

### UserInfo

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 用户 ID |
| username | string | 用户名 |
| phone | string | 手机号（脱敏） |
| email | string | 邮箱（脱敏） |
| avatar | string | 头像 URL |
| gender | int | 0=未设置 1=男 2=女 |
| bio | string | 个人简介 |
| birthday | long | 生日（epoch ms） |
| createdAt | long | 注册时间（epoch ms） |
| updatedAt | long | 更新时间（epoch ms） |
| balance | double | 余额 |

### Message 常用 content 结构

**文本 (msgType=1)**:
```json
{ "text": "内容", "mentionUserIds": [123], "mentionAll": false }
```

**图片 (msgType=2)**:
```json
{ "fileId": 123, "url": "", "thumbnailUrl": "", "width": 800, "height": 600, "size": 102400, "format": "jpeg" }
```

**文件 (msgType=3)**:
```json
{ "fileId": 123, "url": "", "name": "文档.pdf", "size": 204800, "ext": "pdf", "mimeType": "application/pdf" }
```

**语音 (msgType=5)**:
```json
{ "fileId": 123, "url": "", "duration": 15, "size": 81920 }
```

**视频 (msgType=4)**:
```json
{ "fileId": 123, "url": "", "thumbnailUrl": "", "duration": 30, "width": 1920, "height": 1080, "size": 5242880 }
```

**位置 (msgType=6)**:
```json
{ "latitude": 39.9042, "longitude": 116.4074, "address": "详细地址", "name": "地点名称" }
```

**系统消息 (msgType=7)**:
```json
{ "action": "member.joined", "detail": "张三邀请李四加入群聊", "relatedUserIds": [333], "actorId": 123, "actorType": "user", "payload": "{}" }
```

---

## 附录 B：Phase 2 预留（本版不实现）

以下路由在 v1 中暂不提供，Phase 2 扩展：

- Bot 管理：`/bots/**`
- MCP Server：`/mcp-servers/**`
- Bot 绑定会话：`/convs/{id}/bots/**`
- AI 会话工具：`/convs/{id}/summarize`、`/convs/{id}/todos/**`、`/messages/{id}/translate`
- 知识库：`/knowledge/**`、`/wiki/**`
- LLM 模型：`/models/**`
- OAuth 登录：`/auth/oauth/**`
- Token 刷新：`/auth/refresh`
- OAuth/会话管理/余额：Phase 2 补充
- 广播消息：`/broadcasts/**`
