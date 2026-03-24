package com.sky.task;

import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrdersMapper;
import com.sky.service.OrdersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/24 15:15
 */


@Component
@Slf4j
public class MyTask {

    @Autowired
    private OrdersService ordersService;
    @Autowired
    private OrdersMapper ordersMapper;

    /**
     * 每分钟检查是否有超时未支付订单，如果有，自动取消
     */
    @Scheduled(cron = "0 * * * * ?")
    public void rejectTimeoutOrder() {
        log.info("开始执行cancelTimeoutOrder定时任务...");
        List<Long> orderIds = ordersMapper.getTimeoutOrder();
        // 1. 没有符合条件的订单直接返回
        if (CollectionUtils.isEmpty(orderIds)) {
            return;
        }

        // 2. 批量取消订单
        ordersMapper.updateBatch(orderIds,6,"订单超时未支付，自动取消！");
        log.info("成功取消{}个订单！", orderIds.size());
    }

    /**
     * 每天凌晨自动完成派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void completeINProcessOrder() {
        log.info("开始执行completeINProcessOrder任务...");
        List<Long> orderIds = ordersMapper.getINProcessOrder();
        if (CollectionUtils.isEmpty(orderIds)) {
            return;
        }
        // 批量完成订单
        ordersMapper.updateBatch(orderIds,5,null);
    }

}
