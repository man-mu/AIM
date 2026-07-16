import axios, {type AxiosError} from 'axios';
import JSONbig from 'json-bigint';
import {storage} from "../utils/storage.ts";
import {BASE_URL} from "@/constant";
import router from "../router";

const client = axios.create({
    baseURL: BASE_URL || 'https://localhost:3000',
    timeout: 10000,
    transformResponse: [
        (data) => {
        if (typeof data === 'string') {
            try {
                return JSONbig.parse(data);
            } catch {
                return data;
            }
        }
        return data;
        }
    ]
})
// 白名单机制
const WHITE_LIST = [
    '/auth/login',
    '/auth/register',
    '/auth/refresh',   // 刷新 Token 时用的是 RefreshToken，不是 AccessToken
    '/public/',        // 如果有些公开接口，可以用 includes 匹配
];
// 添加请求拦截器
client.interceptors.request.use(
    (config) => {
        const isWhiteListed = WHITE_LIST.some((path) => config.url?.includes(path));
        if (!isWhiteListed) {
            const token = storage.getAccessToken();
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => Promise.reject(error)
)
// 添加响应拦截器
client.interceptors.response.use(
    (response) => {
        const {code, message} = response.data;
        console.log('响应拦截器', code, message);
        if ([400, 401, 404].includes(code)) {
            return router.navigate('/login');
        }
        console.log('响应拦截器', response.data);
        return response.data;
    },
    (error: AxiosError) => {
        console.log('响应拦截器', error);
        if (error.response?.status === 401) {
            console.log('登录状态有误');
            storage.clearAuth();
            router.navigate('/login', { replace: true });
        } else if (!error.response) {
            console.log('网络错误');
        } else {
            console.log(error.response.status);
        }
        return Promise.reject(error);
    }
)

export default client;