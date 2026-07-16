import client from "./client.ts";
import type {ApiResponse} from "../types/Common.ts";
import type {Method} from "axios";

export const request = <T>(
    url: string,
    method: Method = 'GET',
    submitData?: object,
    options?: { signal?: AbortSignal }
): Promise<T> => {
    return client.request<ApiResponse<T>>({
        url,
        method,
        ...(method.toUpperCase() === 'GET'
            ? { params: submitData }
            : { data: submitData }
        ),
        signal: options?.signal
    }).then((res) => {
        const {code, message, data} = res.data;
        if (code !== 0) {
            throw new Error(message);
        }
        return data;
    });
}
