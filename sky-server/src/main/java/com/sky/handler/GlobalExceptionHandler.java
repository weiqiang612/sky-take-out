package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获添加数据时违反sql唯一约束异常
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        // Duplicate entry 'yzg' for key 'employee.idx_username'
        log.error("异常信息：{}",ex.getMessage());
        String message = ex.getMessage();
        // 判断是否包含唯一索引冲突的关键字
        if(message.contains("Duplicate entry")){
            String[] s = message.split(" ");
            String username = s[2];
            String msg = username + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }

        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
