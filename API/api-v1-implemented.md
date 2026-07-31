# AIM API v1 实现态接口文档（Phase 1）

> **版本**：v1.0.0-impl（基于 user-service / conv-service / file-service 已实现代码生成）
> **Base URL**：`http://{host}:9080/api/v1`
> **Content-Type**：`application/json`
> **字符编码**：UTF-8
> **时间格式**：epoch 毫秒时间戳（`long`），部分内部字段为秒级（已标注）
> **大整数**：所有 `id` / `*Id` / `*Seq` 字段为 Java `long`，序列化为 JSON number，前端需用 `JSONbig` / `bigint` 解析避免精度丢失

---

## 0. 架构说明

### 0.1 双协议暴露

业务服务同时暴露两种协议：

| 协议 | 端口 | 用途 |
|---|---|---|
| HTTP | 见下表 | 供前端经 gateway 转发访问 |
| Dubbo | 20881-20883 | 供其他业务服务 RPC 调用 |

| 服务 | HTTP 端口 | Dubbo 端口 |
|---|---|---|
| gateway-service | 9080 | — |
| user-service | 8081 | 20881 |
| conv-service | 8082 | 20883 |
| file-service | 8083 | 20882 |

### 0.2 鉴权流程

```
前端 ── Authorization: Bearer <token> ──▶ gateway:9080
                                              │
                                              ▼
                                   JwtAuthGlobalFilter
                                   1. 白名单放行（register/login/refresh）
                                   2. 校验 JWT 签名 + 过期
                                   3. 查 Redis 黑名单（revoked_token:{jti}）
                                   4. 注入 X-User-Id header
                                              │
                                              ▼
                                    业务服务 Controller
                                    @RequestHeader("X-User-Id") long userId
```

- **白名单接口**（无需 Token）：`POST /auth/register`、`POST /auth/login`、`POST /auth/refresh`
- **鉴权接口**（必须带 Token）：其余所有接口
- **身份来源**：Controller 一律从 `X-User-Id` header 取当前用户 ID，**不接受 body/query 中的 userId**

### 0.3 Token 说明

- Access Token：JWT，有效期 2 小时，载荷含 `jti`/`userId`/`username`/`type`/`iss`/`iat`/`exp`
- Refresh Token：JWT，有效期 7 天，仅用于刷新 Access Token
- 登出时同时吊销两个 Token，写入 Redis 黑名单 `revoked_token:{jti}`，TTL = Token 剩余有效期

---

## 1. 通用约定

### 1.1 统一响应格式

所有接口返回 JSON，外层结构：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

- `code = 0` 表示成功；非 0 表示失败（见错误码表）
- 失败时 `data` 为 `null`
- 无返回数据的接口 `data` 为 `null`（如 logout、delete）

### 1.2 错误码段

| 段 | 服务 | 示例 |
|---|---|---|
| 0 | 通用成功 | 0 success |
| 400 | 通用 | 请求参数错误 |
| 401 | 通用 | 未认证（Token 无效/过期/已吊销） |
| 500 | 通用 | 服务器内部错误 |
| 10xxx | user-service | 10001 用户不存在 |
| 30xxx | conv-service | 30001 会话不存在 |
| 50xxx | file-service | 50001 文件不存在 |

### 1.3 全局异常处理

每个服务均注册 `@RestControllerAdvice`（`exception.GlobalExceptionHandler`），统一处理：

| 异常 | 响应 |
|---|---|
| `BizException` | `{ code: ex.code, message: ex.message }` |
| `MethodArgumentNotValidException` / `HttpMessageNotReadableException` | `{ code: 400, message: "bad request" }` |
| 其他 `Exception` | `{ code: 500, message: "internal error" }`（同时打 ERROR 日志） |

---

## 2. Auth 认证模块

> 路由：`gateway → user-service`，predicate `Path=/api/v1/auth/**`

### 2.1 注册

```
POST /api/v1/auth/register
```

**鉴权**：白名单，无需 Token

**Request Body**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 3~32 字符 |
| password | string | 是 | 6~32 字符 |
| phone | string | 否 | 手机号 |
| email | string | 否 | 邮箱 |
| deviceId | string | 是 | 设备唯一标识 |
| platform | string | 是 | `ios` / `android` / `web` |

