package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author weiqiang
 * @version 1.0
 * @Date 2026/3/13 16:40
 */

/**
 * 菜品相关接口
 */
@RequestMapping("/admin/dish")
@RestController
@Slf4j
public class DishController {
    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 新增菜品和口味
     *
     * @param dishDTO
     * @return
     */
    @PostMapping
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("save dish:{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);
        // 清理Redis缓存
        cleanCache("dish_" + dishDTO.getCategoryId());
        return Result.success();
    }

    /**
     * 菜品分页查询功能
     *
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询:{}", dishPageQueryDTO);
        PageResult result = dishService.page(dishPageQueryDTO);
        return Result.success(result);
    }

    /**
     * 批量删除菜品
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    public Result deleteBatch(@RequestParam List<Long> ids) {
        log.info("删除菜品,ids:{}", ids);
        dishService.deleteBatch(ids);

        // 将所有以dish_开头的缓存清掉
        cleanCache("dish_*");
        return Result.success();
    }

    /**
     * 根据菜品ID查询菜品
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<DishVO> getDishById(@PathVariable Long id) {
        log.info("根据ID查询菜品:{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }


    /**
     * 修改菜品
     *
     * @param dishDTO
     * @return
     */
    @PutMapping
    public Result updateWithFlavor(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品:{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);
//        Long oldCategoryId = dishService.getByIdWithFlavor(dishDTO.getId()).getCategoryId();
//        // 如果修改的是菜品的分类，那么需要把旧菜品的分类的缓存清掉
//        if (!dishDTO.getCategoryId().equals(oldCategoryId)) {
//            // 旧分类
//            redisTemplate.delete("dish_" + oldCategoryId);
//        }
//        // 当前分类，无论修不修改分类，当前分类都是变了的
//        redisTemplate.delete("dish_" + dishDTO.getCategoryId());
        cleanCache("dish_*");
        return Result.success();
    }

    @PostMapping("/status/{status}")
    public Result updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("菜品起售停售，菜品id:{}，调整状态为：{}", id, status == 1 ? "起售" : "停售");
        dishService.updateStatus(status,id);
        // 菜品起售停售将所有菜品数据删除
        cleanCache("dish_*");
        return Result.success();
    }

    /**
     * 清理缓存统一方法
     * @param pattern
     */
    private void cleanCache(String pattern) {
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}
