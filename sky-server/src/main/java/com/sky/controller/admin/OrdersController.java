package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 管理端进行条件查询，订单搜索功能
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/conditionSearch")
    public Result<PageResult> page(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("管理端进行条件查询,{}",ordersPageQueryDTO);
        PageResult result = ordersService.page(ordersPageQueryDTO);
        return Result.success(result);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @GetMapping("/details/{id}")
    public Result<OrderVO> getById(@PathVariable Long id) {
        log.info("管理端查询订单详情,id:{}",id);
        return Result.success(ordersService.getById(id));
    }

    /**
     * 查询各个状态订单数量
     * @return
     */
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> statistics(){
        log.info("管理端查询各个状态订单数量...");
        return Result.success(ordersService.statistics());
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     * @return
     */
    @PutMapping("/confirm")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("管理端请求接单，id{}",ordersConfirmDTO.getId());
        ordersService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    @PutMapping("/rejection")
    public Result reject(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {
        log.info("管理端拒单，{}",ordersRejectionDTO);
        ordersService.reject(ordersRejectionDTO);
        return Result.success();
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     * @return
     */
    @PutMapping("/cancel")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("管理端取消订单，{}",ordersCancelDTO);
        ordersService.cancel(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 派送订单
     * @param id
     * @return
     */
    @PutMapping("/delivery/{id}")
    public Result delivery(@PathVariable Long id) {
        log.info("管理端派送订单，id：{}",id);
        ordersService.delivery(id);
        return Result.success();
    }


}
