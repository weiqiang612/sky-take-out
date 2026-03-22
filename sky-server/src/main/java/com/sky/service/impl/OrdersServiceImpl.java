package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrdersService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:19
 */

@Service
@Slf4j
public class OrdersServiceImpl implements OrdersService {

    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;


    /**
     * 用户下单功能
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        // 注意：用户下单操作需要操作包括订单表、订单明细表以及购物车表（清空购物车）三张表

        Long userId = BaseContext.getCurrentId();

        // 0. 对用户传来的数据进行校验，处理各种业务异常情况（地址簿为空，购物车为空）
        // 检查地址簿
        Long addressBookId = ordersSubmitDTO.getAddressBookId();
        AddressBook addressBook = addressBookMapper.getById(addressBookId);
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 检查购物车
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if (shoppingCarts == null || shoppingCarts.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 1. 向订单表插入一条数据
        // 未处理字段：checkout_time 付款时间、cancel_time 取消时间、
        // rejection_reason 拒单原因 、 cancel_reason 取消原因 、delivery_time 送达时间
        User user = userMapper.getById(userId);
        Orders orders = Orders.builder()
                .userName(user.getName())
                .number(String.valueOf(System.currentTimeMillis()))
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .addressBookId(ordersSubmitDTO.getAddressBookId())
                .orderTime(LocalDateTime.now())
                .payMethod(ordersSubmitDTO.getPayMethod())
                .payStatus(Orders.UN_PAID)
                .amount(ordersSubmitDTO.getAmount())
                .remark(ordersSubmitDTO.getRemark())
                .phone(addressBook.getPhone())
                .address(addressBook.getDetail())
                .consignee(addressBook.getConsignee())
                .estimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime())
                .deliveryStatus(ordersSubmitDTO.getDeliveryStatus())
                .packAmount(ordersSubmitDTO.getPackAmount())
                .tablewareNumber(ordersSubmitDTO.getTablewareNumber())
                .tablewareStatus(ordersSubmitDTO.getTablewareStatus())
                .build();
        // 需要主键回填，便于添加订单明细表
        ordersMapper.insert(orders);

        // 2. 向订单明细表插入n条数据
        List<OrderDetail> orderDetails = new ArrayList<>(shoppingCarts.size());
        for (ShoppingCart shoppingCart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetails);

        // 3. 清空当前用户购物车
        shoppingCartMapper.remove(ShoppingCart.builder().userId(userId).build());
        // 4. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderAmount(ordersSubmitDTO.getAmount())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = ordersMapper.getByNumber(outTradeNo);

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("订单已支付，请勿重复支付！");
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        ordersMapper.update(orders);
    }

    /**
     * 分页查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult page(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 1. 开启分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        // 2. 先查订单基本信息
        Page<Orders> ordersPage = ordersMapper.page(ordersPageQueryDTO);
        // 3. 将orders封装为orderVO返回
        ArrayList<OrderVO> orderVOS = getOrderVOS(ordersPage);
        // 空集合直接返回
        return new PageResult(ordersPage.getTotal(), orderVOS);
    }

    /**
     * 根据订单ID查询订单
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO getById(Long id) {
        if (id == null) {
            throw new OrderBusinessException("查询订单id不能为NULL！");
        }
        Orders order = ordersMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        // 1. 封装菜品信息字符串
        orderVO.setOrderDishes(getOrderDishesStr(order));
        // 2. 封装菜品orderDetailList
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(order.getId()));
        return orderVO;
    }

    /**
     * 查询各个状态订单数量
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();

        // 待接单订单
        Integer toBeConfirmed = ordersMapper.countByStatus(Orders.TO_BE_CONFIRMED);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed == null ? 0 : toBeConfirmed);
        // 待派送订单
        Integer confirmed = ordersMapper.countByStatus(Orders.CONFIRMED);
        orderStatisticsVO.setConfirmed(confirmed == null ? 0 : confirmed);
        // 派送中订单
        Integer deliveryInProgress = ordersMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress == null ? 0 : deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Long id = ordersConfirmDTO.getId();
        // 1. 先查询订单状态，处理业务异常，只有待接单并且已付款的订单才可以接单
        Integer status = ordersMapper.getStatusById(id);
        if (status == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.TO_BE_CONFIRMED.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Integer payStatus = ordersMapper.getPayStatus(id);
        if (!Orders.PAID.equals(payStatus)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_PAID);
        }
        // 2. 修改订单状态
        ordersMapper.update(Orders.builder()
                .id(id)
                .status(Orders.CONFIRMED)
                .build());
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     */
    @Transactional
    @Override
    public void reject(OrdersRejectionDTO ordersRejectionDTO) {
        Orders orders = ordersMapper.getById(ordersRejectionDTO.getId());
        // 1. 先查询订单状态，处理业务异常，只有待接单的订单才可以拒单
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = orders.getStatus();

        if (!Orders.TO_BE_CONFIRMED.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 2. 拒单和退款逻辑
        executeRejectORCancel(Orders.builder()
                .id(orders.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .number(orders.getNumber())
                .amount(orders.getAmount())
                .build());

    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Transactional
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO, List<Integer> allowedStatuses) {
        Orders orders = ordersMapper.getById(ordersCancelDTO.getId());
        // 1. 先查询订单状态，处理业务异常，只有3已接单 4派送中的订单才可以取消
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 处理当前订单状态不符合要求的情况
        if (!allowedStatuses.contains(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 2.执行取消订单逻辑，包含退款
        executeRejectORCancel(Orders.builder()
                .id(orders.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .number(orders.getNumber())
                .amount(orders.getAmount())
                .build());
    }

    /**
     * 处理订单取消和拒单逻辑，整合更新订单状态和处理退款逻辑，需要调用方将订单的number属性、amount属性给出
     *
     * @param orders
     */
    private void executeRejectORCancel(Orders orders) {
        Long id = orders.getId();
        // 1. 检查订单支付状态，如果已支付需要退款，并将订单payStatus修改为退款
        Integer payStatus = ordersMapper.getPayStatus(id);
        if (Orders.PAID.equals(payStatus)) {
            // 退款，测试环境不退款
//            // 商户退款单号，测试环境不使用
//            String outRefundNo = "";
//            weChatPayUtil.refund(order.getNumber(),outRefundNo,order.getAmount(),order.getAmount());
            log.info("订单 {} 触发订单退款逻辑，金额：{}", orders.getNumber(), orders.getAmount());
            // 修改订单支付状态为已退款
            orders.setPayStatus(Orders.REFUND);
        }
        ordersMapper.update(orders);
    }


    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Integer status = ordersMapper.getStatusById(id);
        if (status == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1. 处理业务异常，只有已接单的订单才可以派送
        if (!Orders.CONFIRMED.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 2. 派送订单
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        ordersMapper.update(orders);
    }

    /**
     * 管理端完成订单
     */
    @Override
    public void complete(Long id) {
        Integer status = ordersMapper.getStatusById(id);
        if (status == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1. 处理业务异常，只有派送中的订单才可以完成
        if (!Orders.DELIVERY_IN_PROGRESS.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 2. 派送订单
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .build();
        ordersMapper.update(orders);
    }

    /**
     * 用户端取消订单
     *
     * @param id
     */
    @Override
    public void userCancel(Long id) {
        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(id);
        ordersCancelDTO.setCancelReason("用户取消订单");
        // 用户端允许状态：1待付款 2待接单
        cancel(ordersCancelDTO, Arrays.asList(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED));
    }

    /**
     * 管理端取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        // 管理端允许状态：3已接单 4派送中 ，只有这些状态才可以执行取消订单操作
        cancel(ordersCancelDTO, Arrays.asList(Orders.DELIVERY_IN_PROGRESS, Orders.CONFIRMED));
    }

    /**
     * 将分页查询结果封装成 OrderVO 集合返回
     *
     * @param ordersPage
     * @return
     */
    @NonNullDecl
    private ArrayList<OrderVO> getOrderVOS(Page<Orders> ordersPage) {
        ArrayList<OrderVO> orderVOS = new ArrayList<>();
        List<Orders> orders = ordersPage.getResult();
        if (orders != null && !orders.isEmpty()) {
            orders.forEach(order -> {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                // 将orders菜品提取出菜品信息字符串
                String orderDishes = getOrderDishesStr(order);
                orderVO.setOrderDishes(orderDishes);
                // 封装orderDetailList属性
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
                orderVO.setOrderDetailList(orderDetails);
                orderVOS.add(orderVO);
            });
        }
        return orderVOS;
    }

    /**
     * 将订单的相关信息封装成字符串返回
     *
     * @param order
     * @return
     */
    private String getOrderDishesStr(Orders order) {
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
        StringBuilder orderDishesStr = new StringBuilder();
        orderDetails.forEach(orderDetail -> {
            orderDishesStr.append(orderDetail.getName());
            if (orderDetail.getDishFlavor() != null) {
                orderDishesStr.append(orderDetail.getDishFlavor());
            }
            orderDishesStr.append("*" + orderDetail.getNumber() + ";");
        });
        return orderDishesStr.toString();
    }
}
