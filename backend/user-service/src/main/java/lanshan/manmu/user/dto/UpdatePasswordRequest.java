package lanshan.manmu.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求（user-service 本地校验 DTO）。
 *
 * <p>newPassword 的强度（必须含字母+数字）由 service 层 validatePasswordStrength 兜底校验，
 * 此处仅做基本非空与长度校验，避免与 service 层规则重复。
 */
public record UpdatePasswordRequest(
        @NotBlank(message = "oldPassword 不能为空")
        @Size(max = 64, message = "oldPassword 长度不能超过 64")
        String oldPassword,

        @NotBlank(message = "newPassword 不能为空")
        @Size(min = 6, max = 32, message = "newPassword 长度需 6~32 个字符")
        String newPassword
) {
}
