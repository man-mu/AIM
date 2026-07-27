import * as conversations from './conversations';
import * as files from './files';
import * as messages from './messages';
import { bootstrapWorldFor, seedNpcUsers } from './seed';
import * as social from './social';
import { createEmptyState, hydrate, serialize, type DbState } from './state';
import * as users from './users';
import type { DbSnapshot } from './schema';

export { MockDbError } from './users';
export { RECALL_WINDOW_MS, EDIT_WINDOW_MS } from './messages';
export { NPC_IDS } from './seed';
export type { DbSnapshot } from './schema';

/**
 * MockDb 门面：把按领域拆分的纯函数绑定到同一份 DbState 上。
 *
 * 用法（handler 侧）：
 *   db.users.verifyPassword(account, password)
 *   db.convs.toConversationDTO(row, userId)
 *
 * 测试可直接 new MockDb() 注入固定 now，实现全确定性验证。
 */
export class MockDb {
  private state: DbState;

  constructor(snapshot?: DbSnapshot | null) {
    this.state = (snapshot ? hydrate(snapshot) : null) ?? createEmptyState();
  }

  serialize(): DbSnapshot {
    return serialize(this.state);
  }

  /** 初始化 NPC 阵容（幂等）。 */
  seedNpcs(now: number): void {
    seedNpcUsers(this.state, now);
  }

  /** 是否已为该用户搭建初始世界。 */
  hasWorldFor(userId: string): boolean {
    return conversations.listConversationRowsFor(this.state, userId).length > 0;
  }

  bootstrapWorldFor(userId: string, now: number): void {
    if (!this.hasWorldFor(userId)) {
      bootstrapWorldFor(this.state, userId, now);
    }
  }

  // —— 领域命名空间（方法均已绑定 state）——

  readonly users = {
    create: (input: users.CreateUserInput, now: number) => users.createUser(this.state, input, now),
    get: (userId: string) => users.getUser(this.state, userId),
    require: (userId: string) => users.requireUser(this.state, userId),
    findByAccount: (account: string) => users.findUserByAccount(this.state, account),
    verifyPassword: (account: string, password: string) => users.verifyPassword(this.state, account, password),
    updateProfile: (userId: string, patch: users.UpdateProfileInput, now: number) =>
      users.updateProfile(this.state, userId, patch, now),
    updatePassword: (userId: string, oldPwd: string, newPwd: string, now: number) =>
      users.updatePassword(this.state, userId, oldPwd, newPwd, now),
    search: (keyword: string, pageNum: number, pageSize: number) =>
      users.searchUsers(this.state, keyword, pageNum, pageSize),
    listByIds: (ids: string[]) => users.listUsersByIds(this.state, ids),
    toUserInfo: users.toUserInfo,
  };

  readonly convs = {
    get: (conversationId: string) => conversations.getConversation(this.state, conversationId),
    require: (conversationId: string) => conversations.requireConversation(this.state, conversationId),
    createDirect: (creatorId: string, peerUserId: string, now: number) =>
      conversations.createDirectConversation(this.state, creatorId, peerUserId, now),
    createGroup: (creatorId: string, input: { name: string; avatar?: string; memberIds?: string[] }, now: number) =>
      conversations.createGroupConversation(this.state, creatorId, input, now),
    listFor: (userId: string) => conversations.listConversationRowsFor(this.state, userId),
    getMember: (conversationId: string, userId: string) => conversations.getMember(this.state, conversationId, userId),
    requireMember: (conversationId: string, userId: string) =>
      conversations.requireMember(this.state, conversationId, userId),
    listMembers: (conversationId: string) => conversations.listMembersOf(this.state, conversationId),
    memberCount: (conversationId: string) => conversations.memberCountOf(this.state, conversationId),
    addMembers: (conversationId: string, operatorId: string, userIds: string[], now: number) =>
      conversations.addMembers(this.state, conversationId, operatorId, userIds, now),
    kickMembers: (conversationId: string, operatorId: string, userIds: string[], now: number) =>
      conversations.kickMembers(this.state, conversationId, operatorId, userIds, now),
    muteMember: (conversationId: string, operatorId: string, userId: string, durationSeconds: number, now: number) =>
      conversations.muteMember(this.state, conversationId, operatorId, userId, durationSeconds, now),
    unmuteMember: (conversationId: string, operatorId: string, userId: string, now: number) =>
      conversations.unmuteMember(this.state, conversationId, operatorId, userId, now),
    transferOwner: (conversationId: string, operatorId: string, newOwnerId: string, now: number) =>
      conversations.transferOwner(this.state, conversationId, operatorId, newOwnerId, now),
    setAnnouncement: (conversationId: string, operatorId: string, content: string, now: number) =>
      conversations.setAnnouncement(this.state, conversationId, operatorId, content, now),
    getSettings: (conversationId: string, userId: string) => conversations.getSettings(this.state, conversationId, userId),
    updateSettings: (conversationId: string, userId: string, patch: { isMuted?: boolean; isPinned?: boolean; nickname?: string }) =>
      conversations.updateSettings(this.state, conversationId, userId, patch),
    markRead: (conversationId: string, userId: string, seq: number) =>
      conversations.markRead(this.state, conversationId, userId, seq),
    directPeerOf: (conversationId: string, selfId: string) => conversations.directPeerOf(this.state, conversationId, selfId),
    toDTO: (row: NonNullable<ReturnType<typeof conversations.getConversation>>, forUserId: string) =>
      conversations.toConversationDTO(this.state, row, forUserId),
    toMemberDTO: (member: NonNullable<ReturnType<typeof conversations.getMember>>) =>
      conversations.toMemberDTO(this.state, member),
  };

