package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 根据订单id查询订单
     * @param id
     * @return
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 统计各个状态订单数量
     * @param status
     * @return
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer countByStatus(Integer status);

    /**
     * 根据订单id查询订单状态
     * @param id
     * @return
     */
    @Select("select status from orders where id = #{id}")
    Integer getStatusById(Long id);

    /**
     * 根据订单id查询支付状态
     * @param id
     * @return
     */
    @Select("select pay_status from orders where id = #{id}")
    Integer getPayStatus(Long id);

    /**
     * 查询未支付的超时订单
     * @return
     */
    List<Long> getTimeoutOrder();

    /**
     * 查询商家没有点击已完成的，但实际上派送完的订单
     * @return
     */
    List<Long> getINProcessOrder();

    void updateBatch(List<Long> orderIds, long targetStatus, String reason);

    /**
     * 营业额统计
     * @param begin
     * @param end
     * @return
     */
    @MapKey("orderDate")
    Map<String, Map<String, Object>> turnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 条件查询订单数量
     * @param begin
     * @param end
     * @param status
     * @return
     */
    Integer countByTime(@Param("begin") LocalDateTime begin,@Param("end") LocalDateTime end,@Param("status") Integer status);

    /**
     * 查询指定时间内的订单id
     * @param begin
     * @param end
     * @return
     */
    List<Integer> getByTime(LocalDate begin, LocalDate end);

    /**
     * 获取营业额
     * @param map
     */
    Double turnoverStatisticsDaily(HashMap<String, Object> map);
}