```json
{
  "username": "zhangsan",
  "password": "Abc@123456",
  "phone": "13800138000",
  "email": "zhangsan@foo.com",
  "deviceId": "device-uuid",
  "platform": "web"
}
```

**Response `data`**（`RegisterResp`）：

```json
{
  "userId": 339394874048512000,
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessExpire": 1784993000000,
    "refreshExpire": 1785500000000
  },
  "user": {
    "id": 339394874048512000,
    "username": "zhangsan",
    "phone": "13800138000",
    "email": "zhangsan@foo.com",
    "avatar": "",
    "gender": 0,
    "bio": "",
    "birthday": 0,
    "createdAt": 1784986000000,
    "updatedAt": 1784986000000,
    "balance": 0
  }
}
```

**错误码**：`10002 用户名已存在`、`10003 手机号已被注册`

### 2.2 登录

```
POST /api/v1/auth/login
```

**鉴权**：白名单，无需 Token

**Request Body**（`LoginReq`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| account | string | 是 | 用户名 / 手机号 / 邮箱 |
| password | string | 是 | 密码 |
| deviceId | string | 是 | 设备唯一标识 |
| platform | string | 是 | `ios` / `android` / `web` |

```json
{
  "account": "zhangsan",
  "password": "Abc@123456",
  "deviceId": "device-uuid",
  "platform": "web"
}
```

**Response `data`**（`LoginResp`）：结构同注册返回

**错误码**：`10001 用户不存在`、`10004 密码错误`、`10008 用户被禁用`

### 2.3 登出

```
POST /api/v1/auth/logout
```

**鉴权**：需 Token（`Authorization: Bearer <accessToken>`）

**Request Body**（`LogoutReq`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| refreshToken | string | 是 | 待吊销的 refreshToken（accessToken 从 Header 提取） |

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

> ⚠️ `accessToken` 字段虽在 DTO 中存在，但 Controller 会用 Header 中的 Token 覆盖，前端**只需传 `refreshToken`**。

**Response**：

```json
{ "code": 0, "message": "success", "data": null }
```

**副作用**：将 access / refresh 两个 Token 的 `jti` 写入 Redis 黑名单 `revoked_token:{jti}`，TTL = Token 剩余有效期

### 2.4 Token 校验

```
GET /api/v1/auth/validate
```

**鉴权**：需 Token

**Response `data`**（`ValidateTokenResp`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| valid | boolean | 是否有效 |
| userId | long | 用户 ID |
| expiresAt | long | Token 过期时间（epoch 毫秒） |

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "valid": true,
    "userId": 339394874048512000,
    "expiresAt": 1784993000000
  }
}
```

### 2.5 刷新 Token

```
POST /api/v1/auth/refresh
```

**鉴权**：白名单，无需 Token

**Request Body**（`RefreshTokenReq`）：

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response `data`**（`RefreshTokenResp`）：仅返回新的 accessToken，refreshToken 不变

| 字段 | 类型 | 说明 |
|---|---|---|
| accessToken | string | 新的访问 Token |
| accessExpire | long | 过期时间（epoch 毫秒） |

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessExpire": 1784993000000
  }
}
```

**错误码**：`10005 Token 无效或已过期`、`10006 Token 已过期`

---

## 3. User 用户模块

> 路由：`gateway → user-service`，predicate `Path=/api/v1/users/**`
> 所有接口均需鉴权，从 `X-User-Id` header 取当前用户

### 3.1 获取当前用户资料

```
GET /api/v1/users/me
```

**Response `data`**：`UserInfo`（见附录 A）

### 3.2 更新用户资料

```
PUT /api/v1/users/me
```

**Request Body**（`UpdateProfileReq`，所有字段可选，传什么更新什么）：

| 字段 | 类型 | 说明 |
|---|---|---|
| avatar | string | 头像 URL |
| gender | int | 0=未设置 1=男 2=女 |
| bio | string | 个人简介 |
| birthday | long | 生日（epoch 毫秒） |
| phone | string | 手机号 |
| email | string | 邮箱 |

```json
{
  "avatar": "https://new-avatar.jpg",
  "gender": 1,
  "bio": "新签名",
  "birthday": 946656000000
}
```

