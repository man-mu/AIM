import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuthStore } from '@/stores/useAuthStore';

export function RequireAuth() {
  const isLogin = useAuthStore((state) => state.isLogin);
  const location = useLocation();

  if (!isLogin) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}

export function GuestOnlyRoute() {
  const isLogin = useAuthStore((state) => state.isLogin);

  return isLogin ? <Navigate to="/home" replace /> : <Outlet />;
}
