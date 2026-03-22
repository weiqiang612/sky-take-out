package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:21
 */

@Mapper
public interface OrdersMapper {
    /**
     * 插入订单
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param outTradeNo
     * @return
     */
    @Select("select * from orders where number = #{outTradeNo}")
    Orders getByNumber(String outTradeNo);


    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 管理端分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> page(OrdersPageQueryDTO ordersPageQueryDTO);
}
