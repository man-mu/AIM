export type Tokens = {
    accessToken: string,
    refreshToken: string,
    accessExpire: number,
    refreshExpire: number
}
export type UserInfo = {
    id: Number,
    username: String,
    phone: Number,
    email: String,
    avatar: String,
    gender: Gender,
    bio: String,
    birthday: String,
    createdAt: Number,
    updatedAt: Number,
    balance: Number
}

export type Gender = 0 | 1 | 2