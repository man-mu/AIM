# 注册步骤表单 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将注册表单改为带逐步校验、回退保值和完成状态指示器的两步流程，同时不改变登录页、Mock 或注册接口契约。

**Architecture:** `RegisterForm` 保持一个 Ant Design `Form` 实例，并在组件内维护 `currentStep`。账号字段与联系方式字段按步骤条件渲染，表单实例负责保留字段值；`Register` 页面继续只负责将 `RegisterFormValues` 映射为 `RegisterParams`。

**Tech Stack:** React 19、TypeScript、Ant Design 6、`@ant-design/icons`、Vitest、React Testing Library、CSS。

---

## 文件结构

- 新建：`src/components/Auth/RegisterForm.test.tsx`，验证逐步校验、回退保值和最终提交。
- 修改：`src/components/Auth/RegisterForm.tsx`，加入步骤状态、可访问步骤指示器和上下步导航。
- 修改：`src/index.css`，加入仅以 `register-stepper` 为前缀的步骤样式，不改变共享登录样式。

### Task 1: 注册流程的失败测试

**Files:**
- Create: `src/components/Auth/RegisterForm.test.tsx`
- Reference: `src/components/Auth/RegisterForm.tsx`

- [ ] **Step 1: 写入描述两步流程的失败测试**

```tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
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

function completeAccountStep() {
  fireEvent.change(screen.getByLabelText(texts.username), { target: { value: 'new-user' } });
  fireEvent.change(screen.getByLabelText(texts.password), { target: { value: 'Secret123' } });
  fireEvent.change(screen.getByLabelText(texts.confirmPassword), { target: { value: 'Secret123' } });
  fireEvent.click(screen.getByRole('button', { name: texts.next }));
}

describe('RegisterForm', () => {
  it('stays on the account step when required account fields are invalid', async () => {
    render(<RegisterForm onSubmit={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: texts.next }));

    expect(await screen.findByText('\u8bf7\u8f93\u5165\u7528\u6237\u540d')).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: texts.account })).toHaveAttribute('aria-current', 'step');
    expect(screen.queryByLabelText(texts.phone)).not.toBeInTheDocument();
  });

  it('moves to contact details after the account step is valid', async () => {
    render(<RegisterForm onSubmit={vi.fn()} />);

    completeAccountStep();

    expect(await screen.findByLabelText(texts.phone)).toBeInTheDocument();
    expect(screen.getByRole('listitem', { name: texts.contact })).toHaveAttribute('aria-current', 'step');
  });

  it('preserves account values after returning from the contact step', async () => {
    render(<RegisterForm onSubmit={vi.fn()} />);

    completeAccountStep();
    await screen.findByLabelText(texts.phone);
    fireEvent.click(screen.getByRole('button', { name: texts.back }));

    expect(screen.getByLabelText(texts.username)).toHaveValue('new-user');
    expect(screen.getByLabelText(texts.password)).toHaveValue('Secret123');
  });

  it('submits all form values from the contact step', async () => {
    const onSubmit = vi.fn();
    render(<RegisterForm onSubmit={onSubmit} />);

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
});
```

- [ ] **Step 2: 验证测试失败，且失败原因是尚无步骤 UI**

Run: `pnpm exec vitest run src/components/Auth/RegisterForm.test.tsx`

Expected: FAIL，测试找不到“下一步”按钮与“账户信息”步骤语义。

### Task 2: 实现注册步骤状态和导航

**Files:**
- Modify: `src/components/Auth/RegisterForm.tsx`

- [ ] **Step 1: 用单一表单实例实现步骤切换与字段分组**

