import type { MockDb } from '../db';
import type { MessageRow } from '../db/schema';
import type { DownstreamEvent } from '@/realtime/protocol';
import { toInt64String } from '@/lib/ids';

/** 下行事件出口：由 MockRealtimeHub 实现；未接线时为 no-op。 */
export interface MockEventSink {
  push(event: DownstreamEvent, options?: { delayMs?: number }): void;
}

export const nullEventSink: MockEventSink = { push: () => undefined };

/** handler 运行上下文（时间与事件均可注入，测试全确定）。 */
export interface HandlerContext {
  db: MockDb;
  now(): number;
  events: MockEventSink;
  /** 当前用户发出消息后回调（hub 用于触发 NPC 回复剧本）。 */
  afterUserMessage?(message: MessageRow): void;
}

// ---------------------------------------------------------------------------
// 请求体取值助手（宽进严出：兼容 number/string 形态的 Int64）
// ---------------------------------------------------------------------------
export function asRecord(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
}

export function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

export function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value !== '' && Number.isFinite(Number(value))) {
    return Number(value);
  }
  return fallback;
}

export function asBoolean(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

/** Int64 字段：number / string / bigint → 十进制字符串；无效返回 ''。 */
export function asId(value: unknown): string {
  if (typeof value === 'string' && value !== '') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'bigint') {
    return toInt64String(value);
  }
  return '';
}

export function asIdArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map(asId).filter((id) => id !== '');
}

export function pageParams(query: Record<string, string>, defaultSize = 20): { pageNum: number; pageSize: number } {
  const pageNum = Math.max(1, asNumber(query.pageNum, 1));
  const pageSize = Math.min(100, Math.max(1, asNumber(query.pageSize, defaultSize)));
  return { pageNum, pageSize };
}

export function paginate<T>(rows: T[], pageNum: number, pageSize: number): { slice: T[]; total: number } {
  const start = (pageNum - 1) * pageSize;
  return { slice: rows.slice(start, start + pageSize), total: rows.length };
}
