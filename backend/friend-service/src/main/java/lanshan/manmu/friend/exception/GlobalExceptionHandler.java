package lanshan.manmu.friend.exception;

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
 * friend-service 全局异常处理（spec controller-spec.md §4.2）。
 * <p>统一将异常转换为 {@link Result} 响应体，确保「参数错误返回 4xx 而非 500」。
 * <p>映射规则与 user-service GlobalExceptionHandler 对齐。
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        log.info("请求体不可读: {}", ex.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    /** 必填 header（X-User-Id）缺失：语义为未鉴权，返回 401。 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingHeader(MissingRequestHeaderException ex) {
        log.info("缺失请求头: {}", ex.getHeaderName());
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /** 路径变量/query 参数类型不匹配（如非数字 id）→ 400 而非 500。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("参数类型不匹配: param={}, value={}", ex.getName(), ex.getValue());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "参数格式不合法");
    }

    /** DB 唯一索引/长度约束兜底：应用层校验漏网时，避免 DataIntegrityViolationException 上抛 500。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB 数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception ex) {
        log.error("friend-service unexpected error", ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}
