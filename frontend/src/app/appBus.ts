import { createEmitter } from '@/lib/emitter';

/**
 * 应用级事件总线：承载跨层的少量关键事件。
 *
 * 设计约束：API 层（apis/client.ts）不允许 import 路由或 UI ——
 * 登录态失效时它只负责 emit，具体“跳转 + 提示”由挂在路由树内的
 * <AuthExpiryListener/> 消费，保持单向依赖：apis → bus ← app。
 */
export interface AppBusEvents extends Record<string, unknown> {
  /** 登录态不可恢复地失效（刷新失败 / refresh token 过期）。 */
  'auth:expired': { reason: 'token-expired' | 'refresh-failed' };
}

export const appBus = createEmitter<AppBusEvents>();
