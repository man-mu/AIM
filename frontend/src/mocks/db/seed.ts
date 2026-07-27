import type { TextContent } from '@/types/Message/Message';
import { createDirectConversation, createGroupConversation, getMember } from './conversations';
import { appendMessage } from './messages';
import { addFriendPair, createFriendRequest, pushNotification } from './social';
import { createUser } from './users';
import { type DbState } from './state';

/**
 * 剧本种子：固定的 NPC 阵容 + 为新用户搭建“已经在用”的世界。
 * 所有时间相对 `now` 回溯，保证任何时刻注册看到的都是新鲜数据。
 */
export const NPC_IDS = {
  linchuan: '339394874048512101',
  alan: '339394874048512102',
  suwanqing: '339394874048512103',
  ahe: '339394874048512104',
  luzhiyuan: '339394874048512105',
  shenyifan: '339394874048512106',
} as const;

const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

export function seedNpcUsers(state: DbState, now: number): void {
  const npcProfiles = [
    { id: NPC_IDS.linchuan, username: '林川', phone: '13800000101', bio: '设计是克制的艺术。', gender: 1 as const },
    { id: NPC_IDS.alan, username: '阿岚', phone: '13800000102', bio: '读书、爬山、写代码。', gender: 2 as const },
    { id: NPC_IDS.suwanqing, username: '苏晚晴', phone: '13800000103', bio: '正在读《长安的荔枝》', gender: 2 as const },
    { id: NPC_IDS.ahe, username: '阿禾', phone: '13800000104', bio: 'AIM 产品经理', gender: 0 as const },
    { id: NPC_IDS.luzhiyuan, username: '陆知远', phone: '13800000105', bio: '后端 / 分布式', gender: 1 as const },
    { id: NPC_IDS.shenyifan, username: '沈一帆', phone: '13800000106', bio: '刚来的实习生，多多关照', gender: 1 as const },
  ];

  for (const profile of npcProfiles) {
    if (!state.users.has(profile.id)) {
      createUser(
        state,
        {
          ...profile,
          password: '123456',
          email: `${profile.phone}@aim.local`,
          isNpc: true,
        },
        now - 90 * DAY,
      );
    }
  }
}

function say(state: DbState, conversationId: string, fromUserId: string, text: string, at: number, extra?: Partial<TextContent>): void {
  appendMessage(
    state,
    {
      conversationId,
      fromUserId,
      msgType: 1,
      content: { text, ...extra },
      clientMsgId: `seed-${conversationId}-${at}`,
      createdAt: at,
      skipGuards: true,
    },
    at,
  );
}

/**
 * 为 userId 搭建初始世界：
 * - 与林川的单聊（全部已读）
 * - 「周末读书会」群（2 条未读）
 * - 「AIM 前端评审组」群（5 条未读，含 @ 提及与引用）
 * - NPC 好友关系 + 一条待处理好友申请 + 欢迎通知
 */
