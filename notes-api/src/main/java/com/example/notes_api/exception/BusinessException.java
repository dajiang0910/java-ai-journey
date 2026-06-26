package com.example.notes_api.exception;

import org.springframework.http.HttpStatus;

/**
 * 自定义业务异常
 *
 * 为什么不用 RuntimeException？
 * - RuntimeException 太笼统，无法区分"资源不存在"和"代码 bug"
 * - 自定义异常可以携带 HTTP 状态码，让 GlobalExceptionHandler 精确处理
 *
 * 使用示例：
 *   throw new BusinessException(HttpStatus.NOT_FOUND, "笔记不存在: " + id);
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
