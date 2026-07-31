/**
 * Int64 ID 处理约定：
 * 后端所有 id / seq 均为 Java long，JSON 反序列化（json-bigint storeAsString）
 * 后在前端一律以「十进制数字字符串」承接，展示与传参不做 number 运算。
 */
export type Int64String = string;

/** 任意来源（number/string/bigint）归一化为 Int64String。 */
export function toInt64String(value: string | number | bigint): Int64String {
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'bigint') {
    return value.toString();
  }
  if (!Number.isSafeInteger(value)) {
    // 走到这里说明上游没有经过 json-bigint，精度已经丢失，暴露问题而不是掩盖。
    console.warn('[ids] unsafe integer converted to string, precision may be lost:', value);
  }
  return String(value);
}

const INT64_RE = /^-?\d+$/;

export function isInt64String(value: unknown): value is Int64String {
  return typeof value === 'string' && INT64_RE.test(value);
}

/**
 * 比较两个十进制整数字符串（支持超出 Number.MAX_SAFE_INTEGER 的值）。
 * 返回负数 / 0 / 正数，可直接用于 Array#sort。
 */
export function compareInt64(a: Int64String, b: Int64String): number {
  const aNeg = a.startsWith('-');
  const bNeg = b.startsWith('-');
  if (aNeg !== bNeg) {
    return aNeg ? -1 : 1;
  }

  const absA = aNeg ? a.slice(1) : a;
  const absB = bNeg ? b.slice(1) : b;
  const trimmedA = absA.replace(/^0+(?=\d)/, '');
  const trimmedB = absB.replace(/^0+(?=\d)/, '');

  let result: number;
  if (trimmedA.length !== trimmedB.length) {
    result = trimmedA.length - trimmedB.length;
  } else if (trimmedA === trimmedB) {
    result = 0;
  } else {
    result = trimmedA < trimmedB ? -1 : 1;
  }
  return aNeg ? -result : result;
}

export function maxInt64(a: Int64String, b: Int64String): Int64String {
  return compareInt64(a, b) >= 0 ? a : b;
}

/** 幂等发送用的客户端消息 ID。 */
export function newClientMsgId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `c-${crypto.randomUUID()}`;
  }
  // 极端环境兜底（非安全上下文的旧浏览器）。
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

let localSequence = 0;

/** 单调递增的本地 ID（mock 数据库、乐观占位等场景）。 */
export function nextLocalId(prefix: string): string {
  localSequence += 1;
  return `${prefix}-${Date.now().toString(36)}-${localSequence}`;
}
