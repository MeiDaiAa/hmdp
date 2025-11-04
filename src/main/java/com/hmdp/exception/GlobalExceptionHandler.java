package com.hmdp.exception;

import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public Result handleUserUnsignedException(UserUnsignedException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler
    public Result handleServiceException(ServiceException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler
    public Result handleException(Exception e) {
        return Result.fail("服务器异常，请联系管理员");
    }
}
