package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private PayCallbackRecordMapper payCallbackRecordMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private WebSocketServer webSocketServer;


    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 检查客户收货地址是否超出配送范围。
     *
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address", shopAddress);
        map.put("output", "json");
        map.put("ak", ak);

        // 获取店铺经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("店铺地址解析失败");
        }

        // 数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        // 店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address", address);
        // 获取用户收货地址经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("鏀惰揣鍦板潃瑙ｆ瀽澶辫触");
        }

        // 数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        // 用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info", "0");

        // 路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("配送路线规划失败");
        }

        //鏁版嵁瑙ｆ瀽
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if (distance > 50000) {
            // 配送距离超过50000米
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 用户下单功能
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        // 注意：用户下单会同时操作订单表、订单明细表和购物车表

        Long userId = BaseContext.getCurrentId();

        // 0. 对用户传来的数据进行校验，处理地址簿为空、购物车为空等异常
        // 检查地址簿
        Long addressBookId = ordersSubmitDTO.getAddressBookId();
        AddressBook addressBook = addressBookMapper.getById(addressBookId);
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 检查用户收货地址是否超出配送范围
        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        // 检查购物车
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if (shoppingCarts == null || shoppingCarts.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 1. 向订单表插入一条数据
        // 未处理字段：checkout_time 付款时间、cancel_time 取消时间、
        // rejection_reason 拒单原因、cancel_reason 取消原因、delivery_time 送达时间
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
                .version(0)
                .build();
        // 需要主键回填，便于添加订单明细表
        ordersMapper.insert(orders);

        // 2. 向订单明细表插入 n 条数据
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
        // 4. 封装 VO 返回结果
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
        // 当前登录用户 id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), // 商户订单号
                new BigDecimal(0.01), // 支付金额，单位 元
                "苍穹外卖订单", // 商品描述
                user.getOpenid() // 微信用户的 openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态，来单提醒
     *
     * @param outTradeNo
     */
    @Transactional
    @Override
    public void paySuccess(String outTradeNo) {
        Orders ordersDB = ordersMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("order {} already paid, skip duplicate callback", outTradeNo);
            return;
        }

        try {
            payCallbackRecordMapper.insert(PayCallbackRecord.builder()
                    .outTradeNo(outTradeNo)
                    .callbackBody("{\"out_trade_no\":\"" + outTradeNo + "\"}")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            log.info("duplicate callback record for {}, skip", outTradeNo);
            return;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .version(ordersDB.getVersion())
                .build();

        int rows = ordersMapper.updateByIdAndStatus(orders, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        if (rows == 0) {
            Orders current = ordersMapper.getByNumber(outTradeNo);
            if (current != null && Orders.PAID.equals(current.getPayStatus())) {
                log.info("order {} was paid by another request, skip notify", outTradeNo);
                return;
            }
            throw new OrderBusinessException("订单状态异常，请稍后重试");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("type", 1);
                jsonObject.put("orderId", ordersDB.getId());
                jsonObject.put("content", "订单号：" + outTradeNo);
                try {
                    webSocketServer.sendToAllClient(jsonObject.toString());
                } catch (Exception ex) {
                    log.error("failed to send websocket notify for {}", outTradeNo, ex);
                }
            }
        });
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
        // 3. 将 orders 封装为 OrderVO 返回
        ArrayList<OrderVO> orderVOS = getOrderVOS(ordersPage);
        // 空集合直接返回
        return new PageResult(ordersPage.getTotal(), orderVOS);
    }

    /**
     * 根据订单 ID 查询订单
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
        // 2. 封装菜品 orderDetailList
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
     * 鎺ュ崟
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Long id = ordersConfirmDTO.getId();
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (!Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_PAID);
        }
        int rows = ordersMapper.updateByIdAndStatus(Orders.builder()
                .id(id)
                .status(Orders.CONFIRMED)
                .version(order.getVersion())
                .build(), Orders.TO_BE_CONFIRMED, Orders.PAID);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
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
        // 1. 先查询订单状态，处理业务异常，只有待接单的订单才可以接单
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
                .version(orders.getVersion())
                .build());

    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO, List<Integer> allowedStatuses) {
        Orders orders = ordersMapper.getById(ordersCancelDTO.getId());
        // 1. 先查询订单状态，处理业务异常，只有已接单、派送中的订单才可以取消
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 处理当前订单状态不符合要求的情况
        if (!allowedStatuses.contains(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 2. 执行取消订单逻辑，包含退款
        executeRejectORCancel(Orders.builder()
                .id(orders.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .number(orders.getNumber())
                .amount(orders.getAmount())
                .version(orders.getVersion())
                .build());
    }

    /**
     * 处理订单取消和拒单逻辑，整合更新订单状态和退款逻辑，需要调用方传入订单的 number 和 amount。
     *
     * @param orders
     */
    private void executeRejectORCancel(Orders orders) {
        Long id = orders.getId();
        Orders currentOrder = ordersMapper.getById(id);
        if (currentOrder == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1. 检查订单支付状态，如果已支付需要退款，并将订单payStatus修改为退款
        if (Orders.PAID.equals(currentOrder.getPayStatus())) {
            // 退款，测试环境不退款
//            // 商户退款单号，测试环境不使用
//            String outRefundNo = "";
//            weChatPayUtil.refund(order.getNumber(),outRefundNo,order.getAmount(),order.getAmount());
            log.info("订单 {} 触发订单退款逻辑，金额：{}", orders.getNumber(), orders.getAmount());
            // 修改订单支付状态为已退款
            orders.setPayStatus(Orders.REFUND);
        }
        int rows = ordersMapper.updateByIdAndStatus(orders, currentOrder.getStatus(), null);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1. 处理业务异常，只有已接单的订单才可以派送
        if (!Orders.CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 2. 派送订单
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .version(order.getVersion())
                .build();
        int rows = ordersMapper.updateByIdAndStatus(orders, Orders.CONFIRMED, null);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 管理端完成订单
     */
    @Override
    public void complete(Long id) {
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1. 处理业务异常，只有派送中的订单才可以完成
        if (!Orders.DELIVERY_IN_PROGRESS.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 2. 派送订单
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .version(order.getVersion())
                .build();
        int rows = ordersMapper.updateByIdAndStatus(orders, Orders.DELIVERY_IN_PROGRESS, null);
        if (rows == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 用户端取消订单
     *
     * @param id
     */
    @Transactional
    @Override
    public void userCancel(Long id) {
        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(id);
        ordersCancelDTO.setCancelReason("用户取消订单");
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.DELIVERY_IN_PROGRESS.equals(order.getStatus())) {
            throw new OrderBusinessException("订单正在派送中，请联系商家进行退款！");
        }
        // 用户端允许状态：1待付款 2待接单
        cancel(ordersCancelDTO, Arrays.asList(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED));
    }

    /**
     * 管理端取消订单
     *
     * @param ordersCancelDTO
     */
    @Transactional
    @Override
    public void adminCancel(OrdersCancelDTO ordersCancelDTO) {
        // 管理端允许状态：3已接单、4派送中，只有这些状态才可以执行取消订单操作
        cancel(ordersCancelDTO, Arrays.asList(Orders.DELIVERY_IN_PROGRESS, Orders.CONFIRMED));
    }

    /**
     * 再来一单
     *
     * @param id
     */
    @Transactional
    @Override
    public void repetition(Long id) {
//        // 再来一单直接复用数据即可，但是要注意同步插入订单明细表
//        Orders order = ordersMapper.getById(id);
//        if (order == null) {
//            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
//        }
//        order.setId(null);
//        order.setNumber(String.valueOf(System.currentTimeMillis()));
//        order.setStatus(Orders.PENDING_PAYMENT);
//        order.setOrderTime(LocalDateTime.now());
//        order.setCheckoutTime(null);
//        order.setPayStatus(Orders.UN_PAID);
//        order.setCancelReason(null);
//        order.setCancelTime(null);
//        order.setRejectionReason(null);
//        order.setEstimatedDeliveryTime(LocalDateTime.now().plusHours(1));
//        order.setDeliveryTime(null);
//        // 需要主键回填，便于添加订单明细表
//        ordersMapper.insert(order);
//
//        // 2. 向订单明细表插入 n 条数据
//        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
//        List<OrderDetail> newOrderDetails = orderDetails.stream().map(orderDetail -> {
//                    orderDetail.setId(null);
//                    orderDetail.setOrderId(order.getId());
//                    return orderDetail;
//                }
//        ).collect(Collectors.toList());
//        orderDetailMapper.insertBatch(newOrderDetails);

        // 再来一单另一种理解，将菜品重新加入购物车
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        if (details != null && !details.isEmpty()) {
            // 1. 清空用户原有购物车
            shoppingCartMapper.remove(ShoppingCart.builder().userId(BaseContext.getCurrentId()).build());
            // 2. 将 OrderDetail 转换为 ShoppingCart
            List<ShoppingCart> shoppingCarts = details.stream().map(detail -> {
                ShoppingCart sc = new ShoppingCart();
                BeanUtils.copyProperties(detail, sc); // 鎷疯礉 name, image, dishId, setmealId, dishFlavor, number, amount
                sc.setUserId(BaseContext.getCurrentId());
                sc.setCreateTime(LocalDateTime.now());
                return sc;
            }).collect(Collectors.toList());
            // 3. 批量插入购物车
            shoppingCartMapper.saveBatch(shoppingCarts);
        }
    }

    /**
     * 来单提醒
     *
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", 2);
        jsonObject.put("orderId", id);
        jsonObject.put("content", "订单号：" + order.getNumber());
        webSocketServer.sendToAllClient(jsonObject.toJSONString());
    }


    @Override
    public void requestRefund(Long id, String reason) {
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_PAID);
        }
        // 只有待接单的订单才可以申请退款
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        ordersMapper.update(Orders.builder()
                .id(id)
                .payStatus(Orders.REFUND)
                .cancelReason(reason)
                .version(order.getVersion())
                .status(Orders.CANCELLED)
                .build());
    }

    @Override
    public void updateDeliveryAddress(Long id, String newAddress) {
        Orders order = ordersMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.CANCELLED.equals(order.getStatus()) || Orders.COMPLETED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        ordersMapper.update(Orders.builder()
                .id(id)
                .address(newAddress)
                .version(order.getVersion())
                .build());
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
                // 将 orders 菜品提取出菜品信息字符串
                String orderDishes = getOrderDishesStr(order);
                orderVO.setOrderDishes(orderDishes);
                // 封装 orderDetailList 属性
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
            orderDishesStr.append("*").append(orderDetail.getNumber()).append(";");
        });
        return orderDishesStr.toString();
    }
}