**Response `data`**：更新后的 `UserInfo`

### 3.3 修改密码

```
PUT /api/v1/users/me/password
```

**Request Body**（`UpdatePasswordReq`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| oldPassword | string | 是 | 旧密码 |
| newPassword | string | 是 | 新密码（6~32 字符） |

```json
{
  "oldPassword": "Abc@123456",
  "newPassword": "Xyz@654321"
}
```

**Response**：`{ "code": 0, "message": "success", "data": null }`

**错误码**：`10004 密码错误`

### 3.4 获取指定用户信息

```
GET /api/v1/users/{userId}
```

| Path 参数 | 类型 | 说明 |
|---|---|---|
| userId | long | 目标用户 ID |

**Response `data`**：`UserInfo`

**错误码**：`10001 用户不存在`

### 3.5 批量获取用户信息

```
POST /api/v1/users/batch
```

**Request Body**：直接是 `long[]` 数组（**不是对象包裹**）

```json
[123456789, 789012, 345678]
```

**Response `data`**（`BatchGetUserInfoResp`）：

```json
{
  "users": [
    { "...UserInfo" },
    { "...UserInfo" }
  ]
}
```

### 3.6 搜索用户

```
POST /api/v1/users/search?keyword={kw}&pageNum={n}&pageSize={s}
```

| Query 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| keyword | string | 是 | — | 关键词（匹配用户名/手机号/邮箱） |
| pageNum | int | 否 | 1 | 页码（从 1 开始） |
| pageSize | int | 否 | 20 | 每页数量 |

> ⚠️ 用 **query 参数**而非 body

**Response `data`**（`SearchUsersResp`）：

```json
{
  "users": [ { "...UserInfo" } ],
  "total": 100
}
```

---

## 4. Conversation 会话模块

> 路由：`gateway → conv-service`，predicate `Path=/api/v1/convs/**`
> 所有接口均需鉴权，从 `X-User-Id` header 取当前用户（作为 operator/creator）

### 4.1 创建会话

```
POST /api/v1/convs
```

**Request Body**（`CreateConversationReq`，`creatorId` 由服务端从 `X-User-Id` 覆盖，前端可不传）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| type | int | 是 | 1=单聊 2=群聊 |
| peerUserId | long | 单聊必填 | 单聊对方用户 ID |
| name | string | 群聊必填 | 群名称 |
| avatar | string | 否 | 群头像 URL |
| memberIds | long[] | 否 | 群聊初始成员（不含创建者） |

**单聊示例**：

```json
{
  "type": 1,
  "peerUserId": 789012
}
```

**群聊示例**：

```json
{
  "type": 2,
  "name": "技术讨论组",
  "avatar": "",
  "memberIds": [789012, 345678]
}
```

**Response `data`**（`CreateConversationResp`）：

```json
{
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
    "updatedAt": 1707100000000,
    "unreadCount": 0
  }
}
```

**错误码**：`30008 成员数超上限 (500)`

### 4.2 获取会话详情

```
GET /api/v1/convs/{conversationId}
```

**Response `data`**：`ConversationDTO`（见附录 B）

**错误码**：`30001 会话不存在`、`30004 非会话成员`

### 4.3 会话列表（传统分页）

```
GET /api/v1/convs?pageNum={n}&pageSize={s}
```

| Query 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| pageNum | int | 1 | 页码 |
| pageSize | int | 20 | 每页数量 |

> ⚠️ 使用**传统分页**而非游标分页

**Response `data`**（`ListConversationsResp`）：

```json
{
  "conversations": [
    { "...ConversationDTO" }
  ],
  "total": 1
}
```

### 4.4 成员列表

```
GET /api/v1/convs/{conversationId}/members?pageNum={n}&pageSize={s}
```

| Query 参数 | 默认 |
|---|---|
| pageNum | 1 |
| pageSize | 50 |

**Response `data`**（`GetMembersResp`）：

```json
{
  "members": [
    {
      "userId": 123456,
      "username": "zhangsan",
      "avatar": "https://...",
      "role": 1,
      "alias": "",
      "joinedAt": 1707100000000,
      "lastReadSeq": 10,
      "isMuted": false,
      "muteUntil": 0,
      "memberType": 1,
      "botId": 0
    }
  ],
  "total": 1
}
```

