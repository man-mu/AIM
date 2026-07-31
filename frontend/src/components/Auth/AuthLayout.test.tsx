import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AuthLayout from './AuthLayout';

describe('AuthLayout', () => {
  it('provides a named authentication region around its content', () => {
    render(
      <AuthLayout title="Sign in">
        <form aria-label="Sign in form" />
      </AuthLayout>,
    );

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Sign in' })).toContainElement(
      screen.getByRole('form', { name: 'Sign in form' }),
    );
  });
});
