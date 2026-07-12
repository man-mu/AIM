# AIM 前端架构设计

本文档定义 AIM Phase 1 Web 客户端的前端技术选型、目录结构、状态边界和接口接入方式。目标是支撑核心 IM 能力：登录注册、会话列表、消息收发、好友/分组、通知、文件上传、WebSocket 实时事件和断线补偿同步。

## 1. 技术选型

### 1.1 核心栈

| 领域 | 选型 | 说明 |
|---|---|---|
| 包管理 | `pnpm` | 锁定依赖、安装快、适合后续 monorepo 化 |
| 构建工具 | `Vite` | 当前脚手架已使用；开发反馈快，和 Vitest/Tailwind 集成直接 |
| 语言 | `TypeScript` | API DTO、WebSocket 事件、Query Key、Store Slice 全部强类型 |
| UI 框架 | `React` | 当前脚手架已使用；适合组件化 IM 工作台 |
| 路由 | `@tanstack/react-router` | 类型安全路由、嵌套路由、路由级懒加载和 loader 能力 |
| 服务端状态 | `@tanstack/react-query` | REST 数据缓存、分页、Mutation、失效刷新、重连后的同步补偿 |
| 本地状态 | `zustand` | 管理会话选择、WebSocket 连接状态、草稿、临时 UI 状态 |
| 样式 | `tailwindcss` + `@tailwindcss/vite` | 工具类样式，适合快速落地 Figma 初稿；使用 Vite 插件集成 |

### 1.2 建议补充

| 领域 | 建议 | 用途 |
|---|---|---|
| 表单 | `react-hook-form` + `zod` | 登录、注册、资料编辑、群设置表单；Zod 负责运行时校验和类型推导 |
| 组件基础 | `@radix-ui/react-*` | Dialog、Dropdown、Tooltip、Popover、Tabs 等无样式可访问性组件 |
| 样式工具 | `class-variance-authority`、`clsx`、`tailwind-merge` | Button、Input、Badge、MessageBubble 等组件变体管理 |
| 图标 | `lucide-react` | 导航、工具栏、消息操作、文件类型图标 |
| 虚拟列表 | `@tanstack/react-virtual` | 长会话列表、长消息历史、联系人列表性能优化 |
| 日期时间 | `dayjs` 或 `date-fns` | epoch 毫秒时间戳格式化；优先封装在 `shared/lib/datetime` |
| 请求模拟 | `msw` | 在后端接口不完整时做浏览器和测试环境的 API mock |
| 单元/组件测试 | `vitest` + `@testing-library/react` | hooks、store、组件状态和 Query 行为验证 |
| E2E 测试 | `playwright` | 登录、发消息、好友申请、文件上传等关键路径 |
| 质量工具 | `prettier`、`lint-staged`、`husky`、`commitlint` | 统一格式、提交前检查、Conventional Commits 约束 |

暂不建议引入重型 UI 框架或 Redux Toolkit。AIM 的复杂度主要来自服务端数据、实时事件和本地交互状态，`TanStack Query + Zustand` 的边界更清晰。

## 2. 架构原则

1. **服务端数据不进 Zustand**：用户信息、好友列表、会话列表、消息分页、通知列表由 TanStack Query 管理。
2. **Zustand 只放客户端事实**：当前选中会话、WebSocket 状态、输入草稿、UI 偏好、乐观发送队列。
3. **WebSocket 事件只做派发**：实时事件进入统一 dispatcher，再更新 Query Cache 或 Zustand Slice。
4. **API 统一解包 `Result<T>`**：组件不直接处理 `{ code, message, data }` 外壳。
5. **接口 DTO 与 UI Model 分离**：后端字段保持原样，页面需要的派生字段在 mapper 层处理。
6. **按业务域组织代码**：auth、conversation、message、friend、notification、file、user 独立维护 API、hooks、types 和 UI。
7. **Phase 2 预留但不实现**：不引入 Bot/AI/知识库相关前端模块，避免污染 Phase 1 结构。

## 3. 目录结构

```text
frontend/
├── public/
├── src/
│   ├── app/
│   │   ├── App.tsx
│   │   ├── providers/
│   │   │   ├── AppProviders.tsx
│   │   │   ├── QueryProvider.tsx
│   │   │   └── RouterProvider.tsx
│   │   ├── router/
│   │   │   ├── routeTree.gen.ts
│   │   │   └── routes/
│   │   └── styles/
│   │       └── index.css
│   ├── pages/
│   │   ├── auth/
│   │   ├── workspace/
│   │   ├── contacts/
│   │   └── settings/
│   ├── features/
│   │   ├── auth/
│   │   ├── conversation/
│   │   ├── message/
│   │   ├── friend/
│   │   ├── notification/
│   │   ├── file/
│   │   └── realtime/
│   ├── entities/
│   │   ├── user/
│   │   ├── conversation/
│   │   ├── message/
│   │   └── friend/
│   ├── shared/
│   │   ├── api/
│   │   │   ├── client.ts
│   │   │   ├── errors.ts
│   │   │   ├── result.ts
│   │   │   └── queryKeys.ts
│   │   ├── config/
│   │   ├── lib/
│   │   ├── store/
│   │   ├── ui/
│   │   └── types/
│   ├── test/
│   │   ├── mocks/
│   │   └── setup.ts
│   └── main.tsx
├── e2e/
├── package.json
└── vite.config.ts
```