### 4.5 邀请成员

```
POST /api/v1/convs/{conversationId}/members/invite
```

**Request Body**（`InviteBody`，conversationId 取自 path，operatorId 取自 `X-User-Id`）：

```json
{
  "userIds": [333333, 444444]
}
```

**Response `data`**（`AddMembersResp`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| addedUserIds | long[] | 成功添加的用户 ID |
| alreadyMemberIds | long[] | 已是成员的用户 ID（**非 failedUserIds**） |

```json
{
  "addedUserIds": [333333, 444444],
  "alreadyMemberIds": []
}
```

**错误码**：`30005 权限不足`、`30008 成员数超上限 (500)`

### 4.6 移除成员

```
POST /api/v1/convs/{conversationId}/members/kick
```

**Request Body**（`KickBody`）：

```json
{
  "userIds": [789012]
}
```

**Response**：`{ "code": 0, "message": "success", "data": null }`

**错误码**：`30003 用户不在会话中`、`30005 权限不足`

### 4.7 禁言成员

```
PUT /api/v1/convs/{conversationId}/members/{userId}/mute
```

**Request Body**（`MuteBody`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| durationSeconds | long | 禁言时长（秒），0=永久 |

```json
{ "durationSeconds": 3600 }
```

> 内部转换：`muteUntil = now(秒) + durationSeconds`，0 时为永久（muteUntil=0）

**Response**：`{ "code": 0, "message": "success", "data": null }`

**错误码**：`30003 用户不在会话中`、`30005 权限不足`

### 4.8 解除禁言

```
DELETE /api/v1/convs/{conversationId}/members/{userId}/mute
```

**Request Body**：无

> 等价于 `muteUntil=0` 的禁言操作

**Response**：`{ "code": 0, "message": "success", "data": null }`

### 4.9 转让群主

```
POST /api/v1/convs/{conversationId}/transfer
```

**Request Body**（`TransferBody`）：

```json
{ "newOwnerId": 789012 }
```

**Response**：`{ "code": 0, "message": "success", "data": null }`

**错误码**：`30003 用户不在会话中`、`30005 权限不足`、`30009 不能转让给自己`

### 4.10 设置群公告

```
PUT /api/v1/convs/{conversationId}/announcement
```

**Request Body**（`AnnouncementBody`）：

```json
{ "content": "本周五团建" }
```

**Response**：`{ "code": 0, "message": "success", "data": null }`

### 4.11 删除群公告

```
DELETE /api/v1/convs/{conversationId}/announcement
```

**Request Body**：无（等价于 `content=""` 的设置）

**Response**：`{ "code": 0, "message": "success", "data": null }`

### 4.12 获取会话设置

```
GET /api/v1/convs/{conversationId}/settings
```

**Response `data`**（`GetSettingsResp`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| isMuted | boolean | 当前用户是否对该会话设置免打扰 |
| isPinned | boolean | 当前用户是否对该会话置顶 |
| nickname | string | 当前用户在该群的昵称 |

```json
{
  "isMuted": false,
  "isPinned": false,
  "nickname": ""
}
```

### 4.13 更新会话设置

```
PUT /api/v1/convs/{conversationId}/settings
```

**Request Body**（`UpdateSettingsReq`，所有字段可选）：

| 字段 | 类型 | 说明 |
|---|---|---|
| isMuted | boolean | 免打扰 |
| isPinned | boolean | 置顶 |
| nickname | string | 群昵称 |

```json
{
  "isMuted": true,
  "isPinned": true,
  "nickname": "群里的张三"
}
```

**Response**：`{ "code": 0, "message": "success", "data": null }`

### 4.14 标记已读

```
PUT /api/v1/convs/{conversationId}/read
```

**Request Body**（`MarkReadBody`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| seq | long | 已读到第几条消息的 seq |

```json
{ "seq": 10 }
```

> 内部映射：`seq → lastReadSeq`

**Response**：`{ "code": 0, "message": "success", "data": null }`

---

## 5. File 文件模块

> 路由：`gateway → file-service`，predicate `Path=/api/v1/files/**`
> 所有接口均需鉴权，从 `X-User-Id` header 取当前用户（作为 uploader/userId）

