/**
 * Int64 承接约定：后端 Java long 经 json-bigint(storeAsString) 反序列化，
 * 大数为 string、小数为 number —— wire 层用该联合类型，UI 层经 mapper
 * 统一 toInt64String 归一为 string 后使用。
 */
export type Int64 = string | number;

export type Tokens = {
  accessToken: string;
  refreshToken: string;
  accessExpire: number;
  refreshExpire: number;
};

export type Gender = 0 | 1 | 2;

/** UserInfo（api-v1-implemented.md 附录 A）。 */
export type UserInfo = {
  id: Int64;
  username: string;
  phone: string;
  email: string;
  avatar: string;
  gender: Gender;
  bio: string;
  birthday: number;
  createdAt: number;
  updatedAt: number;
  /** BigDecimal 序列化为 plain string。 */
  balance: string | number;
};

// GET /users/me
export type ProfileData = UserInfo;

// PUT /users/me（所有字段可选，传什么更新什么）
export interface UpdateParams {
  avatar?: string;
  gender?: Gender;
  bio?: string;
  birthday?: number;
  phone?: string;
  email?: string;
}
export type UpdateData = UserInfo;

// PUT /users/me/password
export interface UpdatePasswordParams {
  oldPassword: string;
  newPassword: string;
}

// POST /users/batch（请求体直接是 Int64[]）
export interface BatchGetUsersData {
  users: UserInfo[];
}

// POST /users/search?keyword=&pageNum=&pageSize=
export interface SearchUsersParams {
  keyword: string;
  pageNum?: number;
  pageSize?: number;
}
export interface SearchUsersData {
  users: UserInfo[];
  total: number;
}
