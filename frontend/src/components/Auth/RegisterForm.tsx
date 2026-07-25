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
      // Ant Design renders validation feedback for the invalid account fields.
    }
  };

  const handleSubmit = () => {
    onSubmit(form.getFieldsValue(true) as RegisterFormValues);
  };

  return (
    <Form
      className="auth-form"
      form={form}
      name="registerForm"
      autoComplete="on"
      size="large"
      layout="vertical"
      onFinish={handleSubmit}
    >
      <ol
        className={`register-stepper__list ${currentStep === 1 ? 'register-stepper__list--complete' : ''}`}
        aria-label={'\u6ce8\u518c\u6b65\u9aa4'}
      >
        {stepLabels.map((label, index) => {
          const step = index as RegisterStep;
          const isComplete = step < currentStep;
          const isCurrent = step === currentStep;
          const itemClassName = [
            'register-stepper__item',
            isComplete ? 'is-complete' : '',
            isCurrent ? 'is-current' : '',
          ]
            .filter(Boolean)
            .join(' ');

          return (
            <li key={label} className={itemClassName} aria-current={isCurrent ? 'step' : undefined}>
              <span className="register-stepper__node" aria-hidden="true">
                {isComplete ? <CheckOutlined /> : index + 1}
              </span>
              <span className="register-stepper__label">{label}</span>
            </li>
          );
        })}
      </ol>

      {currentStep === 0 ? (
        <>
          <Form.Item
            name="username"
            label={'\u7528\u6237\u540d'}
            rules={[
              { required: true, message: '\u8bf7\u8f93\u5165\u7528\u6237\u540d' },
              { min: 3, message: '\u7528\u6237\u540d\u81f3\u5c11 3 \u4e2a\u5b57\u7b26' },
              { max: 32, message: '\u7528\u6237\u540d\u6700\u591a 32 \u4e2a\u5b57\u7b26' },
            ]}
          >
            <Input
              autoComplete="username"
              prefix={<UserOutlined />}
              placeholder={'\u7528\u6237\u540d\uff083-32 \u4e2a\u5b57\u7b26\uff09'}
            />
          </Form.Item>

          <Form.Item
            name="password"
            label={'\u5bc6\u7801'}
            rules={[
              { required: true, message: '\u8bf7\u8f93\u5165\u5bc6\u7801' },
              { min: 6, message: '\u5bc6\u7801\u81f3\u5c11 6 \u4e2a\u5b57\u7b26' },
              { max: 32, message: '\u5bc6\u7801\u6700\u591a 32 \u4e2a\u5b57\u7b26' },
            ]}
          >
            <Input.Password
              autoComplete="new-password"
              prefix={<LockOutlined />}
              placeholder={'\u5bc6\u7801\uff086-32 \u4e2a\u5b57\u7b26\uff09'}
            />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label={'\u786e\u8ba4\u5bc6\u7801'}
            dependencies={['password']}
            rules={[
              { required: true, message: '\u8bf7\u786e\u8ba4\u5bc6\u7801' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4'));
                },
              }),
            ]}
          >
            <Input.Password
              autoComplete="new-password"
              prefix={<LockOutlined />}
              placeholder={'\u786e\u8ba4\u5bc6\u7801'}
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              block
              icon={<ArrowRightOutlined aria-hidden />}
              iconPlacement="end"
              onClick={goToContactStep}
            >
              {'\u4e0b\u4e00\u6b65'}
            </Button>
          </Form.Item>
        </>
      ) : (
        <>
          <Form.Item
            name="phone"
            label={'\u624b\u673a\u53f7'}
            rules={[{ pattern: /^1\d{10}$/, message: '\u624b\u673a\u53f7\u683c\u5f0f\u4e0d\u6b63\u786e' }]}
          >
            <Input
              autoComplete="tel"
              inputMode="numeric"
              prefix={<PhoneOutlined />}
              placeholder={'\u624b\u673a\u53f7\uff08\u9009\u586b\uff09'}
            />
          </Form.Item>

          <Form.Item
            name="email"
            label={'\u90ae\u7bb1'}
            rules={[{ type: 'email', message: '\u90ae\u7bb1\u683c\u5f0f\u4e0d\u6b63\u786e' }]}
          >
            <Input
              autoComplete="email"
              prefix={<MailOutlined />}
              placeholder={'\u90ae\u7bb1\uff08\u9009\u586b\uff09'}
            />
          </Form.Item>

          <Form.Item className="register-stepper__actions">
            <Button disabled={loading} icon={<ArrowLeftOutlined aria-hidden />} onClick={() => setCurrentStep(0)}>
              {'\u4e0a\u4e00\u6b65'}
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              icon={<ArrowRightOutlined aria-hidden />}
              iconPlacement="end"
            >
              {'\u521b\u5efa\u8d26\u6237'}
            </Button>
          </Form.Item>
        </>
      )}

      <div className="text-center">
        {'\u5df2\u6709\u8d26\u53f7\uff1f'}<Link to="/login">{'\u7acb\u5373\u767b\u5f55'}</Link>
      </div>
    </Form>
  );
}
