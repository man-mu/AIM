package lanshan.manmu.common.result;

import lanshan.manmu.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体。
 *
 * @param <T> 数据载荷类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {

    /** 业务码：0=成功，非0=失败 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 数据载荷 */
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getMessage(), null);
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
