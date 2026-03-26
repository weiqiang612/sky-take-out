package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/26 8:33
 */


@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 查询今日营业数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        // 1. 获得今日营业额
        HashMap<String, Object> map = new HashMap<>();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", Orders.COMPLETED);
        Double turnover = ordersMapper.turnoverStatisticsDaily(map);
        turnover = turnover == null ? 0 : turnover;
        // 2. 获取今日有效订单数
        Integer validOrderNumDaily = ordersMapper.countByTime(begin, end, 5);
        // 今日订单数
        Integer orderNumDaily = ordersMapper.countByTime(begin, end, null);
        // 3. 订单完成率
        Double orderCompletionRate = 0.0;
        if (orderNumDaily > 0 && validOrderNumDaily != null) {
            orderCompletionRate = validOrderNumDaily.doubleValue() / orderNumDaily;
        }
        // 4. 平均客单价 今日营业额 / 有效订单数
        Double unitPrice = 0.0;
        if (validOrderNumDaily > 0) {
            unitPrice = turnover / validOrderNumDaily;
        }
        // 5. 新增用户
        Integer newUsers = userMapper.countUser(begin, end);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderNumDaily)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    /**
     * 查询今日订单管理数据
     *
     * @return
     */
    @Override
    public OrderOverViewVO getOrderOverView() {
        LocalDateTime begin = LocalDateTime.now().with(LocalTime.MIN);
        // 1. 待接单
        Integer toBeConfirmed = ordersMapper.countByTime(begin, null, Orders.TO_BE_CONFIRMED);
        // 2. 待派送
        Integer confirmed = ordersMapper.countByTime(begin, null, Orders.CONFIRMED);
        // 3. 已完成
        Integer completed = ordersMapper.countByTime(begin, null, Orders.COMPLETED);
        // 4. 已取消
        Integer canceled = ordersMapper.countByTime(begin, null, Orders.CANCELLED);
        // 5. 全部订单
        Integer allOrders = ordersMapper.countByTime(begin, null, null);
        return OrderOverViewVO.builder()
                .waitingOrders(toBeConfirmed)
                .deliveredOrders(confirmed)
                .completedOrders(completed)
                .cancelledOrders(canceled)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     *
     * @return
     */
    @Override
    public DishOverViewVO getDishOverView() {
        // 1. 查询已起售菜品
        Integer onSales = dishMapper.countByStatus(StatusConstant.ENABLE);
        // 2. 查询已停售菜品
        Integer offSales = dishMapper.countByStatus(StatusConstant.DISABLE);
        return DishOverViewVO.builder()
                .sold(onSales)
                .discontinued(offSales)
                .build();
    }

    /**
     * 套餐总览
     * @return
     */
    @Override
    public SetmealOverViewVO getSetmealOverView() {
        // 1. 查询起售套餐数
        Integer onSales = setmealMapper.countByStatus(StatusConstant.ENABLE);
        // 2. 查询停售套餐数
        Integer offSales = setmealMapper.countByStatus(StatusConstant.DISABLE);
        return SetmealOverViewVO.builder()
                .sold(onSales)
                .discontinued(offSales)
                .build();
    }
}
