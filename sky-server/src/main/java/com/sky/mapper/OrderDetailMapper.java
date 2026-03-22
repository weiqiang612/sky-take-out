package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.ArrayList;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:56
 */

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细表
     * @param orderDetails
     */
    void insertBatch(List<OrderDetail> orderDetails);

    /**
     * 根据订单id查询订单明细
     * @param id
     * @return
     */
    @Select("select name,number,dish_flavor from order_detail where order_id = #{id}")
    List<OrderDetail> getByOrderId(Long id);
}