业务模块推荐内部结构：

```text
features/message/
├── api/
│   └── messageApi.ts
├── model/
│   ├── message.types.ts
│   ├── message.schemas.ts
│   ├── message.mappers.ts
│   └── message.queries.ts
├── ui/
│   ├── MessageList.tsx
│   ├── MessageBubble.tsx
│   └── MessageComposer.tsx
└── index.ts
```

## 4. 路由设计

```text
/login
/register
/app
/app/chats
/app/chats/$conversationId
/app/contacts
/app/contacts/requests
/app/notifications
/app/settings/profile
```

路由约束：

- 登录态路由统一挂在 `/app` layout 下。
- `/login`、`/register` 进入前检查已有 token，有效则跳转 `/app/chats`。
- `/app/chats/$conversationId` 的 `conversationId` 来自 URL，便于刷新恢复和分享定位。
- 会话筛选、消息搜索、通知筛选使用 URL Search Params，避免刷新丢状态。

## 5. API 接入层

所有 REST 请求经过 `shared/api/client.ts`：

```ts
type ApiResult<T> = {
  code: number
  message: string
  data: T | null
}
```

请求层职责：

- 自动拼接 `VITE_API_BASE_URL=/api/v1`。
- 登录后注入 `Authorization: Bearer <access_token>`。
- 统一解析 `Result<T>`，`code !== 0` 时抛出 `ApiError`。
- 按错误码段映射业务域：`10xxx=user`、`20xxx=friend`、`30xxx=conversation`、`40xxx=message`、`50xxx=file`、`60xxx=notification/signaling`。
- 遇到 `401` 或 `10005/10006`，清理会话并跳转登录。
- 支持 `AbortSignal`，让 Query 在路由切换时可以取消请求。

文件上传按后端流程拆三步：

1. `POST /files/upload-url` 获取预签名 URL。
2. 使用 `XMLHttpRequest` 或带进度能力的请求工具 `PUT` 二进制文件。
3. `POST /files/confirm` 确认上传，再把 `FileInfo` 作为消息附件发送。

## 6. 状态边界

### 6.1 TanStack Query 管理

- `useCurrentUserQuery`
- `useFriendListQuery`
- `useFriendRequestsQuery`
- `useConversationListInfiniteQuery`
- `useConversationDetailQuery`
- `useMessagesInfiniteQuery`
- `useNotificationsQuery`
- `useUnreadNotificationCountQuery`
- `useFileInfoQuery`

Query Key 约定：

```ts
const queryKeys = {
  me: ['user', 'me'],
  conversations: {
    list: (filter) => ['conversations', 'list', filter],
    detail: (id) => ['conversations', 'detail', id],
  },
  messages: {
    pages: (conversationId, filter) => ['messages', conversationId, 'pages', filter],
    detail: (messageId) => ['messages', 'detail', messageId],
  },
}
```

### 6.2 Zustand 管理

建议拆成 slices：

- `authSlice`：accessToken、userId、deviceId、登录状态。当前 v1 文档没有开放 refresh 接口，`401` 后直接登出。
- `workspaceSlice`：当前侧边栏 tab、当前选中会话、右侧详情面板开关。
- `composerSlice`：每个 `conversationId` 的草稿、引用消息、上传中附件。
- `realtimeSlice`：WebSocket 连接状态、重连次数、最后心跳时间、订阅的 presence 用户。
- `uiSlice`：主题、紧凑模式、全局 toast/dialog 状态。

需要持久化的状态仅限 token、deviceId、轻量 UI 偏好和草稿。消息历史、好友列表、通知列表不持久化到 Zustand。

## 7. WebSocket 实时架构

连接地址：

```text
ws://{host}:8081/ws?token=<access_token>&device_id=<deviceId>
```

模块划分：

```text
features/realtime/
├── client/wsClient.ts        # 连接、心跳、重连、send
├── model/wsEvents.ts         # 上下行 event 类型
├── model/wsDispatcher.ts     # 事件派发到 Query/Zustand
└── hooks/useRealtime.ts      # 登录态下启动/关闭连接
```

处理规则：

- 登录成功后连接 WebSocket，登出时关闭连接。
- 每 30 秒发送 `ping`，90 秒无响应视为断线。
- 断线后指数退避重连；重连成功后调用 `/messages/{conversationId}/sync?fromSeq=...` 补拉遗漏消息。
- `message.new`：写入当前会话消息缓存；如果不是当前会话，更新会话列表未读数。
- `message.recalled` / `message.edited`：更新对应消息缓存。
- `presence`：更新 presence slice，不放进 Query。
- `typing` / `typing.stop`：短时 UI 状态，使用过期时间自动清理。
- `read_sync` / `read_receipt`：更新会话 `lastReadSeq` 和消息已读状态。

## 8. 页面与组件拆分

### 8.1 主工作台

