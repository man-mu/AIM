import { lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Navigate, Route } from 'react-router';
import App from '../App';
import { AppLayout } from '@/components/layout/AppLayout';
import { Spinner } from '@/components/ui/Spinner';
import Home from '../pages/Home';
import Login from '../pages/Login';
import Register from '../pages/Register';
import { GuestOnlyRoute, RequireAuth } from './guards';

/**
 * 路由结构：
 *   /login /register        —— 未登录（GuestOnly）
 *   /home/:conversationId?  —— 消息工作台（会话选中态由 URL 驱动）
 *   /contacts /notifications —— 联系人 / 通知（路由级代码分割）
 */
const Contacts = lazy(() => import('../pages/Contacts'));
const Notifications = lazy(() => import('../pages/Notifications'));

function LazyPage({ children }: { children: React.ReactNode }): React.JSX.Element {
  return (
    <Suspense
      fallback={
        <div className="grid h-full place-items-center">
          <Spinner size={20} />
        </div>
      }
    >
      {children}
    </Suspense>
  );
}

const routeElements = createRoutesFromElements(
  <Route path={'/'} element={<App />}>
    <Route index element={<Navigate to="/home" replace />} />
    <Route element={<GuestOnlyRoute />}>
      <Route path={'login'} element={<Login />} />
      <Route path={'register'} element={<Register />} />
    </Route>
    <Route element={<RequireAuth />}>
      <Route element={<AppLayout />}>
        <Route path={'home'} element={<Home />}>
          <Route index element={null} />
          <Route path={':conversationId'} element={null} />
        </Route>
        <Route
          path={'contacts'}
          element={
            <LazyPage>
              <Contacts />
            </LazyPage>
          }
        />
        <Route
          path={'notifications'}
          element={
            <LazyPage>
              <Notifications />
            </LazyPage>
          }
        />
      </Route>
    </Route>
  </Route>,
);

const router = createBrowserRouter(routeElements);

export default router;
