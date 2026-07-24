import { create } from 'zustand';
import { hasValidAuthSession } from '@/utils/authSession';
import type { UserInfo } from '@/types/User/User.ts';

interface AuthState {
    isLogin: boolean;
    user: UserInfo | null;
    setAuth: (user: UserInfo) => void;
    clearAuth: () => void;
    hydrate: () => void;
}
export const useAuthStore = create<AuthState>((set) => ({
    isLogin: hasValidAuthSession(),
    user: null,
    setAuth: (user) => set({ isLogin: true, user }),
    clearAuth: () => set({ isLogin: false, user: null }),
    hydrate: () => set({ isLogin: hasValidAuthSession() }),
}));
