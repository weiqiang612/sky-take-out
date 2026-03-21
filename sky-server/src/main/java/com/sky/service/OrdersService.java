package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderSubmitVO;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:18
 */


public interface OrdersService {
    /**
     * 用户下单功能
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);
}
