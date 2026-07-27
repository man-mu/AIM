# AIM 前端（Phase 1 Web 客户端）

类飞书的 IM 工作台：会话 / 消息（乐观发送 + 实时事件）/ 联系人 / 通知 / 资料与文件上传。
后端未就绪的部分由**浏览器内 Mock 平台**提供完整数据流（含 NPC 自动回复剧本）。

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:5173
```

演示账号：`admin / admin123`、`test / test123`，或直接注册新账号（自动搭建初始世界）。
NPC（林川、阿岚、苏晚晴、阿禾、陆知远、沈一帆）密码均为 `123456`。

> Mock 数据持久化在 localStorage（键前缀 `aim-mock:`），清站点数据即可重置世界。

## 脚本

```bash
pnpm dev          # 开发服务器
pnpm build        # tsc -b（strict 类型检查）+ vite build
pnpm test         # vitest 全量测试（逻辑 + 组件）
pnpm test:watch
pnpm lint
pnpm preview
```

## 环境变量（.env）

```dotenv
VITE_USE_MOCK=true                                # false 时直连真实网关
# VITE_API_BASE_URL=http://localhost:9080/api/v1  # gateway-service
# VITE_WS_URL=ws://localhost:8081/ws              # ws-gateway（Phase B）
```

关闭 mock 后同一套代码直连后端；mock 未命中的路由自动走真实网络（支持逐接口灰度迁移）。

## 文档导航

| 文档 | 内容 |
|---|---|
| `ARCHITECTURE.md` | 实现态架构：分层 / 目录 / 原则 / 验证体系 |
| `docs/data-flow.md` | 数据流总览（含 mermaid 图） |
| `docs/data-flow.html` | 交互式架构与数据流展示（浏览器直接打开） |
| `docs/tech-notes/01~04` | 各功能域技术要点 + 设计权衡 + 自审优化记录 |
| `docs/api-feedback.md` | 对接中发现的接口设计问题（给后端） |
| `scripts/cleanup-obsolete.md` | 重构后废弃文件的清理清单 |

## 目录速览

见 `ARCHITECTURE.md` §3。核心约定：`lib/` 零依赖纯函数（全部单测）；
`mocks/` 是与真实后端同构的微型后端；`modules/<域>/cache.ts` 是可单测的缓存更新纯函数层。
