import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { GuestOnlyRoute, RequireAuth } from './guards';
import { useAuthStore } from '@/stores/useAuthStore';

function renderRouter(initialEntry: string) {
  const router = createMemoryRouter(
    [
      {
        element: <GuestOnlyRoute />,
        children: [{ path: '/login', element: <div>Login page</div> }],
      },
      {
        element: <RequireAuth />,
        children: [{ path: '/home', element: <div>Home page</div> }],
      },
    ],
    { initialEntries: [initialEntry] },
  );

  render(<RouterProvider router={router} />);
}

describe('authentication route guards', () => {
  beforeEach(() => {
    useAuthStore.setState({ isLogin: false, user: null });
  });

  it('redirects an unauthenticated visitor from /home to /login', async () => {
    renderRouter('/home');

    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });

  it('redirects an authenticated visitor from /login to /home', async () => {
    useAuthStore.setState({ isLogin: true });
    renderRouter('/login');

    expect(await screen.findByText('Home page')).toBeInTheDocument();
  });
});
