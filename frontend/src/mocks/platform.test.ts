import { beforeEach, describe, expect, it } from 'vitest';
import { createManualScheduler, type ManualScheduler } from '@/lib/clock';
import type { ApiEnvelope } from '@/lib/result';
import type { ConversationDTO, ListConversationsData } from '@/types/Conversation/Conversation';
import type { ListMessagesData, SendMessageData } from '@/types/Message/Message';
import { NPC_IDS } from './db';
import { createMockPlatform, type MockPlatform } from './platform';

/**
 * Mock 平台端到端测试：以 HTTP 语义直接驱动 handle()，
 * 覆盖鉴权、世界种子、会话/消息/好友/通知全链路。
 */
interface Session {
  accessToken: string;
  refreshToken: string;
  userId: string;
}

let scheduler: ManualScheduler;
let platform: MockPlatform;

function call<T>(input: {
  method: string;
  url: string;
  body?: unknown;
  params?: Record<string, unknown>;
  token?: string;
}): { status: number; envelope: ApiEnvelope<T> } {
  const outcome = platform.handle({
    method: input.method,
    url: input.url,
    body: input.body,
    params: input.params,
    authorization: input.token ? `Bearer ${input.token}` : null,
  });
  if (!outcome) {
    throw new Error(`route not found: ${input.method} ${input.url}`);
  }
  return outcome as { status: number; envelope: ApiEnvelope<T> };
}

function expectOk<T>(outcome: { status: number; envelope: ApiEnvelope<T> }): T {
  expect(outcome.status).toBe(200);
  expect(outcome.envelope.code).toBe(0);
  return outcome.envelope.data as T;
}

function register(username: string): Session {
  const data = expectOk<{ userId: string; tokens: { accessToken: string; refreshToken: string } }>(
    call({
      method: 'POST',
      url: '/auth/register',
      body: { username, password: 'secret123', deviceId: 'd1', platform: 'web' },
    }),
  );
  return { accessToken: data.tokens.accessToken, refreshToken: data.tokens.refreshToken, userId: String(data.userId) };
}

beforeEach(() => {
  scheduler = createManualScheduler(1_760_000_000_000);
  platform = createMockPlatform({ scheduler, seedDemoAccounts: true });
});

describe('auth & bootstrap', () => {
  it('rejects protected routes without a valid token (HTTP 401)', () => {
    const outcome = call({ method: 'GET', url: '/users/me' });
    expect(outcome.status).toBe(401);
    expect(outcome.envelope.code).toBe(401);
  });

  it('register seeds a living world: 3 conversations with expected unread counts', () => {
    const session = register('晨风qwq');
    const data = expectOk<ListConversationsData>(
      call({ method: 'GET', url: '/convs', params: { pageNum: 1, pageSize: 20 }, token: session.accessToken }),
    );

    expect(data.total).toBe(3);
    const byName = new Map(data.conversations.map((conversation) => [conversation.name, conversation]));
    expect(byName.get('林川')?.unreadCount).toBe(0);
    expect(byName.get('周末读书会')?.unreadCount).toBe(2);
    expect(byName.get('AIM 前端评审组')?.unreadCount).toBe(5);
    // 单聊的展示信息来自对端用户。
    expect(byName.get('林川')?.type).toBe(1);
  });

  it('login with a wrong password returns 10004 and demo accounts exist', () => {
    const bad = call({ method: 'POST', url: '/auth/login', body: { account: 'admin', password: 'nope', deviceId: 'd', platform: 'web' } });
    expect(bad.envelope.code).toBe(10004);

    const good = call({ method: 'POST', url: '/auth/login', body: { account: 'admin', password: 'admin123', deviceId: 'd', platform: 'web' } });
    expect(good.envelope.code).toBe(0);
  });

  it('refresh returns 4 fields and rotates the refresh token (old one revoked)', () => {
    const session = register('阿测001');
    const refreshed = expectOk<{ accessToken: string; refreshToken: string; accessExpire: number; refreshExpire: number }>(
      call({ method: 'POST', url: '/auth/refresh', body: { refreshToken: session.refreshToken } }),
    );
    expect(refreshed.accessToken).not.toBe(session.accessToken);
    expect(refreshed.refreshToken).not.toBe(session.refreshToken);
    expect(refreshed.refreshExpire).toBeGreaterThan(0);

    // 旧 refreshToken 已一次性吊销。
    const replayOld = call({ method: 'POST', url: '/auth/refresh', body: { refreshToken: session.refreshToken } });
    expect(replayOld.envelope.code).toBe(10005);

    // 新 refreshToken 可继续轮换。
    const again = call({ method: 'POST', url: '/auth/refresh', body: { refreshToken: refreshed.refreshToken } });
    expect(again.envelope.code).toBe(0);

    // 快进 3 小时：旧 access 过期。
    scheduler.advance(3 * 60 * 60 * 1000);
    const expired = call({ method: 'GET', url: '/users/me', token: session.accessToken });
    expect(expired.status).toBe(401);
  });
});

