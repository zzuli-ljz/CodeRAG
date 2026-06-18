package com.coderag.exception;

import com.coderag.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局统一异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(AccessDeniedException e) {
        return R.forbidden("无权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return R.fail(message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return R.fail(message);
    }

    @ExceptionHandler(VectorDimensionMismatchException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public R<Void> handleVectorDimensionMismatch(VectorDimensionMismatchException e) {
        log.warn("向量维度不匹配: {}", e.getMessage());
        return R.fail(409, e.getMessage());
    }

    /**
     * RuntimeException — 通常由 Service 层主动抛出的业务/运行时异常
     * 把异常详情传递给前端（不再是死板的 "服务器内部错误"）
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleRuntimeException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "服务器内部错误";
        }
        // 超长截断避免响应体过大
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "...(已截断)";
        }
        log.error("运行时异常: {}", msg, e);
        return R.fail(500, msg);
    }

    /**
     * 兜底 Exception 处理（非 RuntimeException 的意外异常）
     * 不泄露内部细节给前端
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return R.fail(500, "服务器内部错误");
    }
}
