package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

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
    @Select("select * from order_detail where order_id = #{id}")
    List<OrderDetail> getByOrderId(Long id);

    /**
     * 统计指定时间内的热卖品
     * 注意，映射的结构是{"name":"菜品","number":10}
     * @param begin
     * @param end
     * @return
     */
    List<LinkedHashMap<String, Object>> top(LocalDate begin, LocalDate end);
}
