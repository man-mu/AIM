import type { ApiResponse } from "@/types/Common";
import { mockLogin, mockRegister, mockValidate, mockGetProfile } from "./auth";

type MockHandler = (body: Record<string, unknown>) => ApiResponse<unknown>;

export const mockHandlers: Record<string, MockHandler> = {
    "/auth/login": mockLogin as MockHandler,
    "/auth/register": mockRegister as MockHandler,
    "/auth/validate": mockValidate as MockHandler,
    "/users/me": mockGetProfile as MockHandler,
};

export const shouldMock = (): boolean => {
    return import.meta.env.VITE_USE_MOCK === "true";
};