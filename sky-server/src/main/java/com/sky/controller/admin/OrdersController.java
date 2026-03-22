package com.sky.controller.admin;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/22 8:59
 */

/**
 * 订单管理
 */
@RestController
@RequestMapping("/admin/order")
@Slf4j
public class OrdersController {

    @Autowired
    private OrdersService ordersService;

    @GetMapping("/conditionSearch")
    public Result<PageResult> page(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("管理端进行条件查询,{}",ordersPageQueryDTO);
        PageResult result = ordersService.page(ordersPageQueryDTO);
        return Result.success(result);
    }
}