### 5.1 获取上传 URL

```
POST /api/v1/files/upload-url
```

**Request Body**（`GetUploadURLReq`，`uploaderId` 由服务端从 `X-User-Id` 覆盖）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | string | 是 | 文件名（用于推断 ext） |
| mimeType | string | 是 | MIME 类型，如 `image/jpeg` |
| size | long | 是 | 文件大小（字节） |
| purpose | int | 是 | 1=消息附件 2=头像 |
| access | int | 是 | 1=私有 2=会话内可见 3=公开 |
| expiresIn | int | 否 | 上传 URL 有效期（秒），默认 3600 |

```json
{
  "name": "photo.jpg",
  "mimeType": "image/jpeg",
  "size": 102400,
  "purpose": 1,
  "access": 2,
  "expiresIn": 3600
}
```

**Response `data`**（`GetUploadURLResp`）：

```json
{
  "fileId": 6660001,
  "uploadUrl": "https://minio.xx.com/aim/abc.jpg?X-Amz-...",
  "key": "aim/2024/02/abc.jpg",
  "expiresAt": 1707103600000
}
```

**上传流程**：

1. 调本接口获取 `uploadUrl`
2. 客户端直接 `PUT` 文件到 `uploadUrl`（二进制，Content-Type 设为原始 mimeType）
3. 上传完成后调 `confirmUpload` 通知服务端

**错误码**：`50003 文件过大 (100MB)`、`50004 不支持的文件类型`

### 5.2 确认上传

```
POST /api/v1/files/confirm
```

**Request Body**（`ConfirmUploadReq`，`uploaderId` 由服务端覆盖）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| fileId | long | 是 | 上传 URL 接口返回的 fileId |
| md5 | string | 否 | 文件 MD5（可选校验） |

```json
{
  "fileId": 6660001,
  "md5": "d41d8cd98f00b204e9800998ecf8427e"
}
```

**Response `data`**（`ConfirmUploadResp`）：

```json
{
  "file": { "...FileInfo" }
}
```

**错误码**：`50001 文件不存在`、`50002 文件上传失败`

### 5.3 获取下载 URL

```
GET /api/v1/files/{fileId}/download?expiresIn={seconds}
```

| Path 参数 | 类型 | 说明 |
|---|---|---|
| fileId | long | 文件 ID |

| Query 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| expiresIn | int | 3600 | 下载 URL 有效期（秒） |

> userId 从 `X-User-Id` header 取（**不在 query 中传 userId**）

**Response `data`**（`GetDownloadURLResp`）：

```json
{
  "downloadUrl": "https://minio.xx.com/aim/abc.jpg?X-Amz-...",
  "expiresAt": 1707107200000,
  "file": { "...FileInfo" }
}
```

**错误码**：`50001 文件不存在`

### 5.4 获取文件信息

```
GET /api/v1/files/{fileId}/info
```

**Response `data`**：`FileInfo`（见附录 C）

### 5.5 删除文件

```
DELETE /api/v1/files/{fileId}
```

**Request Body**：无（fileId 取自 path，userId 取自 `X-User-Id` header）

**Response**：`{ "code": 0, "message": "success", "data": null }`

**错误码**：`50001 文件不存在`

### 5.6 批量获取文件信息

```
POST /api/v1/files/batch
```

**Request Body**：直接是 `long[]` 数组

```json
[6660001, 6660002, 6660003]
```

**Response `data`**：`List<FileInfo>`

```json
[
  { "...FileInfo" },
  { "...FileInfo" }
]
```

---

## 6. 错误码参考

### 6.1 通用错误

| code | message |
|---|---|
| 0 | success |
| 400 | bad request |
| 401 | unauthorized |
| 500 | internal error |

### 6.2 user-service (10xxx)

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

### 6.3 conv-service (30xxx)

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

### 6.4 file-service (50xxx)

| code | message |
|---|---|
| 50001 | 文件不存在 |
| 50002 | 文件上传失败 |
| 50003 | 文件过大 (100MB) |
| 50004 | 不支持的文件类型 |

---

