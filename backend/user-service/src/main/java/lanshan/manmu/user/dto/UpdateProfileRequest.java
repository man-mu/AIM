package lanshan.manmu.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 更新资料请求（user-service 本地校验 DTO）。
 *
 * <p>所有字段均可选（传入 null 表示不更新）；非 null 字段才参与校验。
 * userId 由网关注入的 X-User-Id 覆盖，不由客户端传入，故此处不做校验。
 *
 * <p>字段上限对齐 DB schema：avatar VARCHAR(512)、phone VARCHAR(20)、email VARCHAR(128)、bio TEXT(500 应用层限制)。
 */
public record UpdateProfileRequest(
        @Size(max = 512, message = "avatar 长度不能超过 512")
        String avatar,

        @Min(value = 0, message = "gender 取值需 0/1/2")
        @Max(value = 2, message = "gender 取值需 0/1/2")
        Integer gender,

        @Size(max = 500, message = "bio 长度不能超过 500")
        String bio,

        @PositiveOrZero(message = "birthday 不能为负数")
        Long birthday,

        @Size(max = 20, message = "phone 长度不能超过 20")
        String phone,

        @Size(max = 128, message = "email 长度不能超过 128")
        @Email(message = "email 格式不合法")
        String email
) {
}
