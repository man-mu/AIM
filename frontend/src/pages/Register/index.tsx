import AuthLayout from "@/components/Auth/AuthLayout";
import RegisterForm, { type RegisterFormValues } from "@/components/Auth/RegisterForm";
import { useRegister } from "@/hooks/useAuth";
import { getDeviceId, PLATFORM } from "@/utils/device";
import type { RegisterParams } from "@/types/Auth/Auth";

export default function Register() {
    const registerMutation = useRegister();

    const handleSubmit = (values: RegisterFormValues) => {
        const params: RegisterParams = {
            username: values.username,
            password: values.password,
            phone: values.phone || undefined,
            email: values.email || undefined,
            deviceId: getDeviceId(),
            platform: PLATFORM,
        };
        registerMutation.mutate(params);
    };

    return (
        <AuthLayout title="注册账号">
            <RegisterForm
                onSubmit={handleSubmit}
                loading={registerMutation.isPending}
            />
        </AuthLayout>
    );
}
