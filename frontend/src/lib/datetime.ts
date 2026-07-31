/**
 * 时间格式化工具（IM 语义），基于 Intl，全部纯函数：
 * `now` 可注入，测试完全确定。所有时间戳为 epoch 毫秒。
 */

const timeFormatter = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
const monthDayFormatter = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' });
const fullDateFormatter = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' });
const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const;

export function startOfDay(timestamp: number): number {
  const date = new Date(timestamp);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

export function isSameDay(a: number, b: number): boolean {
  return startOfDay(a) === startOfDay(b);
}

const DAY_MS = 24 * 60 * 60 * 1000;

function dayDiff(timestamp: number, now: number): number {
  return Math.round((startOfDay(now) - startOfDay(timestamp)) / DAY_MS);
}

/** `HH:mm`（消息气泡时间）。 */
export function formatTimeOfDay(timestamp: number): string {
  return timeFormatter.format(timestamp);
}

/**
 * 会话列表右上角时间戳：
 * 今天 → HH:mm；昨天 → 昨天；7 天内 → 周X；今年 → M月D日；更早 → YYYY年M月D日。
 */
export function formatConversationStamp(timestamp: number, now = Date.now()): string {
  const diff = dayDiff(timestamp, now);
  if (diff <= 0) {
    return formatTimeOfDay(timestamp);
  }
  if (diff === 1) {
    return '昨天';
  }
  if (diff < 7) {
    return WEEKDAY_LABELS[new Date(timestamp).getDay()] as string;
  }
  if (new Date(timestamp).getFullYear() === new Date(now).getFullYear()) {
    return monthDayFormatter.format(timestamp);
  }
  return fullDateFormatter.format(timestamp);
}

/**
 * 消息列表的日期分隔条：
 * 今天 → 今天；昨天 → 昨天；今年 → M月D日 周X；更早 → YYYY年M月D日。
 */
export function formatDayDivider(timestamp: number, now = Date.now()): string {
  const diff = dayDiff(timestamp, now);
  if (diff <= 0) {
    return '今天';
  }
  if (diff === 1) {
    return '昨天';
  }
  const weekday = WEEKDAY_LABELS[new Date(timestamp).getDay()] as string;
  if (new Date(timestamp).getFullYear() === new Date(now).getFullYear()) {
    return `${monthDayFormatter.format(timestamp)} ${weekday}`;
  }
  return fullDateFormatter.format(timestamp);
}

/** 详情场景的完整时间：`YYYY年M月D日 HH:mm`。 */
export function formatFullDateTime(timestamp: number): string {
  return `${fullDateFormatter.format(timestamp)} ${formatTimeOfDay(timestamp)}`;
}

/**
 * 消息之间是否需要插入日期分隔条 / 时间间隔条。
 * 规则：跨天必插；同天间隔超过 gapMs（默认 10 分钟）也插时间条。
 */
export function needsTimeDivider(prevTimestamp: number | null, timestamp: number, gapMs = 10 * 60 * 1000): boolean {
  if (prevTimestamp === null) {
    return true;
  }
  if (!isSameDay(prevTimestamp, timestamp)) {
    return true;
  }
  return timestamp - prevTimestamp >= gapMs;
}

/** 禁言剩余时长等场景：秒数 → 可读文案。 */
export function formatDuration(totalSeconds: number): string {
  if (totalSeconds <= 0) {
    return '0秒';
  }
  const units: Array<[number, string]> = [
    [86400, '天'],
    [3600, '小时'],
    [60, '分钟'],
    [1, '秒'],
  ];
  const parts: string[] = [];
  let rest = Math.floor(totalSeconds);
  for (const [size, label] of units) {
    if (rest >= size && parts.length < 2) {
      parts.push(`${Math.floor(rest / size)}${label}`);
      rest %= size;
    }
  }
  return parts.join('');
}
