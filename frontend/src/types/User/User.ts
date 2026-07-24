export type Int64 = string | number

export type Tokens = {
    accessToken: string,
    refreshToken: string,
    accessExpire: number,
    refreshExpire: number
}
export type UserInfo = {
    id: Int64,
    username: string,
    phone: string | number,
    email: string,
    avatar: string,
    gender: Gender,
    bio: string,
    birthday: number | string,
    createdAt: number,
    updatedAt: number,
    balance: number
}
export type Gender = 0 | 1 | 2
// GET /users/me
export type ProfileData = UserInfo
// PUT /users/me
export interface UpdateParams {
    avatar: string,
    gender: Gender,
    bio: string,
    birthday: number
}
export type UpdateData = UserInfo
