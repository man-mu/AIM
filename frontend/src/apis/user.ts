import {request} from "@/apis/request.ts";
import type {ProfileData, UpdateData, UpdateParams} from "@/types/User/User.ts";

export const userApi = {
    getProfile: () => {
        return request<ProfileData>('/users/me', 'GET')
    },
    updateProfile: (data: UpdateParams) => {
        return request<UpdateData>('/users/me', 'PUT', data)
    }
}