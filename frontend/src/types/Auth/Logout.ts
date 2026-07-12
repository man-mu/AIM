// POST /auth/logout
export interface LogoutReq {
    userId: number,
    tokenId: string
}
export interface LogoutRes {
    code: number,
    message: string,
    data: {
        list: String[],
        total: number,
        pageNum: number,
        pageSize: number
    }
}
