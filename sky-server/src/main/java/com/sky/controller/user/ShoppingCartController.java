package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/19 17:53
 */

@RestController("userShoppingCartController")
@RequestMapping("/user/shoppingCart")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;


    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/add")
    public Result save(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("用户端添加购物车，{}", shoppingCartDTO);
        shoppingCartService.save(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查看购物车
     *
     * @return
     */
    @GetMapping("/list")
    public Result<List<ShoppingCart>> list() {
        log.info("用户查看购物车，userId:{}", BaseContext.getCurrentId());
        return Result.success(shoppingCartService.list(BaseContext.getCurrentId()));
    }

    /**
     * 删除购物车某商品
     *
     * @param shoppingCartDTO
     * @return
     */
    @PostMapping("/sub")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("用户：{} 删除购物车：{}", BaseContext.getCurrentId(), shoppingCartDTO);
        shoppingCartService.remove(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 清空购物车
     *
     * @return
     */
    @DeleteMapping("/clean")
    public Result clean() {
        log.info("用户：{}执行了清空购物车操作", BaseContext.getCurrentId());
        shoppingCartService.clean();
        return Result.success();
    }
}
