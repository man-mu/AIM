// POST /Auth/register
import type {Tokens, UserInfo} from "../User/User.ts";

export interface RegisterReq {
    username: String,     //  3~32 字符
    password: String,   // 6~32 字符
    phone?: Number,
    email?: String,
    deviceId: String,   // 设备唯一标识
    platform: String
}
export interface RegisterRes {
    code: Number,
    message: String,
    data: {
        userId: Number,
        tokens: Tokens,
        user: UserInfo
    }
}