```text
WorkspaceLayout
├── AppRail
├── ConversationSidebar
│   ├── ConversationSearch
│   ├── ConversationFilterTabs
│   └── ConversationList
├── ChatPanel
│   ├── ChatHeader
│   ├── MessageList
│   └── MessageComposer
└── ConversationDetailPanel
    ├── GroupSummary
    ├── ConversationSettings
    ├── MemberList
    └── PermissionActions
```

### 8.2 好友与通知

```text
ContactsPage
├── ContactNav
├── FriendList
├── FriendRequestTabs
├── FriendGroupManager
├── BlacklistPanel
└── NotificationPanel
```

### 8.3 登录与资料

```text
AuthLayout
├── LoginForm
├── RegisterForm
└── ProfileForm
```

组件策略：

- `shared/ui` 只放通用无业务组件：`Button`、`Input`、`Badge`、`Avatar`、`Dialog`、`Tabs`、`Spinner`。
- 业务组件放各自 `features/*/ui` 下，不把业务字段塞进通用组件。
- 消息类型组件按 `msgType` 拆分：`TextMessage`、`ImageMessage`、`FileMessage`、`VoiceMessage`、`VideoMessage`、`LocationMessage`、`SystemMessage`。

## 9. 数据类型与校验

接口 DTO 先按文档手写，后续如后端提供 OpenAPI，再切到代码生成。

建议规则：

- `*.types.ts`：接口 DTO 和 UI Model 类型。
- `*.schemas.ts`：Zod schema，校验登录、注册、发消息、资料更新等入参。
- `*.mappers.ts`：DTO → UI Model，比如时间格式、消息预览、在线状态文案。
- `*.queries.ts`：Query hooks 和 mutation hooks。

Long ID 统一用 `string` 承接，避免 JavaScript `number` 精度问题。接口文档中 `userId`、`conversationId`、`messageId` 都可能超过安全整数范围，前端展示和传参不做数值运算。

## 10. 工程化规范

建议脚本：

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview",
    "lint": "eslint .",
    "format": "prettier --write .",
    "typecheck": "tsc -b --noEmit",
    "test": "vitest run",
    "test:watch": "vitest",
    "e2e": "playwright test"
  }
}
```

环境变量：

```text
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=ws://localhost:8081/ws
VITE_APP_ENV=local
```

CI 检查顺序：

1. `pnpm install --frozen-lockfile`
2. `pnpm lint`
3. `pnpm typecheck`
4. `pnpm test`
5. `pnpm build`
6. `pnpm e2e`（可在后端和基础设施可用时启用）

## 11. 测试策略

| 层级 | 工具 | 覆盖目标 |
|---|---|---|
| 单元测试 | Vitest | mapper、query key、错误码映射、Zustand slice |
| 组件测试 | Vitest + Testing Library | 表单校验、消息气泡、会话列表、权限按钮状态 |
| API Mock | MSW | 登录、会话列表、消息分页、通知列表、文件上传 URL |
| E2E | Playwright | 登录 → 进入会话 → 发送消息 → 收到实时事件 → 标记已读 |

优先测试高风险路径：登录鉴权、消息发送幂等、断线重连补偿、撤回/编辑 120 秒限制、文件上传确认。

## 12. 性能与体验

- 消息列表和会话列表使用虚拟滚动。
- 消息分页采用 `useInfiniteQuery`，滚动到顶部加载更早消息。
- 会话切换预取最近一页消息。
- 图片消息使用缩略图，原图懒加载。
- 输入中事件节流发送，停止输入延迟触发 `typing_stop`。
- 乐观发送消息：先展示本地 pending，服务端返回 `messageId/seq` 后替换；重复消息 `40004` 时回收本地状态。
- 大文件上传支持进度、取消和失败重试。

## 13. 落地顺序

1. 安装基础依赖：TanStack Router/Query、Zustand、Tailwind、Zod、React Hook Form、Radix、Lucide。
2. 清理 Vite 默认模板，建立 `app/shared/features/pages` 目录。
3. 建立 API Client、`Result<T>` 解包、错误码映射和 QueryProvider。
4. 实现 Auth：登录、注册、登出、路由守卫。
5. 实现主工作台骨架：AppRail、会话列表、聊天面板、详情面板。
6. 接入消息分页、发送、撤回、编辑、删除和引用回复。
7. 接入 WebSocket：心跳、重连、事件分发、增量同步。
8. 实现好友、分组、申请、黑名单和通知页面。
9. 接入文件上传流程。
10. 补齐测试、Mock、CI 和构建检查。

## 14. 参考资料

- TanStack Query React Docs: https://tanstack.com/query/latest/docs/framework/react/overview
- TanStack Router Docs: https://tanstack.com/router/latest/docs/overview
- Zustand TypeScript Guide: https://zustand.docs.pmnd.rs/learn/guides/beginner-typescript
- Tailwind CSS with Vite: https://tailwindcss.com/docs
- React Hook Form: https://react-hook-form.com/get-started
- Zod: https://zod.dev/
- Vitest: https://vitest.dev/guide/
- Playwright: https://playwright.dev/docs/intro
- MSW: https://mswjs.io/docs/
