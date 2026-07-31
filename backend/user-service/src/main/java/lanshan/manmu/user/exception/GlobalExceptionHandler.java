package lanshan.manmu.user.exception;

import jakarta.validation.ConstraintViolationException;
import lanshan.manmu.common.exception.BizException;
import lanshan.manmu.common.exception.ErrorCode;
import lanshan.manmu.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * user-service 全局异常处理（spec controller-spec.md §4.2）。
 * <p>统一将异常转换为 {@link Result} 响应体，确保「参数错误返回 4xx 而非 500」。
 *
 * <p>异常映射优先级与语义：
 * <ul>
 *   <li>{@link BizException}：业务异常，透传 code/message。</li>
 *   <li>{@link MethodArgumentNotValidException}：@Valid @RequestBody 校验失败 → 400，附字段错误。</li>
 *   <li>{@link ConstraintViolationException}：方法参数级校验失败 → 400。</li>
 *   <li>{@link HttpMessageNotReadableException}：请求体 JSON 不可读/缺失 → 400。</li>
 *   <li>{@link MissingRequestHeaderException}：必填 header（如 X-User-Id）缺失 → 401（未鉴权语义）。</li>
 *   <li>{@link MethodArgumentTypeMismatchException}：路径变量/参数类型不匹配 → 400。</li>
 *   <li>{@link DataIntegrityViolationException}：DB 列长度/约束兜底 → 400，避免 500 泄露。</li>
 *   <li>其它 {@link Exception}：未知异常 → 500，记录日志。</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleArgNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        log.info("参数校验失败: {}", msg);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .findFirst()
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        log.info("约束校验失败: {}", msg);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.info("请求体不可读: {}", ex.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    /** 必填 header（X-User-Id）缺失：语义为未鉴权，返回 401。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingHeader(MissingRequestHeaderException ex) {
        log.info("必填 header 缺失: {}", ex.getHeaderName());
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("参数类型不匹配: param={}, value={}", ex.getName(), ex.getValue());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "参数格式不合法");
    }

    /** DB 唯一索引/长度约束兜底：应用层校验漏网时，避免 DataIntegrityViolationException 上抛 500。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB 数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求数据不合法");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("user-service unexpected error", ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
