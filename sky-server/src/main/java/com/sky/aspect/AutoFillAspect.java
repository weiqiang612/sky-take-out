package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * @author 袁志刚
 * @version 1.0
 * @Date 2026/3/13 9:32
 */

/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Slf4j
@Aspect
@Component
public class AutoFillAspect {

    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper..*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointcut() {}

    /**
     * 前置通知(增强逻辑)，在通知中进行公共字段的赋值
     */
    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段的填充...");
        // 1. 获取被拦截方法的@AutoFill注解的OperationType值
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();// 获得签名对象
        AutoFill annotation = signature.getMethod().getAnnotation(AutoFill.class);// 获得方法上的注解对象
        OperationType value = annotation.value();// 获得数据库操作类型
        // 2. 获取到被拦截方法的参数(需要被赋值的实体类)
        // 这里做一个约定，要想实现自动填充的话，将被增强参数放在第一个位置
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            log.debug("AOP增强的方法传递的参数为空！{}",args);
            return;
        }
        Object entity = args[0];
        // 3. 准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();
        // 4. 根据OperationType值不同，选择不同的增强逻辑，为实体类通过反射赋值
        if(value == OperationType.INSERT){
            // 为四个字段赋值
            try {
                Method setCreateTime = entity.getClass().getMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setUpdateTime = entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateUser = entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateUser.invoke(entity, currentId);
                setCreateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);
                setCreateTime.invoke(entity, now);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } else if (value == OperationType.UPDATE) {
            // 为两个字段赋值
            try {
                Method setUpdateTime = entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateUser.invoke(entity, currentId);
                setUpdateTime.invoke(entity, now);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
