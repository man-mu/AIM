import { LockOutlined, MailOutlined, PhoneOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Form, Input } from 'antd';
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

export default function RegisterForm({ onSubmit, loading = false }: RegisterFormProps) {
  const [form] = Form.useForm<RegisterFormValues>();

  return (
    <Form
      form={form}
      onFinish={onSubmit}
      name="registerForm"
      autoComplete="on"
      size="large"
      layout="vertical"
    >
      <Form.Item
        name="username"
        label="用户名"
        rules={[
          { required: true, message: '请输入用户名' },
          { min: 3, message: '用户名至少 3 个字符' },
          { max: 32, message: '用户名最多 32 个字符' },
        ]}
      >
        <Input autoComplete="username" prefix={<UserOutlined />} placeholder="用户名（3-32 个字符）" />
      </Form.Item>

      <Form.Item
        name="password"
        label="密码"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 6, message: '密码至少 6 个字符' },
          { max: 32, message: '密码最多 32 个字符' },
        ]}
      >
        <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="密码（6-32 个字符）" />
      </Form.Item>

      <Form.Item
        name="confirmPassword"
        label="确认密码"
        dependencies={['password']}
        rules={[
          { required: true, message: '请确认密码' },
          ({ getFieldValue }) => ({
            validator(_, value) {
              if (!value || getFieldValue('password') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="确认密码" />
      </Form.Item>

      <Form.Item name="phone" label="手机号" rules={[{ pattern: /^1\d{10}$/, message: '手机号格式不正确' }]}>
        <Input
          autoComplete="tel"
          inputMode="numeric"
          prefix={<PhoneOutlined />}
          placeholder="手机号（选填）"
        />
      </Form.Item>

      <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}>
        <Input autoComplete="email" prefix={<MailOutlined />} placeholder="邮箱（选填）" />
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={loading} block>
          注册
        </Button>
      </Form.Item>

      <div className="text-center">
        已有账号？<Link to="/login">立即登录</Link>
      </div>
    </Form>
  );
}
