import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Form, Input } from 'antd';
import { Link } from 'react-router';

export interface LoginFormValues {
  account: string;
  password: string;
}

interface LoginFormProps {
  onSubmit: (values: LoginFormValues) => void;
  loading?: boolean;
}

export default function LoginForm({ onSubmit, loading = false }: LoginFormProps) {
  return (
    <Form className="auth-form" onFinish={onSubmit} name="loginForm" autoComplete="on" size="large" layout="vertical">
      <Form.Item name="account" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
        <Input
          autoComplete="username"
          prefix={<UserOutlined />}
          placeholder="用户名、手机号或邮箱"
        />
      </Form.Item>
      <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
        <Input.Password
          autoComplete="current-password"
          prefix={<LockOutlined />}
          placeholder="请输入密码"
        />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          登录
        </Button>
      </Form.Item>
      <div className="text-center">
        <Link to="/register">注册账号</Link>
      </div>
    </Form>
  );
}
