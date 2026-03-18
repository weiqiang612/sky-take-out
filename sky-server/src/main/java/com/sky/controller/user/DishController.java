package com.sky.controller.user;

import com.alibaba.fastjson.JSON;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/17 17:15
 */

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类ID查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    public Result<List<DishVO>> list(@RequestParam("categoryId") Long categoryId) {
        log.info("用户端根据分类ID查询菜品中，分类ID{}", categoryId);

        String key = "dish_" + categoryId;

        // 1. 先去Redis中去查询数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);

        // 1.1 Redis中有数据，直接返回
        if (list != null && list.size() > 0) {
            return Result.success(list);
        }

        // 1.2 Redis中没数据，查询MySQL，并回写Redis
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE); // 查询起售的菜品

        list = dishService.listWithFlavors(dish);
        // 将菜品数据序列化为JSON
        // 这里不用再配置，已经在配置类中配置了Redis的序列化器
//            String jsonString = JSON.toJSONString(list);
        // 需要设置过期时间，避免冷数据长期占用内存
        redisTemplate.opsForValue().set(key, list, 60 + new Random().nextInt(10), TimeUnit.MINUTES);
        return Result.success(list);
    }

}
