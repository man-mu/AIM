import type { ReactNode } from 'react';

interface AuthLayoutProps {
  children?: ReactNode;
  title?: string;
}

export default function AuthLayout({ children, title }: AuthLayoutProps) {
  return (
    <main className="auth-page">
      <header className="auth-brand" aria-label="AIM">
        AIM
      </header>
      <section className="auth-panel" aria-label={title}>
        <p className="auth-eyebrow">AIM</p>
        <h1>{title}</h1>
        {children}
      </section>
    </main>
  );
}