export function bootstrapWorldFor(state: DbState, userId: string, now: number): void {
  // ---- 单聊：林川 ----
  const direct = createDirectConversation(state, userId, NPC_IDS.linchuan, now - 3 * DAY);
  say(state, direct.id, NPC_IDS.linchuan, '在吗？AIM 的登录页配色我调了一版', now - 3 * DAY + 10 * MIN);
  say(state, direct.id, userId, '发我看看？', now - 3 * DAY + 12 * MIN);
  say(state, direct.id, NPC_IDS.linchuan, '主色用了 #0071e3，克制一点，像系统原生', now - 3 * DAY + 15 * MIN);
  say(state, direct.id, userId, '我喜欢这个方向，接口联调完就套上', now - 3 * DAY + 20 * MIN);
  say(state, direct.id, NPC_IDS.linchuan, '今晚一起吃饭吗？顺便聊聊详情面板的交互', now - 2 * HOUR);
  say(state, direct.id, userId, '好啊，老地方见', now - 2 * HOUR + 3 * MIN);
  const directMember = getMember(state, direct.id, userId);
  if (directMember) {
    directMember.lastReadSeq = state.conversations.get(direct.id)?.maxSeq ?? 0;
  }
  const peerMember = getMember(state, direct.id, NPC_IDS.linchuan);
  if (peerMember) {
    peerMember.lastReadSeq = state.conversations.get(direct.id)?.maxSeq ?? 0;
  }

  // ---- 群：周末读书会 ----
  const bookClub = createGroupConversation(
    state,
    NPC_IDS.alan,
    { name: '周末读书会', memberIds: [userId, NPC_IDS.suwanqing, NPC_IDS.linchuan] },
    now - 20 * DAY,
  );
  state.conversations.get(bookClub.id)!.announcement = '每周六下午 3 点，城南图书馆二楼共读。\n本月书目：《长安的荔枝》';
  say(state, bookClub.id, NPC_IDS.alan, '欢迎新伙伴加入本周的读书会～', now - 2 * DAY);
  say(state, bookClub.id, NPC_IDS.suwanqing, '这周读到第六章，节奏越来越紧了', now - DAY - 5 * HOUR);
  say(state, bookClub.id, NPC_IDS.linchuan, '我周六可能晚到半小时，你们先开始', now - DAY - 4 * HOUR);
  say(state, bookClub.id, NPC_IDS.alan, '没问题，笔记我会同步到群里', now - 40 * MIN);
  say(state, bookClub.id, NPC_IDS.suwanqing, '对了，下个月书目大家投个票？', now - 25 * MIN);
  const bookMember = getMember(state, bookClub.id, userId);
  if (bookMember) {
    bookMember.lastReadSeq = Math.max(0, (state.conversations.get(bookClub.id)?.maxSeq ?? 0) - 2);
  }

  // ---- 群：AIM 前端评审组 ----
  const review = createGroupConversation(
    state,
    NPC_IDS.ahe,
    { name: 'AIM 前端评审组', memberIds: [userId, NPC_IDS.linchuan, NPC_IDS.luzhiyuan] },
    now - 10 * DAY,
  );
  state.conversations.get(review.id)!.announcement = '每周三 16:00 例行评审。\n提交评审的 PR 请提前挂到看板。';
  say(state, review.id, NPC_IDS.ahe, '这周评审重点：会话列表虚拟滚动的性能基线', now - DAY + 2 * HOUR);
  say(state, review.id, NPC_IDS.luzhiyuan, '后端这边 conv 列表接口已经部署到 dev 环境了', now - 5 * HOUR);
  say(state, review.id, NPC_IDS.linchuan, '详情面板的空态图我下午出', now - 4 * HOUR);
  say(state, review.id, NPC_IDS.ahe, '收到。记得未读角标超过 99 显示 99+', now - 3 * HOUR);
  say(state, review.id, NPC_IDS.luzhiyuan, '消息 seq 是 long，前端记得用字符串承接，别用 number', now - 90 * MIN);
  say(state, review.id, NPC_IDS.ahe, '@所有人 周三评审改到 15:30，别迟到', now - 30 * MIN, { mentionAll: true });
  const reviewMember = getMember(state, review.id, userId);
  if (reviewMember) {
    reviewMember.lastReadSeq = Math.max(0, (state.conversations.get(review.id)?.maxSeq ?? 0) - 5);
  }

  // ---- 好友关系 / 申请 / 通知 ----
  addFriendPair(state, userId, NPC_IDS.linchuan, now - 3 * DAY);
  addFriendPair(state, userId, NPC_IDS.alan, now - 20 * DAY);
  addFriendPair(state, userId, NPC_IDS.suwanqing, now - 18 * DAY);
  addFriendPair(state, userId, NPC_IDS.ahe, now - 10 * DAY);

  const request = createFriendRequest(state, NPC_IDS.shenyifan, userId, '你好！我是新来的实习生沈一帆，想加个好友请教前端问题', now - 6 * HOUR);
  pushNotification(
    state,
    {
      userId,
      type: 1,
      title: '新好友申请',
      content: '沈一帆 申请添加你为好友',
      referenceId: request.id,
    },
    now - 6 * HOUR,
  );
  pushNotification(
    state,
    {
      userId,
      type: 1,
      title: '欢迎使用 AIM',
      content: '这里是你的消息工作台。当前为 Mock 模式，所有数据仅存于本地浏览器。',
    },
    now - 6 * HOUR + MIN,
  );
}
