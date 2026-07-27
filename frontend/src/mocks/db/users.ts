import type { UserInfo } from '@/types/User/User';
import { nextId, type DbState } from './state';
import type { UserRow } from './schema';

/** 用户表操作。抛错约定：携带业务错误码的 MockDbError。 */
export class MockDbError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'MockDbError';
    this.code = code;
  }
}

export interface CreateUserInput {
  username: string;
  password: string;
  phone?: string;
  email?: string;
  isNpc?: boolean;
  avatar?: string;
  bio?: string;
  gender?: 0 | 1 | 2;
  /** 固定 id（NPC 剧本用）。 */
  id?: string;
}

export function createUser(state: DbState, input: CreateUserInput, now: number): UserRow {
  if ([...state.users.values()].some((user) => user.username === input.username)) {
    throw new MockDbError(10002, '用户名已存在');
  }
  if (input.phone && [...state.users.values()].some((user) => user.phone === input.phone)) {
    throw new MockDbError(10003, '手机号已被注册');
  }

  const row: UserRow = {
    id: input.id ?? nextId(state, 'user'),
    username: input.username,
    password: input.password,
    phone: input.phone ?? '',
    email: input.email ?? '',
    avatar: input.avatar ?? '',
    gender: input.gender ?? 0,
    bio: input.bio ?? '',
    birthday: 0,
    createdAt: now,
    updatedAt: now,
    balance: '0',
    disabled: false,
    isNpc: input.isNpc ?? false,
  };
  state.users.set(row.id, row);
  return row;
}

export function getUser(state: DbState, userId: string): UserRow | null {
  return state.users.get(userId) ?? null;
}

export function requireUser(state: DbState, userId: string): UserRow {
  const user = state.users.get(userId);
  if (!user) {
    throw new MockDbError(10001, '用户不存在');
  }
  return user;
}

/** 账号可为用户名 / 手机号 / 邮箱。 */
export function findUserByAccount(state: DbState, account: string): UserRow | null {
  for (const user of state.users.values()) {
    if (user.username === account || (user.phone && user.phone === account) || (user.email && user.email === account)) {
      return user;
    }
  }
  return null;
}

export function verifyPassword(state: DbState, account: string, password: string): UserRow {
  const user = findUserByAccount(state, account);
  if (!user) {
    throw new MockDbError(10001, '用户不存在');
  }
  if (user.disabled) {
    throw new MockDbError(10008, '用户被禁用');
  }
  if (user.password !== password) {
    throw new MockDbError(10004, '密码错误');
  }
  return user;
}

export interface UpdateProfileInput {
  avatar?: string;
  gender?: 0 | 1 | 2;
  bio?: string;
  birthday?: number;
  phone?: string;
  email?: string;
}

export function updateProfile(state: DbState, userId: string, patch: UpdateProfileInput, now: number): UserRow {
  const user = requireUser(state, userId);
  if (patch.avatar !== undefined) user.avatar = patch.avatar;
  if (patch.gender !== undefined) user.gender = patch.gender;
  if (patch.bio !== undefined) user.bio = patch.bio;
  if (patch.birthday !== undefined) user.birthday = patch.birthday;
  if (patch.phone !== undefined) user.phone = patch.phone;
  if (patch.email !== undefined) user.email = patch.email;
  user.updatedAt = now;
  return user;
}

export function updatePassword(state: DbState, userId: string, oldPassword: string, newPassword: string, now: number): void {
  const user = requireUser(state, userId);
  if (user.password !== oldPassword) {
    throw new MockDbError(10004, '密码错误');
  }
  user.password = newPassword;
  user.updatedAt = now;
}

export interface UserSearchResult {
  users: UserRow[];
  total: number;
}

export function searchUsers(state: DbState, keyword: string, pageNum: number, pageSize: number): UserSearchResult {
  const normalized = keyword.trim().toLowerCase();
  const matched = normalized
    ? [...state.users.values()].filter(
        (user) =>
          user.username.toLowerCase().includes(normalized) ||
          user.phone.includes(normalized) ||
          user.email.toLowerCase().includes(normalized),
      )
    : [];
  const start = (Math.max(1, pageNum) - 1) * pageSize;
  return { users: matched.slice(start, start + pageSize), total: matched.length };
}

export function listUsersByIds(state: DbState, ids: string[]): UserRow[] {
  return ids.map((id) => state.users.get(id)).filter((user): user is UserRow => Boolean(user));
}

/** UserRow → 对外 UserInfo（隐藏 password / isNpc / disabled）。 */
export function toUserInfo(user: UserRow): UserInfo {
  return {
    id: user.id,
    username: user.username,
    phone: user.phone,
    email: user.email,
    avatar: user.avatar,
    gender: user.gender,
    bio: user.bio,
    birthday: user.birthday,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
    balance: user.balance,
  };
}
