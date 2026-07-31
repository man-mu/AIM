# AIM 前端架构（实现态）

> 本文描述**当前已实现**的架构（区别于早期规划稿）。
> 配套文档：`docs/tech-notes/01~04`（每个功能域的要点与自审记录）、`docs/data-flow.md`（数据流总览）、`docs/api-feedback.md`（接口反馈）。

## 1. 技术栈（实际使用）

| 领域 | 选型 | 说明 |
|---|---|---|
| 构建 | Vite 8 + TypeScript（**strict**） | `tsc -b` 全量类型检查参与构建 |
| UI | React 19 + Tailwind CSS 4 | 自绘 Apple 风格组件体系（`components/ui`） |
| 路由 | react-router v8 | 声明式路由，选中会话入 URL |
| 服务端状态 | @tanstack/react-query v5 | 缓存/失效/乐观更新 |
| 客户端状态 | zustand v5 | pending 队列 / 草稿 / typing / UI 状态 |
| HTTP | axios + json-bigint(storeAsString) | Java long → 十进制字符串 |
| antd | 仅存量登录/注册表单 + 图标 | 新 UI 不再扩大 antd 面积 |
| 测试 | vitest + Testing Library | 纯逻辑层可脱离 DOM 在 Node 直跑 |

**零新增运行时依赖**：虚拟化(content-visibility)、模态(原生 dialog)、菜单(Popover API)、Toast、
防抖、退避、事件总线等全部手写在 `src/lib` 与 `components/ui`，并有对应单测。

## 2. 架构原则（全部落地）

1. 服务端数据只进 Query；Zustand 只放客户端事实（乐观队列/草稿/typing/面板态）。
2. **缓存更新纯函数化**：`modules/<域>/cache.ts` 无副作用、Node 可测；dispatcher 与 mutation 共用。
3. API 统一解包 `Result<T>`；错误码集中映射为中文文案（`lib/errorCodes.ts`）。
4. 实时层传输无关：`RealtimeChannel` 接口下挂 mockChannel（现在）与 wsChannel（Phase B 就绪）。
5. 环境变量唯一入口 `config/env.ts`；核心逻辑不触碰 `import.meta`。
6. Int64 约定：wire 层 `string|number` 联合，mapper 归一为 string，比较用 `compareInt64`。
7. API 层不依赖 UI/路由：登录态失效仅 emit `auth:expired`，由 App 层统一提示与跳转。

## 3. 目录结构（实现态）

```text
src/
├── app/                 # 装配层：queryClient、appBus
├── config/env.ts        # import.meta.env 唯一入口
├── lib/                 # 零依赖纯函数核心（全部有单测）
│   ├── result / errorCodes / ids / datetime / emitter
│   ├── singleFlight / backoff / clock(可注入调度) / storageKV / async
├── apis/                # 领域 API + client(静默刷新) + queryKeys
├── mocks/               # 浏览器内微型后端（详见 tech-notes/02）
│   ├── engine/          # 路由匹配 + mock JWT
│   ├── db/              # 领域数据库（八表 + 快照持久化 + 世界种子）
│   ├── handlers/        # 七个域的“Controller”
│   ├── platform.ts      # 网关：鉴权/异常→错误码
│   ├── realtimeHub.ts   # NPC 剧本 + 环境活动
│   └── index.ts         # axios adapter 装配（唯一触网层）
├── realtime/            # protocol / channel / wsChannel / dispatcher / useRealtime
├── stores/              # zustand：auth / workspace / pending / composer / typing / realtime
├── modules/
│   ├── conversation/    # model+cache+hooks+workspace+components
│   ├── message/         # model+cache+hooks+components（乐观发送管线）
│   ├── contacts/        # 好友/申请/分组/黑名单
│   ├── user/            # 资料/搜索/ProfileDialog
│   └── file/            # 三步上传 + Worker 哈希 + 图片处理
├── workers/hash.worker.ts
├── components/
│   ├── ui/              # Avatar/Badge/Dialog/Menu/Switch/Toast/…（自绘基座）
│   └── layout/          # AppLayout + AppRail
├── pages/               # Login/Register/Home/Contacts/Notifications
└── router/              # 路由树 + 守卫
```

## 4. 路由

```text
/login /register                 GuestOnly
/home/:conversationId?           消息工作台（三栏；选中态由 URL 驱动）
/contacts?tab=…                  联系人（懒加载）
/notifications                   通知中心（懒加载）
```

## 5. 双环境验证体系

| 层 | 验证方式 | 位置 |
|---|---|---|
| 纯逻辑（lib/mocks/cache/dispatcher/wsChannel） | Node 直跑单测（81 项） | `*.test.ts` |
| 类型正确性 | `tsc --noEmit`（strict） | 全仓 |
| React 组件/hooks | vitest + jsdom（本地 `pnpm test`） | `*.test.tsx` |

纯逻辑与 React 绑定的分离不是偶然——它让核心正确性可以在任何环境（CI、无头容器）被廉价验证。

## 6. Mock ↔ 真后端切换

`.env` 设 `VITE_USE_MOCK=false` 即直连网关（默认 `http://localhost:9080/api/v1`）。
mock 未命中的路由自动走真实网络，支持逐接口灰度。演示账号：`admin/admin123`、`test/test123`；
NPC 密码均为 `123456`。Phase B 后端就绪清单：ws-gateway 上线 → `realtime/index.ts` 工厂自动切 wsChannel（含心跳/重连/sync 补偿接口已就绪）。
