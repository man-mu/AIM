import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import RegisterForm from './RegisterForm';

const texts = {
  account: '\u8d26\u6237\u4fe1\u606f',
  contact: '\u8054\u7cfb\u65b9\u5f0f',
  username: '\u7528\u6237\u540d',
  password: '\u5bc6\u7801',
  confirmPassword: '\u786e\u8ba4\u5bc6\u7801',
  phone: '\u624b\u673a\u53f7',
  email: '\u90ae\u7bb1',
  next: '\u4e0b\u4e00\u6b65',
  back: '\u4e0a\u4e00\u6b65',
  submit: '\u521b\u5efa\u8d26\u6237',
};

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

function completeAccountStep() {
  fireEvent.change(screen.getByLabelText(texts.username), { target: { value: 'new-user' } });
  fireEvent.change(screen.getByLabelText(texts.password), { target: { value: 'Secret123' } });
  fireEvent.change(screen.getByLabelText(texts.confirmPassword), { target: { value: 'Secret123' } });
  fireEvent.click(screen.getByRole('button', { name: texts.next }));
}

function renderRegisterForm(onSubmit = vi.fn()) {
  return render(
    <MemoryRouter>
      <RegisterForm onSubmit={onSubmit} />
    </MemoryRouter>,
  );
}

describe('RegisterForm', () => {
  it('stays on the account step when required account fields are invalid', async () => {
    renderRegisterForm();

    fireEvent.click(screen.getByRole('button', { name: texts.next }));

    expect(await screen.findByText('\u8bf7\u8f93\u5165\u7528\u6237\u540d')).toBeInTheDocument();
    expect(screen.getByText(texts.account).closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.queryByLabelText(texts.phone)).not.toBeInTheDocument();
  });

  it('moves to contact details after the account step is valid', async () => {
    renderRegisterForm();

    completeAccountStep();

    expect(await screen.findByLabelText(texts.phone)).toBeInTheDocument();
    expect(screen.getByText(texts.contact).closest('li')).toHaveAttribute('aria-current', 'step');
    expect(screen.getByText(texts.account).closest('li')).toHaveClass('is-complete');
    expect(screen.getByRole('list', { name: '\u6ce8\u518c\u6b65\u9aa4' })).toHaveClass(
      'register-stepper__list--complete',
    );
  });

  it('preserves account values after returning from the contact step', async () => {
    renderRegisterForm();

    completeAccountStep();
    await screen.findByLabelText(texts.phone);
    fireEvent.click(screen.getByRole('button', { name: texts.back }));

    expect(screen.getByLabelText(texts.username)).toHaveValue('new-user');
    expect(screen.getByLabelText(texts.password)).toHaveValue('Secret123');
  });

  it('submits all form values from the contact step', async () => {
    const onSubmit = vi.fn();
    renderRegisterForm(onSubmit);

    completeAccountStep();
    const phone = await screen.findByLabelText(texts.phone);
    fireEvent.change(phone, { target: { value: '13800138000' } });
    fireEvent.change(screen.getByLabelText(texts.email), { target: { value: 'new@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: texts.submit }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        username: 'new-user',
        password: 'Secret123',
        confirmPassword: 'Secret123',
        phone: '13800138000',
        email: 'new@example.com',
      }),
    );
  });

  it('submits without optional contact details', async () => {
    const onSubmit = vi.fn();
    renderRegisterForm(onSubmit);

    completeAccountStep();
    await screen.findByLabelText(texts.phone);
    fireEvent.click(screen.getByRole('button', { name: texts.submit }));

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          username: 'new-user',
          password: 'Secret123',
          confirmPassword: 'Secret123',
        }),
      ),
    );
  });

  it('blocks submission when contact details have an invalid format', async () => {
    const onSubmit = vi.fn();
    renderRegisterForm(onSubmit);

    completeAccountStep();
    const phone = await screen.findByLabelText(texts.phone);
    fireEvent.change(phone, { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: texts.submit }));

    expect(await screen.findByText('\u624b\u673a\u53f7\u683c\u5f0f\u4e0d\u6b63\u786e')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
