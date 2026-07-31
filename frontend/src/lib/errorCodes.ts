/**
 * 后端错误码 → 用户可读文案。
 *
 * 码段划分（见 document/api-v1-implemented.md §1.2 / §6）：
 *   400/401/500 通用；10xxx user；20xxx friend；30xxx conv；
 *   40xxx message；50xxx file；60xxx notification/signaling。
 */
export const ERROR_MESSAGES: Readonly<Record<number, string>> = {
  400: '请求参数有误',
  401: '登录状态已失效',
  500: '服务器开小差了，请稍后再试',

  // user-service (10xxx)
  10001: '用户不存在',
  10002: '用户名已存在',
  10003: '手机号已被注册',
  10004: '密码错误',
  10005: '登录状态已失效，请重新登录',
  10006: '登录已过期，请重新登录',
  10007: '登录会话不存在',
  10008: '登录失败次数过多，请稍后再试',
  10009: '邮箱已被注册',

  // friend-service (20xxx)
  20001: '你们已经是好友了',
  20002: '好友申请不存在或已处理',
  20003: '对方不是你的好友',
  20004: '不能添加自己为好友',
  20005: '好友分组不存在',
  20006: '对方已被你拉黑',
  20007: '你已被对方拉黑',

  // conv-service (30xxx)
  30001: '会话不存在',
  30002: '该用户已在会话中',
  30003: '该用户不在会话中',
  30004: '你不是该会话成员',
  30005: '权限不足',
  30006: '你已被禁言',
  30007: '当前全员禁言中',
  30008: '成员数量已达上限（500）',
  30009: '不能转让给自己',

  // message-service (40xxx)
  40001: '消息不存在',
  40002: '已超过可撤回时间',
  40003: '已超过可编辑时间',
  40004: '请勿重复发送',
  40005: '没有操作该消息的权限',

  // file-service (50xxx)
  50001: '文件不存在',
  50002: '文件上传失败',
  50003: '文件过大（上限 100MB）',
  50004: '不支持的文件类型',

  // notification / signaling (60xxx)
  60001: '通知不存在',
};

/** 会话（登录态）失效类错误码：命中后进入静默刷新 / 强制登出流程。 */
export const AUTH_FAILURE_CODES: ReadonlySet<number> = new Set([401, 10005, 10006]);

export function isAuthFailureCode(code: number): boolean {
  return AUTH_FAILURE_CODES.has(code);
}

/** 优先取映射文案；后端文案兜底；再兜底通用文案。 */
export function messageForCode(code: number, serverMessage?: string): string {
  return ERROR_MESSAGES[code] ?? (serverMessage?.trim() || `操作失败（${code}）`);
}
