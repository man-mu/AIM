// POST /Auth/login
export interface LoginParams {
    account: string,       // 用户名/手机号/邮箱
    password: string,
    deviceId: string,
    platform: string
}
export type LoginData = RegisterData
// POST /auth/logout
export interface LogoutParams {
    userId: number,
    tokenId: string
}
// POST /Auth/register
import type {Tokens, UserInfo} from "../User/User.ts";

export interface RegisterParams {
    username: string,     //  3~32 字符
    password: string,   // 6~32 字符
    phone?: number,
    email?: string,
    deviceId: string,   // 设备唯一标识
    platform: string
}
export interface RegisterData {
    userId: number,
    tokens: Tokens,
    user: UserInfo
}
// GET /auth/validata
export interface ValidateData {
    valid: boolean,
    userId: number,
    deviceId: string,
    expiresAt: number
}