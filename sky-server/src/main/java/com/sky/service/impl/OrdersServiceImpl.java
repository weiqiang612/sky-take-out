package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.OrdersService;
import com.sky.vo.OrderSubmitVO;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/20 16:19
 */

@Service
public class OrdersServiceImpl implements OrdersService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private OrdersMapper ordersMapper;

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
        // 未处理字段：checkout_time 付款时间 、user_name 用户名 、cancel_time 取消时间、
        // rejection_reason 拒单原因 、 cancel_reason 取消原因 、delivery_time 送达时间
        Orders orders = Orders.builder()
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
}
