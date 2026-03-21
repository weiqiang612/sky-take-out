package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:11
 */

@RestController("userOrdersController")
@RequestMapping("/user/order")
@Slf4j
public class OrdersController {

    @Autowired
    private OrdersService ordersService;

    /**
     * 用户下单功能
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户：{} 下单: {}", BaseContext.getCurrentId(), ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = ordersService.submit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }
}
