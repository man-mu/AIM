import { createEmitter } from '@/lib/emitter';
import { toMessageNewData, type DownstreamEvent, type RealtimeFrame } from '@/realtime/protocol';
import type { TextContent } from '@/types/Message/Message';
import type { Scheduler } from '@/lib/clock';
import type { MockDb } from './db';
import type { MessageRow } from './db/schema';
import type { MockEventSink } from './handlers/context';

/**
 * Mock 实时中枢：模拟 signaling-service → ws-gateway 的下行推送。
 *
 * 职责：
 *  1. 作为 handlers 的事件出口（MockEventSink），带可选延迟地派发下行帧；
 *  2. 剧本引擎——用户发消息后，NPC 会「正在输入 → 回复 → 已读」；
 *  3. 环境活动——低频率地在群里制造新消息，让未读角标真实变化。
 *
 * 全部定时行为经由注入的 Scheduler，测试可用 ManualScheduler 逐步推进。
 */
export interface MockRealtimeHub extends MockEventSink {
  subscribe(listener: (frame: RealtimeFrame) => void): () => void;
  notifyUserMessage(message: MessageRow): void;
  /** 启动环境活动循环（返回停止函数）。 */
  startAmbient(): () => void;
}

export interface MockRealtimeHubOptions {
  db: MockDb;
  scheduler: Scheduler;
  /** 当前登录用户（未登录时为 null，事件全部静默丢弃）。 */
  currentUserId(): string | null;
  random?: () => number;
  /** 事件派发后回调（平台层用于持久化 db 快照）。 */
  onMutated?(): void;
}

const REPLY_POOL_DIRECT = [
  '好，我看看',
  '收到！',
  '哈哈可以',
  '稍等，我在开会，一会儿回你',
  '这个想法不错，晚点细聊',
  '嗯嗯，就这么定',
];

const REPLY_POOL_GROUP = [
  '同意+1',
  '我这边没问题',
  '这个点记到会议纪要里了',
  '有道理，回头我试试',
  '收到，我跟进',
  '哈哈哈笑死',
];

const AMBIENT_POOL = [
  '大家周报别忘了今天交～',
  '刚看到一篇不错的文章，回头发群里',
  '下午的会议室我订好了',
  '这周的进度看起来不错',
  '有人一起点咖啡吗？',
];

export function createMockRealtimeHub(options: MockRealtimeHubOptions): MockRealtimeHub {
  const { db, scheduler, currentUserId } = options;
  const random = options.random ?? Math.random;
  const emitter = createEmitter<{ frame: RealtimeFrame }>();

  const pick = <T>(pool: T[]): T => pool[Math.floor(random() * pool.length)] as T;
  const between = (min: number, max: number): number => Math.round(min + (max - min) * random());

  const deliver = (event: DownstreamEvent): void => {
    emitter.emit('frame', { event: event.event, data: event.data, timestamp: scheduler.now() });
  };

  const push: MockEventSink['push'] = (event, pushOptions) => {
    const delayMs = pushOptions?.delayMs ?? 0;
    if (delayMs <= 0) {
      deliver(event);
      return;
    }
    scheduler.schedule(() => deliver(event), delayMs);
  };

  /** NPC 在会话中发言：写库 + 推送 message.new（面向当前用户）。 */
  const npcSpeak = (conversationId: string, npcId: string, text: string): void => {
    const me = currentUserId();
    if (!me) {
      return;
    }
    const now = scheduler.now();
    const row = db.messages.append(
      {
        conversationId,
        fromUserId: npcId,
        msgType: 1,
        content: { text } satisfies TextContent,
        clientMsgId: `npc-${npcId}-${now}-${Math.floor(random() * 1e6)}`,
        skipGuards: true,
      },
      now,
    );
    // NPC 自己已读到自己的消息。
    db.convs.markRead(conversationId, npcId, row.seq);

    const npc = db.users.get(npcId);
    const member = db.convs.getMember(conversationId, me);
    const conversation = db.convs.get(conversationId);
    const unread = member && conversation ? Math.max(0, conversation.maxSeq - member.lastReadSeq) : 0;
    deliver({
      event: 'message.new',
      data: toMessageNewData(db.messages.toDTO(row), { id: npcId, username: npc?.username ?? 'NPC', avatar: npc?.avatar ?? '' }, unread),
    });
    options.onMutated?.();
  };

  const notifyUserMessage = (message: MessageRow): void => {
    const me = currentUserId();
    if (!me || message.fromUserId !== me) {
      return;
    }
    const npcMembers = db.convs
      .listMembers(message.conversationId)
      .filter((member) => member.userId !== me && db.users.get(member.userId)?.isNpc);
    if (npcMembers.length === 0) {
      return;
    }

    const conversation = db.convs.get(message.conversationId);
    const isDirect = conversation?.type === 1;
    const responder = pick(npcMembers).userId;

    // 1. NPC 已读你的消息（read_sync）。
    scheduler.schedule(() => {
      db.convs.markRead(message.conversationId, responder, message.seq);
      deliver({
        event: 'read_sync',
        data: { convId: message.conversationId, userId: responder, lastReadSeq: message.seq },
      });
      options.onMutated?.();
    }, between(600, 1500));

    // 2. 正在输入…（60% 概率展示 typing）。
    const willType = random() < 0.6;
    if (willType) {
      scheduler.schedule(() => {
        deliver({ event: 'typing', data: { convId: message.conversationId, userId: responder } });
      }, between(1200, 2200));
    }

    // 3. 回复。
    scheduler.schedule(() => {
      if (willType) {
        deliver({ event: 'typing.stop', data: { convId: message.conversationId, userId: responder } });
      }
      npcSpeak(message.conversationId, responder, pick(isDirect ? REPLY_POOL_DIRECT : REPLY_POOL_GROUP));
    }, between(2800, 5200));
  };

  const startAmbient = (): (() => void) => {
    let stopped = false;
    let cancel: (() => void) | null = null;

    const tick = (): void => {
      if (stopped) {
        return;
      }
      const me = currentUserId();
      if (me) {
        const groups = db.convs.listFor(me).filter((row) => row.type === 2);
        if (groups.length > 0 && random() < 0.7) {
          const target = pick(groups);
          const npcMembers = db.convs
            .listMembers(target.id)
            .filter((member) => member.userId !== me && db.users.get(member.userId)?.isNpc);
          if (npcMembers.length > 0) {
            npcSpeak(target.id, pick(npcMembers).userId, pick(AMBIENT_POOL));
          }
        }
      }
      cancel = scheduler.schedule(tick, between(50_000, 110_000));
    };

    cancel = scheduler.schedule(tick, between(25_000, 45_000));
    return () => {
      stopped = true;
      cancel?.();
    };
  };

  return {
    push,
    subscribe: (listener) => emitter.on('frame', listener),
    notifyUserMessage,
    startAmbient,
  };
}
