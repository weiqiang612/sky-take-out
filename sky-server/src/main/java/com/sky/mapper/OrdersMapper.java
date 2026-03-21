package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

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
}
