package com.sky.exception;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 17:02
 */

/**
 * 分类不存在异常
 */
public class CategoryNotExistException extends BaseException {
    public CategoryNotExistException(String message) {
        super(message);
    }
}