## 附录 A：UserInfo

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 用户 ID |
| username | string | 用户名 |
| phone | string | 手机号 |
| email | string | 邮箱 |
| avatar | string | 头像 URL |
| gender | int | 0=未设置 1=男 2=女 |
| bio | string | 个人简介 |
| birthday | long | 生日（epoch 毫秒） |
| createdAt | long | 注册时间（epoch 毫秒） |
| updatedAt | long | 更新时间（epoch 毫秒） |
| balance | BigDecimal | 余额（序列化为 plain string，避免科学计数法） |

## 附录 B：ConversationDTO

| 字段 | 类型 | 说明 |
|---|---|---|
| id | long | 会话 ID |
| type | int | 1=单聊 2=群聊 |
| name | string | 会话名称 |
| avatar | string | 会话头像 |
| ownerId | long | 群主用户 ID（单聊为 0） |
| memberCount | int | 成员数 |
| maxSeq | long | 当前最大消息 seq |
| lastMessageId | long | 最新消息 ID |
| lastMessagePreview | string | 最新消息预览 |
| announcement | string | 群公告 |
| isMutedAll | boolean | 是否全员禁言 |
| createdAt | long | 创建时间（epoch 毫秒） |
| updatedAt | long | 更新时间（epoch 毫秒） |
| unreadCount | long | 当前用户未读数（由 conv-service 查 Redis 填充） |

## 附录 C：FileInfo

| 字段 | 类型 | 说明 |
|---|---|---|
| fileId | long | 文件 ID |
| name | string | 文件名 |
| key | string | 对象存储 key |
| size | long | 文件大小（字节） |
| mimeType | string | MIME 类型 |
| ext | string | 扩展名（不含点） |
| width | int | 图片/视频宽度（非媒体为 0） |
| height | int | 图片/视频高度（非媒体为 0） |
| duration | int | 音视频时长（秒，非媒体为 0） |
| md5 | string | MD5（confirmUpload 时传则填） |
| purpose | int | 1=消息附件 2=头像 |
| access | int | 1=私有 2=会话内可见 3=公开 |
| uploaderId | long | 上传者用户 ID |
| bucket | string | 对象存储 bucket 名 |
| status | int | 0=PENDING 1=CONFIRMED 2=DELETED |
| createdAt | long | 创建时间（epoch 毫秒） |

## 附录 D：ConversationMemberDTO

| 字段 | 类型 | 说明 |
|---|---|---|
| userId | long | 用户 ID |
| username | string | 用户名 |
| avatar | string | 头像 URL |
| role | int | 0=Member 1=Owner 2=Admin |
| alias | string | 群昵称 |
| joinedAt | long | 加入时间（epoch 毫秒） |
| lastReadSeq | long | 该用户最后已读 seq |
| isMuted | boolean | 是否被禁言 |
| muteUntil | long | 禁言截止时间（epoch **秒**，0=未禁言或永久禁言） |
| memberType | int | 1=user 2=bot（Phase 2） |
| botId | long | bot ID（非 bot 为 0） |

---

## 附录 E：实现态与原 api-v1.md 主要差异

> 详见 `backend/docs/API/api-v1-diff.md`（如有）。简要：

1. **Base URL 端口**：`8080 → 9080`（避让 nacos 3.x console）
2. **logout body**：`{ userId, tokenId } → { refreshToken }`（accessToken 取自 Header）
3. **validate response**：移除 `deviceId`
4. **新增 `/auth/refresh`**：从 Phase 2 预留移入已实现
5. **users/batch**：`{ userIds: [...] } → [...]`（直接数组）
6. **users/search**：body → query 参数
7. **search response**：`{ list, pageNum, pageSize } → { users, total }`
8. **convs 列表**：游标分页 → 传统分页 `pageNum/pageSize`
9. **ConversationDTO**：移除 `lastReadSeq/isMuted/isPinned/background`（这些在 settings 接口）
10. **conv 多接口**：body 不再传 `conversationId/operatorId/userId`（取自 path/header）
11. **invite response**：`failedUserIds → alreadyMemberIds`
12. **file userId**：统一从 `X-User-Id` header 取，不在 query/body 传
13. **file delete**：DELETE 无 body
14. **新增 `/files/batch`**：原 api-v1.md 未列出
15. **settings response**：新增 `nickname` 字段
