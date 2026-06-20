package com.sky.test;

import com.sky.controller.ai.AiCustomerController;
import com.sky.entity.AddressBook;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import com.sky.service.CategoryService;
import com.sky.service.DishService;
import com.sky.service.OrdersService;
import com.sky.service.SetmealService;
import com.sky.service.ShopService;
import com.sky.service.ShoppingCartService;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCustomerControllerContractTest {

    @Test
    void cancelOrderShouldReturnStableConfirmationText() {
        OrdersService ordersService = mock(OrdersService.class);
        AiCustomerController controller = controller(ordersService);

        Result<?> result = controller.cancelOrder(7L, 88L);

        assertEquals(1, result.getCode());
        assertEquals("Cancelled order 88", result.getData());
        verify(ordersService).userCancel(88L);
    }

    @Test
    void refundOrderShouldReturnStableConfirmationText() {
        OrdersService ordersService = mock(OrdersService.class);
        AiCustomerController controller = controller(ordersService);

        Result<?> result = controller.refundOrder(7L, 88L, request("late delivery"));

        assertEquals(1, result.getCode());
        assertEquals("Refund requested for order 88: late delivery", result.getData());
        verify(ordersService).requestRefund(88L, "late delivery");
    }

    @Test
    void updateOrderAddressShouldReturnStableConfirmationText() {
        OrdersService ordersService = mock(OrdersService.class);
        AiCustomerController controller = controller(ordersService);

        Result<?> result = controller.updateOrderAddress(7L, 88L, addressRequest("No. 1 Road"));

        assertEquals(1, result.getCode());
        assertEquals("Updated delivery address for order 88: No. 1 Road", result.getData());
        verify(ordersService).updateDeliveryAddress(88L, "No. 1 Road");
    }

    @Test
    void recentOrdersShouldWrapPageResult() {
        OrdersService ordersService = mock(OrdersService.class);
        PageResult pageResult = new PageResult(2, Arrays.asList(mock(OrderVO.class), mock(OrderVO.class)));
        when(ordersService.page(org.mockito.ArgumentMatchers.any())).thenReturn(pageResult);
        AiCustomerController controller = controller(ordersService);

        Result<PageResult> result = controller.recentOrders(7L, 5);

        assertEquals(1, result.getCode());
        assertEquals(2, result.getData().getTotal());
        verify(ordersService).page(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void defaultAddressShouldReturnErrorWhenNoDefaultExists() {
        AddressBookService addressBookService = mock(AddressBookService.class);
        when(addressBookService.list(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());
        AiCustomerController controller = controller(addressBookService);

        Result<AddressBook> result = controller.defaultAddress(7L);

        assertEquals(0, result.getCode());
        assertEquals("No default address", result.getMsg());
        assertNull(result.getData());
    }

    private AiCustomerController controller(OrdersService ordersService) {
        return new AiCustomerController(
                ordersService,
                mock(CategoryService.class),
                mock(DishService.class),
                mock(SetmealService.class),
                mock(ShopService.class),
                mock(ShoppingCartService.class),
                mock(AddressBookService.class)
        );
    }

    private AiCustomerController controller(AddressBookService addressBookService) {
        return new AiCustomerController(
                mock(OrdersService.class),
                mock(CategoryService.class),
                mock(DishService.class),
                mock(SetmealService.class),
                mock(ShopService.class),
                mock(ShoppingCartService.class),
                addressBookService
        );
    }

    private AiCustomerController.RefundRequest request(String reason) {
        AiCustomerController.RefundRequest request = new AiCustomerController.RefundRequest();
        request.setReason(reason);
        return request;
    }

    private AiCustomerController.UpdateOrderAddressRequest addressRequest(String newAddress) {
        AiCustomerController.UpdateOrderAddressRequest request = new AiCustomerController.UpdateOrderAddressRequest();
        request.setNewAddress(newAddress);
        return request;
    }
}
