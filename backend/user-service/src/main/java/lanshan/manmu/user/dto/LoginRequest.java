package lanshan.manmu.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求（user-service 本地校验 DTO）。
 *
 * <p>同 {@link RegisterRequest}，Bean Validation 注解加在本地 DTO 上，避免给 common 模块引入 jakarta.validation 依赖。
 */
public record LoginRequest(
        @NotBlank(message = "account 不能为空")
        @Size(max = 128, message = "account 长度不能超过 128")
        String account,

        @NotBlank(message = "password 不能为空")
        @Size(max = 64, message = "password 长度不能超过 64")
        String password,

        @Size(max = 128, message = "deviceId 长度不能超过 128")
        String deviceId,

        @Size(max = 32, message = "platform 长度不能超过 32")
        String platform
) {
}
