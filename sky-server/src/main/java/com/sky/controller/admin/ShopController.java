package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ShopService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/15 18:18
 */

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Slf4j
@Api(tags = "店铺接口")
public class ShopController {

    @Autowired
    private ShopService shopService;

    /**
     * 查询店铺营业状态
     * 1营业 0打烊
     * @return
     */
    @GetMapping("/status")
    public Result<Integer> queryStatus() {
        log.info("管理端查询店铺营业状态...");
        return Result.success(shopService.queryStatus());
    }

    /**
     * 设置店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    public Result updateStatus(@PathVariable Integer status){
        log.info("管理端设置店铺营业状态，status : {}",status.equals(1) ? "营业中" : "打烊中");
        shopService.updateStatus(status);
        return Result.success();
    }
}