```tsx
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CheckOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Form, Input } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';

export interface RegisterFormValues {
  username: string;
  password: string;
  confirmPassword: string;
  phone?: string;
  email?: string;
}

interface RegisterFormProps {
  onSubmit: (values: RegisterFormValues) => void;
  loading?: boolean;
}

type RegisterStep = 0 | 1;

const accountFields: Array<keyof RegisterFormValues> = ['username', 'password', 'confirmPassword'];
const stepLabels = ['\u8d26\u6237\u4fe1\u606f', '\u8054\u7cfb\u65b9\u5f0f'];

export default function RegisterForm({ onSubmit, loading = false }: RegisterFormProps) {
  const [form] = Form.useForm<RegisterFormValues>();
  const [currentStep, setCurrentStep] = useState<RegisterStep>(0);

  const goToContactStep = async () => {
    try {
      await form.validateFields(accountFields);
      setCurrentStep(1);
    } catch {
      // Ant Design renders validation feedback for the current fields.
    }
  };

  return (
    <Form className="auth-form" form={form} name="registerForm" autoComplete="on" size="large" layout="vertical" onFinish={onSubmit}>
      <ol className={`register-stepper__list ${currentStep === 1 ? 'register-stepper__list--complete' : ''}`} aria-label="\u6ce8\u518c\u6b65\u9aa4">
        {stepLabels.map((label, index) => {
          const step = index as RegisterStep;
          const isComplete = step < currentStep;
          const isCurrent = step === currentStep;

          return (
            <li className={`register-stepper__item ${isCurrent ? 'is-current' : ''}`} key={label} aria-current={isCurrent ? 'step' : undefined}>
              <span className="register-stepper__node" aria-hidden="true">{isComplete ? <CheckOutlined /> : index + 1}</span>
              <span className="register-stepper__label">{label}</span>
            </li>
          );
        })}
      </ol>

      {currentStep === 0 ? (
        <>
          <Form.Item
            name="username"
            label="\u7528\u6237\u540d"
            rules={[
              { required: true, message: '\u8bf7\u8f93\u5165\u7528\u6237\u540d' },
              { min: 3, message: '\u7528\u6237\u540d\u81f3\u5c11 3 \u4e2a\u5b57\u7b26' },
              { max: 32, message: '\u7528\u6237\u540d\u6700\u591a 32 \u4e2a\u5b57\u7b26' },
            ]}
          >
            <Input autoComplete="username" prefix={<UserOutlined />} placeholder="\u7528\u6237\u540d\uff083-32 \u4e2a\u5b57\u7b26\uff09" />
          </Form.Item>
          <Form.Item
            name="password"
            label="\u5bc6\u7801"
            rules={[
              { required: true, message: '\u8bf7\u8f93\u5165\u5bc6\u7801' },
              { min: 6, message: '\u5bc6\u7801\u81f3\u5c11 6 \u4e2a\u5b57\u7b26' },
              { max: 32, message: '\u5bc6\u7801\u6700\u591a 32 \u4e2a\u5b57\u7b26' },
            ]}
          >
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="\u5bc6\u7801\uff086-32 \u4e2a\u5b57\u7b26\uff09" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="\u786e\u8ba4\u5bc6\u7801"
            dependencies={['password']}
            rules={[
              { required: true, message: '\u8bf7\u786e\u8ba4\u5bc6\u7801' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  return !value || getFieldValue('password') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="\u786e\u8ba4\u5bc6\u7801" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" block icon={<ArrowRightOutlined />} iconPosition="end" onClick={goToContactStep}>
              \u4e0b\u4e00\u6b65
            </Button>
          </Form.Item>
        </>
      ) : (
        <>
          <Form.Item name="phone" label="\u624b\u673a\u53f7" rules={[{ pattern: /^1\d{10}$/, message: '\u624b\u673a\u53f7\u683c\u5f0f\u4e0d\u6b63\u786e' }]}>
            <Input autoComplete="tel" inputMode="numeric" prefix={<PhoneOutlined />} placeholder="\u624b\u673a\u53f7\uff08\u9009\u586b\uff09" />
          </Form.Item>
          <Form.Item name="email" label="\u90ae\u7bb1" rules={[{ type: 'email', message: '\u90ae\u7bb1\u683c\u5f0f\u4e0d\u6b63\u786e' }]}>
            <Input autoComplete="email" prefix={<MailOutlined />} placeholder="\u90ae\u7bb1\uff08\u9009\u586b\uff09" />
          </Form.Item>
          <Form.Item className="register-stepper__actions">
            <Button disabled={loading} icon={<ArrowLeftOutlined />} onClick={() => setCurrentStep(0)}>
              \u4e0a\u4e00\u6b65
            </Button>
            <Button type="primary" htmlType="submit" loading={loading} icon={<ArrowRightOutlined />} iconPosition="end">
              \u521b\u5efa\u8d26\u6237
            </Button>
          </Form.Item>
        </>
      )}

      <div className="text-center">\u5df2\u6709\u8d26\u53f7\uff1f<Link to="/login">\u7acb\u5373\u767b\u5f55</Link></div>
    </Form>
  );
}
```