  readonly messages = {
    append: (input: messages.AppendMessageInput, now: number) => messages.appendMessage(this.state, input, now),
    list: (conversationId: string, userId: string, cursorSeq: number, limit: number) =>
      messages.listMessages(this.state, conversationId, userId, cursorSeq, limit),
    sync: (conversationId: string, userId: string, fromSeq: number, limit: number) =>
      messages.syncMessages(this.state, conversationId, userId, fromSeq, limit),
    require: (messageId: string) => messages.requireMessage(this.state, messageId),
    findByClientMsgId: (conversationId: string, clientMsgId: string) =>
      messages.findByClientMsgId(this.state, conversationId, clientMsgId),
    recall: (messageId: string, operatorId: string, now: number) =>
      messages.recallMessage(this.state, messageId, operatorId, now),
    edit: (messageId: string, operatorId: string, newContent: Parameters<typeof messages.editMessage>[3], now: number) =>
      messages.editMessage(this.state, messageId, operatorId, newContent, now),
    delete: (messageId: string, operatorId: string, deleteForAll: boolean) =>
      messages.deleteMessage(this.state, messageId, operatorId, deleteForAll),
    previewOf: messages.previewOf,
    toDTO: messages.toMessageDTO,
  };

  readonly social = {
    areFriends: (a: string, b: string) => social.areFriends(this.state, a, b),
    isBlocked: (ownerId: string, targetId: string) => social.isBlocked(this.state, ownerId, targetId),
    addFriendPair: (a: string, b: string, now: number) => social.addFriendPair(this.state, a, b, now),
    removeFriendPair: (a: string, b: string) => social.removeFriendPair(this.state, a, b),
    listFriends: (ownerId: string, groupId?: string) => social.listFriends(this.state, ownerId, groupId),
    setFriendRemark: (ownerId: string, friendId: string, remark: string) =>
      social.setFriendRemark(this.state, ownerId, friendId, remark),
    moveFriendToGroup: (ownerId: string, friendId: string, groupId: string) =>
      social.moveFriendToGroup(this.state, ownerId, friendId, groupId),
    createRequest: (fromUserId: string, toUserId: string, message: string, now: number) =>
      social.createFriendRequest(this.state, fromUserId, toUserId, message, now),
    acceptRequest: (requestId: string, operatorId: string, now: number) =>
      social.acceptFriendRequest(this.state, requestId, operatorId, now),
    rejectRequest: (requestId: string, operatorId: string, now: number) =>
      social.rejectFriendRequest(this.state, requestId, operatorId, now),
    cancelRequest: (requestId: string, operatorId: string, now: number) =>
      social.cancelFriendRequest(this.state, requestId, operatorId, now),
    listRequests: (userId: string, direction: 'incoming' | 'outgoing') =>
      social.listFriendRequests(this.state, userId, direction),
    createGroup: (ownerId: string, name: string, now: number) => social.createFriendGroup(this.state, ownerId, name, now),
    renameGroup: (ownerId: string, groupId: string, name: string) =>
      social.renameFriendGroup(this.state, ownerId, groupId, name),
    deleteGroup: (ownerId: string, groupId: string) => social.deleteFriendGroup(this.state, ownerId, groupId),
    listGroups: (ownerId: string) => social.listFriendGroups(this.state, ownerId),
    block: (ownerId: string, targetId: string, now: number) => social.blockUser(this.state, ownerId, targetId, now),
    unblock: (ownerId: string, targetId: string) => social.unblockUser(this.state, ownerId, targetId),
    listBlacklist: (ownerId: string) => social.listBlacklist(this.state, ownerId),
    pushNotification: (input: social.PushNotificationInput, now: number) =>
      social.pushNotification(this.state, input, now),
    listNotifications: (userId: string, filter: { type?: number; isRead?: boolean }) =>
      social.listNotifications(this.state, userId, filter),
    unreadNotificationCount: (userId: string) => social.unreadNotificationCount(this.state, userId),
    markNotificationRead: (userId: string, notificationId: string) =>
      social.markNotificationRead(this.state, userId, notificationId),
    markAllNotificationsRead: (userId: string) => social.markAllNotificationsRead(this.state, userId),
    deleteNotification: (userId: string, notificationId: string) =>
      social.deleteNotification(this.state, userId, notificationId),
    presenceOf: (userId: string) => social.presenceOf(this.state, userId),
  };

  readonly files = {
    createPending: (input: files.CreatePendingFileInput, now: number) => files.createPendingFile(this.state, input, now),
    require: (fileId: string) => files.requireFile(this.state, fileId),
    confirm: (fileId: string, md5: string | undefined, meta?: { width?: number; height?: number }) =>
      files.confirmFile(this.state, fileId, md5, meta),
    delete: (fileId: string, operatorId: string) => files.deleteFile(this.state, fileId, operatorId),
    listByIds: (ids: string[]) => files.listFilesByIds(this.state, ids),
  };
}
