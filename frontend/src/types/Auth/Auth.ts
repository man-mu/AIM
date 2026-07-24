import type {Int64, Tokens, UserInfo} from "../User/User.ts";

export type Platform = 'ios' | 'android' | 'web'

// POST /auth/login
export interface LoginParams {
    account: string,       // 用户名/手机号/邮箱
    password: string,
    deviceId: string,
    platform: Platform
}
export type LoginData = RegisterData
// POST /auth/logout
export interface LogoutParams {
    userId: Int64,
    tokenId: string
}
// POST /auth/register

export interface RegisterParams {
    username: string,     //  3~32 字符
    password: string,   // 6~32 字符
    phone?: string,
    email?: string,
    deviceId: string,   // 设备唯一标识
    platform: Platform
}
export interface RegisterData {
    userId: Int64,
    tokens: Tokens,
    user: UserInfo
}
// GET /auth/validate
export interface ValidateData {
    valid: boolean,
    userId: Int64,
    deviceId: string,
    expiresAt: number
}
