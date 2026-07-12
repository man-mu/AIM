import type {RegisterRes} from "./Register.ts";

// POST /Auth/login
export interface LoginReq {
    account: String,       // 用户名/手机号/邮箱
    password: String,
    deviceId: String,
    platform: String
}
export type LoginRes = RegisterRes