describe('conversations', () => {
  it('creating the same direct chat twice is idempotent', () => {
    const session = register('小北同学');
    const first = expectOk<{ conversationId: string }>(
      call({ method: 'POST', url: '/convs', body: { type: 1, peerUserId: NPC_IDS.shenyifan }, token: session.accessToken }),
    );
    const second = expectOk<{ conversationId: string }>(
      call({ method: 'POST', url: '/convs', body: { type: 1, peerUserId: NPC_IDS.shenyifan }, token: session.accessToken }),
    );
    expect(String(second.conversationId)).toBe(String(first.conversationId));
  });

  it('group lifecycle: create → invite(already-member split) → settings → mark read', () => {
    const session = register('组长大人');
    const created = expectOk<{ conversationId: string; conversation: ConversationDTO }>(
      call({
        method: 'POST',
        url: '/convs',
        body: { type: 2, name: '新项目组', memberIds: [NPC_IDS.linchuan] },
        token: session.accessToken,
      }),
    );
    const convId = String(created.conversationId);

    const invite = expectOk<{ addedUserIds: string[]; alreadyMemberIds: string[] }>(
      call({
        method: 'POST',
        url: `/convs/${convId}/members/invite`,
        body: { userIds: [NPC_IDS.linchuan, NPC_IDS.alan] },
        token: session.accessToken,
      }),
    );
    expect(invite.addedUserIds.map(String)).toEqual([NPC_IDS.alan]);
    expect(invite.alreadyMemberIds.map(String)).toEqual([NPC_IDS.linchuan]);

    expectOk(
      call({
        method: 'PUT',
        url: `/convs/${convId}/settings`,
        body: { isPinned: true, nickname: '组里的我' },
        token: session.accessToken,
      }),
    );
    // 契约 §5：GET 响应键为 muted/pinned（无 is- 前缀）；PUT 请求体仍是 isMuted/isPinned。
    const settings = expectOk<{ muted: boolean; pinned: boolean; nickname: string }>(
      call({ method: 'GET', url: `/convs/${convId}/settings`, token: session.accessToken }),
    );
    expect(settings).toMatchObject({ muted: false, pinned: true, nickname: '组里的我' });

    // 邀请产生了系统消息 → 未读，标记已读后归零。
    const detailBefore = expectOk<ConversationDTO>(call({ method: 'GET', url: `/convs/${convId}`, token: session.accessToken }));
    expect(detailBefore.maxSeq).toBeGreaterThan(0);
    expectOk(call({ method: 'PUT', url: `/convs/${convId}/read`, body: { seq: detailBefore.maxSeq }, token: session.accessToken }));
    const detailAfter = expectOk<ConversationDTO>(call({ method: 'GET', url: `/convs/${convId}`, token: session.accessToken }));
    expect(detailAfter.unreadCount).toBe(0);
  });

  it('permission rules: plain member cannot mute; owner cannot transfer to self', () => {
    const session = register('权限试探者');
    const conversations = expectOk<ListConversationsData>(call({ method: 'GET', url: '/convs', token: session.accessToken }));
    const bookClub = conversations.conversations.find((conversation) => conversation.name === '周末读书会');
    expect(bookClub).toBeDefined();
    const convId = String(bookClub?.id);

    const mute = call({
      method: 'PUT',
      url: `/convs/${convId}/members/${NPC_IDS.suwanqing}/mute`,
      body: { durationSeconds: 600 },
      token: session.accessToken,
    });
    expect(mute.envelope.code).toBe(30005);

    const myGroup = expectOk<{ conversationId: string }>(
      call({ method: 'POST', url: '/convs', body: { type: 2, name: '自转让测试组', memberIds: [NPC_IDS.alan] }, token: session.accessToken }),
    );
    const selfTransfer = call({
      method: 'POST',
      url: `/convs/${myGroup.conversationId}/transfer`,
      body: { newOwnerId: session.userId },
      token: session.accessToken,
    });
    expect(selfTransfer.envelope.code).toBe(30009);
  });
});