将注释位置替换为当前组件中原有的对应 `Form.Item` 定义，不改变规则、自动填充属性或字段名。联系人字段只有在第二步渲染；账号字段只有在第一步渲染。

- [ ] **Step 2: 运行组件测试，确认行为已通过**

Run: `pnpm exec vitest run src/components/Auth/RegisterForm.test.tsx`

Expected: PASS，4 个测试全部通过。

### Task 3: 增加隔离样式并完成验证

**Files:**
- Modify: `src/index.css`
- Test: `src/components/Auth/RegisterForm.test.tsx`

- [ ] **Step 1: 在现有认证样式之后加入仅针对步骤表单的样式**

```css
.register-stepper__list {
  position: relative;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0 0 28px;
  padding: 0;
  list-style: none;
}

.register-stepper__list::before,
.register-stepper__list::after {
  position: absolute;
  top: 14px;
  left: 25%;
  width: 50%;
  height: 1px;
  content: '';
}

.register-stepper__list::before { background: #d2d2d7; }
.register-stepper__list::after {
  background: #0071e3;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 180ms ease;
}
.register-stepper__list--complete::after { transform: scaleX(1); }

.register-stepper__item {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 6px;
  min-width: 0;
  color: #86868b;
  font-size: 12px;
  line-height: 1.2;
  text-align: center;
}
.register-stepper__node {
  z-index: 1;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid #d2d2d7;
  border-radius: 50%;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
}
.register-stepper__item:first-child .register-stepper__node,
.register-stepper__item.is-current .register-stepper__node {
  border-color: #0071e3;
  background: #0071e3;
  color: #fff;
}
.register-stepper__item.is-current { color: #1d1d1f; font-weight: 600; }
.register-stepper__actions { display: flex; gap: 12px; }
.register-stepper__actions .ant-btn { flex: 1; }

@media (max-width: 480px) {
  .register-stepper__list { margin-bottom: 24px; }
  .register-stepper__label { overflow-wrap: anywhere; }
}
```

- [ ] **Step 2: 执行全部前端检查**

Run: `pnpm test`

Expected: PASS，全部测试通过。

Run: `pnpm lint`

Expected: PASS，退出码为 0。

Run: `pnpm build`

Expected: PASS，TypeScript 编译和 Vite 构建完成。

- [ ] **Step 3: 在开发服务器手动检查三种状态**

Run: `pnpm dev -- --host 127.0.0.1 --port 5176`

Expected: 打开 `http://127.0.0.1:5176/register` 后可检查初始账号步骤、校验失败后不前进、填写完整后进入联系方式并可返回。打开 `/login` 确认登录视觉与字段未变；使用 Mock 凭据或新注册账号确认现有注册行为仍可用。

- [ ] **Step 4: 提交实现**

```bash
git add src/components/Auth/RegisterForm.tsx src/components/Auth/RegisterForm.test.tsx src/index.css
git commit -m "feat(auth): add registration stepper"
```
