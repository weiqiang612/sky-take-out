package com.sky.controller.ai;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.*;
import com.sky.vo.DishItemVO;
import com.sky.vo.DishVO;
import com.sky.vo.OrderVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/customer")
public class AiCustomerController {

    private final OrdersService ordersService;
    private final CategoryService categoryService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final ShopService shopService;
    private final ShoppingCartService shoppingCartService;
    private final AddressBookService addressBookService;

    public AiCustomerController(OrdersService ordersService, CategoryService categoryService, DishService dishService,
                                SetmealService setmealService, ShopService shopService, ShoppingCartService shoppingCartService,
                                AddressBookService addressBookService) {
        this.ordersService = ordersService;
        this.categoryService = categoryService;
        this.dishService = dishService;
        this.setmealService = setmealService;
        this.shopService = shopService;
        this.shoppingCartService = shoppingCartService;
        this.addressBookService = addressBookService;
    }

    @GetMapping("/orders/{id}")
    public Result<OrderVO> orderDetail(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id) {
        BaseContext.setCurrentId(userId);
        return Result.success(ordersService.getById(id));
    }

    @GetMapping("/orders/recent")
    public Result<PageResult> recentOrders(@RequestHeader("X-AI-User-Id") Long userId,
                                           @RequestParam(defaultValue = "3") Integer pageSize) {
        BaseContext.setCurrentId(userId);
        OrdersPageQueryDTO query = new OrdersPageQueryDTO();
        query.setPage(1);
        query.setPageSize(pageSize);
        query.setUserId(userId);
        return Result.success(ordersService.page(query));
    }

    @PutMapping("/orders/{id}/cancel")
    public Result cancelOrder(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id) {
        BaseContext.setCurrentId(userId);
        ordersService.userCancel(id);
        return Result.success("Cancelled order " + id);
    }

    @PutMapping("/orders/{id}/refund")
    public Result refundOrder(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id,
                              @RequestBody RefundRequest request) {
        BaseContext.setCurrentId(userId);
        ordersService.requestRefund(id, request.getReason());
        return Result.success("Refund requested for order " + id + ": " + request.getReason());
    }

    @PutMapping("/orders/{id}/address")
    public Result updateOrderAddress(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id,
                                     @RequestBody UpdateOrderAddressRequest request) {
        BaseContext.setCurrentId(userId);
        ordersService.updateDeliveryAddress(id, request.getNewAddress());
        return Result.success("Updated delivery address for order " + id + ": " + request.getNewAddress());
    }

    @GetMapping("/orders/{id}/reminder")
    public Result remindOrder(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id) {
        BaseContext.setCurrentId(userId);
        ordersService.reminder(id);
        return Result.success("Reminder sent for order " + id);
    }

    @PostMapping("/orders/{id}/reorder")
    public Result reorder(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id) {
        BaseContext.setCurrentId(userId);
        ordersService.repetition(id);
        return Result.success("Reordered items from order " + id);
    }

    @GetMapping("/categories")
    public Result listCategories(@RequestParam(required = false) Integer type) {
        return Result.success(categoryService.list(type));
    }

    @GetMapping("/dishes")
    public Result<List<DishVO>> listDishes(@RequestParam Long categoryId) {
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(1);
        return Result.success(dishService.listWithFlavors(dish));
    }

    @GetMapping("/setmeals")
    public Result<List<Setmeal>> listSetmeals(@RequestParam Long categoryId) {
        return Result.success(setmealService.list(Setmeal.builder().categoryId(categoryId).status(1).build()));
    }

    @GetMapping("/setmeals/{id}/dishes")
    public Result<List<DishItemVO>> listSetmealDishes(@PathVariable Long id) {
        return Result.success(setmealService.listDishItem(id));
    }

    @GetMapping("/shop/status")
    public Result<Integer> shopStatus() {
        return Result.success(shopService.queryStatus());
    }

    @GetMapping("/cart")
    public Result listCart(@RequestHeader("X-AI-User-Id") Long userId) {
        BaseContext.setCurrentId(userId);
        return Result.success(shoppingCartService.list(userId));
    }

    @PostMapping("/cart/dish")
    public Result addDishToCart(@RequestHeader("X-AI-User-Id") Long userId, @RequestBody ShoppingCartDTO dto) {
        BaseContext.setCurrentId(userId);
        shoppingCartService.save(dto);
        return Result.success("Dish added to cart");
    }

    @PostMapping("/cart/setmeal")
    public Result addSetmealToCart(@RequestHeader("X-AI-User-Id") Long userId, @RequestBody ShoppingCartDTO dto) {
        BaseContext.setCurrentId(userId);
        shoppingCartService.save(dto);
        return Result.success("Setmeal added to cart");
    }

    @PostMapping("/cart/remove")
    public Result removeCartItem(@RequestHeader("X-AI-User-Id") Long userId, @RequestBody ShoppingCartDTO dto) {
        BaseContext.setCurrentId(userId);
        shoppingCartService.remove(dto);
        return Result.success("Cart item removed");
    }

    @DeleteMapping("/cart")
    public Result cleanCart(@RequestHeader("X-AI-User-Id") Long userId) {
        BaseContext.setCurrentId(userId);
        shoppingCartService.clean();
        return Result.success("Cart cleaned");
    }

    @GetMapping("/addresses")
    public Result<List<AddressBook>> listAddresses(@RequestHeader("X-AI-User-Id") Long userId) {
        BaseContext.setCurrentId(userId);
        return Result.success(addressBookService.list(AddressBook.builder().userId(userId).build()));
    }

    @GetMapping("/addresses/default")
    public Result<AddressBook> defaultAddress(@RequestHeader("X-AI-User-Id") Long userId) {
        BaseContext.setCurrentId(userId);
        List<AddressBook> list = addressBookService.list(AddressBook.builder().userId(userId).isDefault(1).build());
        return list.size() == 1 ? Result.success(list.get(0)) : Result.error("No default address");
    }

    @PutMapping("/addresses/default")
    public Result setDefaultAddress(@RequestHeader("X-AI-User-Id") Long userId, @RequestBody AddressBook addressBook) {
        BaseContext.setCurrentId(userId);
        addressBookService.setDefault(addressBook);
        return Result.success("Default address set to " + addressBook.getId());
    }

    @PutMapping("/addresses/{id}")
    public Result updateAddress(@RequestHeader("X-AI-User-Id") Long userId, @PathVariable Long id,
                                @RequestBody AddressBook addressBook) {
        BaseContext.setCurrentId(userId);
        addressBook.setId(id);
        addressBookService.update(addressBook);
        return Result.success("Address " + id + " updated: " + addressBook.getDetail());
    }

    public static class RefundRequest {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class UpdateOrderAddressRequest {
        private String newAddress;

        public String getNewAddress() {
            return newAddress;
        }

        public void setNewAddress(String newAddress) {
            this.newAddress = newAddress;
        }
    }
}