describe('messages', () => {
  function sendText(session: Session, convId: string, text: string, clientMsgId: string) {
    return call<SendMessageData>({
      method: 'POST',
      url: '/messages/send',
      body: { conversationId: convId, msgType: 1, content: { text }, clientMsgId },
      token: session.accessToken,
    });
  }

  it('send assigns increasing seq; duplicate clientMsgId hits 40004', () => {
    const session = register('话痨本痨');
    const conversations = expectOk<ListConversationsData>(call({ method: 'GET', url: '/convs', token: session.accessToken }));
    const convId = String(conversations.conversations[0]?.id);
    const before = conversations.conversations[0]?.maxSeq ?? 0;

    const first = expectOk<SendMessageData>(sendText(session, convId, '第一条', 'c-1'));
    expect(first.seq).toBe(before + 1);
    const second = expectOk<SendMessageData>(sendText(session, convId, '第二条', 'c-2'));
    expect(second.seq).toBe(before + 2);

    const duplicate = sendText(session, convId, '第一条', 'c-1');
    expect(duplicate.envelope.code).toBe(40004);
  });

  it('cursor pagination walks the full history without overlap', () => {
    const session = register('翻页人gg');
    const conversations = expectOk<ListConversationsData>(call({ method: 'GET', url: '/convs', token: session.accessToken }));
    const review = conversations.conversations.find((conversation) => conversation.name === 'AIM 前端评审组');
    const convId = String(review?.id);

    const seen = new Set<number>();
    let cursor = '0';
    for (let page = 0; page < 10; page += 1) {
      const data = expectOk<ListMessagesData>(
        call({ method: 'GET', url: `/messages/${convId}`, params: { cursor, limit: 2 }, token: session.accessToken }),
      );
      for (const message of data.list) {
        expect(seen.has(message.seq)).toBe(false);
        seen.add(message.seq);
      }
      if (!data.hasMore || !data.nextCursor) {
        break;
      }
      cursor = data.nextCursor;
    }
    expect(seen.size).toBe(review?.maxSeq);
  });

  it('recall respects the 120s window; edit rewrites content; deleteForMe hides locally', () => {
    const session = register('后悔药师');
    const conversations = expectOk<ListConversationsData>(call({ method: 'GET', url: '/convs', token: session.accessToken }));
    const convId = String(conversations.conversations[0]?.id);

    const sent = expectOk<SendMessageData>(sendText(session, convId, '打错字了', 'c-recall'));
    const messageId = String(sent.messageId);

    expectOk(call({ method: 'PUT', url: `/messages/${messageId}`, body: { newContent: { text: '改好了' } }, token: session.accessToken }));

    const sent2 = expectOk<SendMessageData>(sendText(session, convId, '这条马上撤回', 'c-recall-2'));
    expectOk(call({ method: 'POST', url: `/messages/${String(sent2.messageId)}/recall`, body: {}, token: session.accessToken }));

    const sent3 = expectOk<SendMessageData>(sendText(session, convId, '超时不能撤回', 'c-recall-3'));
    scheduler.advance(121_000);
    const lateRecall = call({ method: 'POST', url: `/messages/${String(sent3.messageId)}/recall`, body: {}, token: session.accessToken });
    expect(lateRecall.envelope.code).toBe(40002);

    expectOk(call({ method: 'DELETE', url: `/messages/${messageId}`, body: { deleteForAll: false }, token: session.accessToken }));
    const list = expectOk<ListMessagesData>(
      call({ method: 'GET', url: `/messages/${convId}`, params: { cursor: 0, limit: 50 }, token: session.accessToken }),
    );
    const ids = list.list.map((message) => String(message.messageId));
    expect(ids).not.toContain(messageId);
    const recalled = list.list.find((message) => String(message.messageId) === String(sent2.messageId));
    expect(recalled?.status).toBe(2);
    expect(recalled?.content).toEqual({});
  });

  it('sync returns messages after fromSeq in ascending order', () => {
    const session = register('断线侠客');
    const conversations = expectOk<ListConversationsData>(call({ method: 'GET', url: '/convs', token: session.accessToken }));
    const review = conversations.conversations.find((conversation) => conversation.name === 'AIM 前端评审组');
    const convId = String(review?.id);
    const fromSeq = (review?.maxSeq ?? 0) - 3;

    const data = expectOk<{ list: Array<{ seq: number }>; maxSeq: number }>(
      call({ method: 'GET', url: `/messages/${convId}/sync`, params: { fromSeq, limit: 50 }, token: session.accessToken }),
    );
    expect(data.list.map((message) => message.seq)).toEqual([fromSeq + 1, fromSeq + 2, fromSeq + 3]);
    expect(data.maxSeq).toBe(review?.maxSeq);
  });
});

