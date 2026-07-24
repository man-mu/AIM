import AuthLayout from "@/components/Auth/AuthLayout";
import LoginForm, { type LoginFormValues } from "@/components/Auth/LoginForm";
import { useLogin } from "@/hooks/useAuth";
import { getDeviceId, PLATFORM } from "@/utils/device";
import type { LoginParams } from "@/types/Auth/Auth";

export default function Login() {
    const loginMutation = useLogin();

    const handleSubmit = (values: LoginFormValues) => {
        const params: LoginParams = {
            account: values.account,
            password: values.password,
            deviceId: getDeviceId(),
            platform: PLATFORM,
        };
        loginMutation.mutate(params);
    };

    return (
        <AuthLayout title="AIM">
            <LoginForm
                onSubmit={handleSubmit}
                loading={loginMutation.isPending}
            />
        </AuthLayout>
    );
}