import type { ApiResponse } from "@/types/Common";
import type { LoginData, RegisterData, ValidateData } from "@/types/Auth/Auth";
import type { ProfileData } from "@/types/User/User";

const REGISTERED_USERS_KEY = "aim_mock_registered_users";
const CURRENT_USER_KEY = "aim_mock_current_user";

const mockTokens = {
    accessToken: "mock_access_token_eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEwMDAxfQ.signature",
    refreshToken: "mock_refresh_token_eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEwMDAxfQ.signature",
    accessExpire: Date.now() + 3600000,
    refreshExpire: Date.now() + 604800000,
};

type MockUser = {
    id: number;
    username: string;
    phone: number;
    email: string;
    avatar: string;
    gender: 0 | 1 | 2;
    bio: string;
    birthday: string;
    createdAt: number;
    updatedAt: number;
    balance: number;
};

const mockUser: MockUser = {
    id: 10001,
    username: "admin",
    phone: 13800138000,
    email: "admin@aim.local",
    avatar: "",
    gender: 0 as const,
    bio: "Mock 用户",
    birthday: "2000-01-01",
    createdAt: Date.now(),
    updatedAt: Date.now(),
    balance: 100.0,
};

function loadRegisteredUsers(): Map<string, { password: string; user: MockUser }> {
    try {
        const raw = localStorage.getItem(REGISTERED_USERS_KEY);
        if (raw) {
            const entries: Array<[string, { password: string; user: MockUser }]> = JSON.parse(raw);
            return new Map(entries);
        }
    } catch {
        // ignore parse errors
    }
    return new Map();
}

function saveRegisteredUsers(map: Map<string, { password: string; user: MockUser }>) {
    try {
        const entries = Array.from(map.entries());
        localStorage.setItem(REGISTERED_USERS_KEY, JSON.stringify(entries));
    } catch {
        // ignore storage errors
    }
}

function loadCurrentUser(): MockUser | null {
    try {
        const raw = localStorage.getItem(CURRENT_USER_KEY);
        if (raw) {
            return JSON.parse(raw) as MockUser;
        }
    } catch {
        // ignore parse errors
    }
    return null;
}

function saveCurrentUser(user: MockUser | null) {
    try {
        if (user) {
            localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user));
        } else {
            localStorage.removeItem(CURRENT_USER_KEY);
        }
    } catch {
        // ignore storage errors
    }
}

const registeredUsers = loadRegisteredUsers();

export function mockLogin(body: Record<string, unknown>): ApiResponse<LoginData> {
    const { account, password } = body as { account?: string; password?: string };

    if (!account || !password) {
        return { code: 400, message: "账户和密码不能为空", data: null as unknown as LoginData };
    }

    const makeSuccess = (user: MockUser) => {
        saveCurrentUser(user);
        return {
            code: 0,
            message: "登录成功",
            data: {
                userId: user.id,
                tokens: mockTokens,
                user: { ...user },
            },
        };
    };

    const registered = registeredUsers.get(account);
    if (registered && registered.password === password) {
        return makeSuccess(registered.user);
    }

    if (account === "admin" && password === "admin123") {
        return makeSuccess({ ...mockUser, username: "admin" });
    }

    if (account === "test" && password === "test123") {
        return makeSuccess({ ...mockUser, id: 10002, username: "test" });
    }

    return { code: 401, message: "账户或密码错误", data: null as unknown as LoginData };
}

export function mockRegister(body: Record<string, unknown>): ApiResponse<RegisterData> {
    const { username, password } = body as { username?: string; password?: string };

    if (!username || !password) {
        return { code: 400, message: "用户名和密码不能为空", data: null as unknown as RegisterData };
    }

    if (typeof username === "string" && (username.length < 3 || username.length > 32)) {
        return { code: 400, message: "用户名需 3-32 个字符", data: null as unknown as RegisterData };
    }

    if (typeof password === "string" && (password.length < 6 || password.length > 32)) {
        return { code: 400, message: "密码需 6-32 个字符", data: null as unknown as RegisterData };
    }

    if (registeredUsers.has(username)) {
        return { code: 400, message: "用户名已存在", data: null as unknown as RegisterData };
    }

    const userId = Date.now();
    const user = { ...mockUser, id: userId, username, phone: Date.now() % 10000000000, email: `${username}@aim.local` };
    registeredUsers.set(username, { password, user });
    saveRegisteredUsers(registeredUsers);

    return {
        code: 0,
        message: "注册成功",
        data: {
            userId,
            tokens: mockTokens,
            user,
        },
    };
}

export function mockValidate(): ApiResponse<ValidateData> {
    const currentUser = loadCurrentUser();
    return {
        code: 0,
        message: "Token 有效",
        data: {
            valid: true,
            userId: currentUser?.id ?? mockUser.id,
            deviceId: "mock-device-id",
            expiresAt: Date.now() + 3600000,
        },
    };
}

export function mockGetProfile(): ApiResponse<ProfileData> {
    const currentUser = loadCurrentUser();
    if (!currentUser) {
        return { code: 401, message: "未登录", data: null as unknown as ProfileData };
    }
    return {
        code: 0,
        message: "获取成功",
        data: { ...currentUser },
    };
}