describe('friends & notifications', () => {
  it('pending request from the seed world can be accepted into the friend list', () => {
    const session = register('社牛本牛');
    const pending = expectOk<{ list: Array<{ requestId: string; fromUsername: string }> }>(
      call({ method: 'GET', url: '/friends/requests/pending', token: session.accessToken }),
    );
    expect(pending.list.length).toBe(1);
    expect(pending.list[0]?.fromUsername).toBe('沈一帆');

    expectOk(call({ method: 'POST', url: `/friends/requests/${String(pending.list[0]?.requestId)}/accept`, body: {}, token: session.accessToken }));

    const friends = expectOk<{ list: Array<{ username: string }>; total: number }>(
      call({ method: 'GET', url: '/friends', params: { pageNum: 1, pageSize: 50 }, token: session.accessToken }),
    );
    expect(friends.list.map((friend) => friend.username)).toContain('沈一帆');
  });

  it('blocking a user removes the friendship and shows in blacklist', () => {
    const session = register('拉黑高手');
    expectOk(call({ method: 'POST', url: `/friends/blacklist/${NPC_IDS.linchuan}`, body: {}, token: session.accessToken }));

    const friends = expectOk<{ list: Array<{ username: string }> }>(
      call({ method: 'GET', url: '/friends', params: { pageNum: 1, pageSize: 50 }, token: session.accessToken }),
    );
    expect(friends.list.map((friend) => friend.username)).not.toContain('林川');

    const blacklist = expectOk<{ list: Array<{ username: string }> }>(
      call({ method: 'GET', url: '/friends/blacklist', token: session.accessToken }),
    );
    expect(blacklist.list.map((entry) => entry.username)).toContain('林川');
  });

  it('notification unread count decreases after marking read', () => {
    const session = register('通知控控');
    const before = expectOk<{ count: number }>(call({ method: 'GET', url: '/notifications/unread-count', token: session.accessToken }));
    expect(before.count).toBe(2);

    const list = expectOk<{ list: Array<{ id: string }> }>(call({ method: 'GET', url: '/notifications', token: session.accessToken }));
    expectOk(call({ method: 'POST', url: `/notifications/${String(list.list[0]?.id)}/read`, body: {}, token: session.accessToken }));

    const after = expectOk<{ count: number }>(call({ method: 'GET', url: '/notifications/unread-count', token: session.accessToken }));
    expect(after.count).toBe(1);
  });
});

describe('users & files', () => {
  it('search + batch use the documented parameter shapes', () => {
    const session = register('找人狂魔');
    const search = expectOk<{ users: Array<{ username: string }>; total: number }>(
      call({ method: 'POST', url: '/users/search?keyword=林&pageNum=1&pageSize=10', token: session.accessToken }),
    );
    expect(search.users.map((user) => user.username)).toContain('林川');

    const batch = expectOk<{ users: Array<{ id: string }> }>(
      call({ method: 'POST', url: '/users/batch', body: [NPC_IDS.linchuan, NPC_IDS.alan], token: session.accessToken }),
    );
    expect(batch.users).toHaveLength(2);
  });

  it('file three-step flow: upload-url → confirm → download', () => {
    const session = register('传文件的');
    const upload = expectOk<{ fileId: string; uploadUrl: string }>(
      call({
        method: 'POST',
        url: '/files/upload-url',
        body: { name: 'avatar.png', mimeType: 'image/png', size: 1024, purpose: 2, access: 3 },
        token: session.accessToken,
      }),
    );
    expect(upload.uploadUrl.startsWith('mock://upload/')).toBe(true);

    const confirmed = expectOk<{ file: { status: number } }>(
      call({ method: 'POST', url: '/files/confirm', body: { fileId: upload.fileId }, token: session.accessToken }),
    );
    expect(confirmed.file.status).toBe(1);

    const download = expectOk<{ downloadUrl: string }>(
      call({ method: 'GET', url: `/files/${String(upload.fileId)}/download`, token: session.accessToken }),
    );
    expect(download.downloadUrl.length).toBeGreaterThan(0);

    const oversize = call({
      method: 'POST',
      url: '/files/upload-url',
      body: { name: 'big.zip', mimeType: 'application/zip', size: 101 * 1024 * 1024, purpose: 1, access: 2 },
      token: session.accessToken,
    });
    expect(oversize.envelope.code).toBe(50003);
  });
});
