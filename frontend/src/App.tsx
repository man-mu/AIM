import { useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router';
import { appBus } from '@/app/appBus';
import { Toaster } from '@/components/ui/toast/Toaster';
import { toast } from '@/components/ui/toast/toastBus';

/**
 * 登录态失效的全局出口：API 层只 emit 事件，这里统一「提示 + 跳转」。
 */
function AuthExpiryListener(): null {
  const navigate = useNavigate();

  useEffect(() => {
    return appBus.on('auth:expired', () => {
      toast.error('登录已过期，请重新登录');
      void navigate('/login', { replace: true });
    });
  }, [navigate]);

  return null;
}

function App() {
  return (
    <>
      <AuthExpiryListener />
      <Outlet />
      <Toaster />
    </>
  );
}

export default App;
