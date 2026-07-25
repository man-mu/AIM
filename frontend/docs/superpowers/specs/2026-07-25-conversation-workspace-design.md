# 会话工作台设计

## 目标

将受认证保护的 `/home` 从空状态骨架升级为可操作的会话工作台。采用已确认的 A 方案：左侧导航与会话列表、中间聊天区、右侧会话详情。首版支持选择会话和本地文本消息即时展示，同时维持当前认证、登出和路由守卫行为不变。

界面遵循当前产品的克制 Apple 风格：浅色全屏工作面、清晰的列边界、紧凑的列表行与 8px 以下圆角控制组件。工作台不是嵌套卡片集合，信息通过面板分栏组织。

## 范围与非目标

本次实现包含：

- 会话列表中的头像、名称、最后消息、时间、未读计数与选中态；
- 默认会话的消息历史、对方与当前用户的文本消息气泡；
- 文本编辑器：`Enter` 发送、`Shift+Enter` 换行、空白内容不可发送；
- 发送消息后的本地即时追加、列表预览和时间更新；
- 右侧会话概要：单聊在线状态，群聊成员数和公告等只读信息；
- 窄桌面隐藏详情栏；移动端先显示会话列表，选择后显示聊天区并提供返回会话列表的按钮。

本次明确不包含：

- 不修改 `src/mocks/**`、现有 mock handler、axios 配置或 API 调用；
- 不连接后端、不引入 WebSocket、不持久化本地发送内容；页面刷新后本地状态重置；
- 不实现新建会话、联系人、通知、图片/文件/语音消息、撤回、编辑、已读回执或会话设置写入；
- 不修改登录、注册、登出、鉴权状态同步和全局路由守卫。

## 组件边界

`HomeShell` 收敛为应用外壳，只拥有账户区域、登出入口、主导航以及三栏布局的插槽，不读取也不修改会话数据。

会话业务放入 `src/modules/conversation/`，并由本地 Provider 连接三个面板：

```text
Home
└─ LocalConversationProvider
   └─ HomeShell
      ├─ ConversationList
      ├─ ChatPanel
      │  ├─ MessageList
      │  └─ MessageComposer
      └─ ConversationDetailPanel
```

Provider 是本地状态唯一所有者，保存当前会话 ID、按会话分组的消息和发送动作。列表、聊天和详情面板只通过明确的 context hook 读取状态或调用行为，从而不依赖 `Home`、认证 store 或路由实现。`Home` 继续只组装用户查询、登出状态和外壳。

模块文件保持最小且可替换：

```text
src/modules/conversation/
├─ components/
│  ├─ ConversationList.tsx
│  ├─ ChatPanel.tsx
│  ├─ ConversationDetailPanel.tsx
│  ├─ MessageList.tsx
│  └─ MessageComposer.tsx
├─ LocalConversationProvider.tsx
├─ demoData.ts
└─ types.ts
```

`demoData.ts` 是模块内的纯展示种子数据，而不是网络层 mock；不被 `src/mocks` 注册，也不拦截任何请求。

## 本地数据与 API 对齐

本地 UI 模型保留后续接口接入需要的核心字段。会话 ID、消息 ID、序号和客户端消息 ID 统一为 `string`，不把可能超过 JavaScript 安全整数的值转换为 `number`。

第一版只渲染 `msgType = 1` 的文本消息，内容对应 API 的 `content.text`。每条本地发送消息都生成 `clientMsgId`，为将来接入 `POST /messages/send` 的幂等键预留位置。会话摘要对应 `GET /convs` 的 `lastMessagePreview`、`unreadCount`、`isPinned` 等概念；已读动作将来可替换为 `PUT /convs/{conversationId}/read`。

当前不发起这些请求，也不改变 API 文档或 mock。待后端接入时，Provider 内部可从展示数据切换到查询和乐观 mutation，面板 API 保持稳定。

## 交互与数据流

选择列表项后，Provider 切换当前会话并将该会话的未读计数设为零；消息区、详情栏和列表选中态由同一状态驱动。

```text
输入文本
  -> MessageComposer 校验 trim 后内容
  -> Provider 创建本地文本消息
  -> 当前会话消息列表立即追加
  -> 会话摘要更新最后预览和时间
  -> MessageList 滚动到底部
```

发送只在本地完成，不显示“已送达”或“已保存”的虚假服务端状态。编辑器使用语义化 `form` 和 `textarea`；发送按钮有可访问名称，空白内容禁用发送。会话列表使用按钮元素，当前项通过语义状态和视觉状态表达。移动端返回按钮的标签明确指向会话列表。

## 响应式布局

- `lg` 以上：左列固定宽度，聊天区弹性占据主空间，右侧详情列展示；
- `sm` 至 `lg`：保留导航/会话列表与聊天区，隐藏详情列以保护消息阅读宽度；
- `sm` 以下：不并排压缩面板。默认显示会话列表，选择会话后显示聊天区，并在头部提供返回列表的图标按钮和 tooltip。

## 测试与验收

新增会话模块组件测试，至少覆盖：

- 默认会话内容与会话列表信息正确渲染；
- 切换会话会更换消息内容，并清除对应未读数；
- 输入文本后通过 `Enter` 立即显示自己的消息，并更新列表预览；
- 空白文本不可发送；
- `Shift+Enter` 保留换行而不发送；
- 现有 Home 登出、认证和路由守卫测试仍通过。

验证命令包括前端的 `pnpm test`、`pnpm lint`、`pnpm build`，并按项目约定尝试 `mvn compile` 与 `mvn test`。若本机 Maven 不可用，记录为环境阻塞而不将其误报为前端失败。
