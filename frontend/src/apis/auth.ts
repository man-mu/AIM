import {request} from "./request.ts";
import type {
    LoginData,
    LoginParams,
    LogoutParams,
    RegisterData,
    RegisterParams,
    ValidateData
} from "@/types/Auth/Auth.ts";
export const authApi = {
    register: (data: RegisterParams) => {
        return request<RegisterData>('/auth/register', 'POST', data);
    },
    login: (data: LoginParams) => {
        return request<LoginData>('/auth/login', 'POST', data)
    },
    logout: (data: LogoutParams) => {
        return request('/auth/logout', 'POST',data)
    },
    validate: () => {
        return request<ValidateData>('/auth/validate', 'GET')
    }
}