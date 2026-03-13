package com.sky.annotation;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/13 9:35
 */

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识哪些方法需要增强公共字段填充处理
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    /**
     * 指定数据库操作类型
     * @return
     */
    OperationType value();
}
