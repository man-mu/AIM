package lanshan.manmu.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求（user-service 本地校验 DTO）。
 *
 * <p>common 模块的 {@code RegisterReq} 为共享 DTO，不引入 jakarta.validation 依赖（保持 common 轻量），
 * 故在 user-service 内定义带 Bean Validation 注解的本地请求体，Controller 层 {@code @Valid} 校验通过后
 * 再转换为 common {@code RegisterReq} 调用 service 层。
 *
 * <p>字段长度上限对齐 DB schema（aim-schema.sql）：username VARCHAR(64)、phone VARCHAR(20)、email VARCHAR(128)。
 */
public record RegisterRequest(
        @NotBlank(message = "username 不能为空")
        @Size(min = 3, max = 64, message = "username 长度需 3~64 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "username 只能含字母、数字、下划线")
        String username,

        @NotBlank(message = "password 不能为空")
        @Size(min = 6, max = 32, message = "password 长度需 6~32 个字符")
        String password,

        @Size(max = 20, message = "phone 长度不能超过 20")
        String phone,

        @Size(max = 128, message = "email 长度不能超过 128")
        @Email(message = "email 格式不合法")
        String email,

        @Size(max = 128, message = "deviceId 长度不能超过 128")
        String deviceId,

        @Size(max = 32, message = "platform 长度不能超过 32")
        String platform
) {
}
