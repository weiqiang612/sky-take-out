package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrdersService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
//        正常调用这个方法，会向微信发送预支付
//        OrderPaymentVO orderPaymentVO = ordersService.payment(ordersPaymentDTO);

        // 模拟返回给前端的支付参数，测试环境使用
        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setNonceStr("mock_" + UUID.randomUUID().toString().substring(0, 10)); // 随机字符串
        vo.setPaySign("mock_pay_sign_RSA_ethan_test"); // 模拟签名
        vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000)); // 当前时间戳
        vo.setSignType("RSA"); // 签名算法
        vo.setPackageStr("prepay_id=mock_wx20260321" + System.currentTimeMillis()); // 模拟prepay_id

        log.info("生成预支付交易单：{}", vo);
        return Result.success(vo);
    }

    /**
     * 用户端查询历史订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/historyOrders")
    public Result<PageResult> pageHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("用户端查询历史订单,{}", ordersPageQueryDTO);
        PageResult result = ordersService.page(ordersPageQueryDTO);
        return Result.success(result);
    }

    /**
     * 用户端查询订单详情
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    public Result<OrderVO> orderDetail(@PathVariable Long id) {
        log.info("用户端查询订单详情，订单id：{}",id);
        OrderVO result = ordersService.getById(id);
        return Result.success(result);
    }

    /**
     * 用户催单功能
     * @param id
     * @return
     */
    @GetMapping("reminder/{id}")
    public Result reminder(@PathVariable Long id) {
        log.info("用户催单，{}",id);
        // TODO使用webSocket向管理端发送提醒
        return Result.success();
    }

    /**
     * 用户端取消订单
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    public Result cancel(@PathVariable Long id) {
        log.info("用户端取消订单，订单id{}",id);
        ordersService.userCancel(id);
        return Result.success();
    }

}
